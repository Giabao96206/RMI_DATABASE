package com.inventory.server.synchronization;

import com.inventory.common.dto.ProductDTO;
import com.inventory.common.dto.SyncEventDTO;
import com.inventory.common.model.EventType;
import com.inventory.common.model.SyncStatus;
import com.inventory.common.service.SynchronizationService;
import com.inventory.common.util.JsonUtils;
import com.inventory.server.config.ServerConfig;
import com.inventory.server.database.DatabaseManager;
import com.inventory.server.repository.ProductRepository;
import com.inventory.server.repository.SyncRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SyncManager {
    private static final Logger logger = LoggerFactory.getLogger(SyncManager.class);

    private final ServerConfig config;
    private final DatabaseManager dbManager;
    private final SyncRepository syncRepository;
    private final SynchronizationServiceImpl localSyncService;
    private final ProductRepository productRepository;
    private final ScheduledExecutorService scheduler;

    private volatile SynchronizationService peerSyncService = null;
    private volatile boolean peerConnected = false;
    private volatile long lastSuccessfulSyncTime = 0;

    public SyncManager(ServerConfig config, DatabaseManager dbManager, SyncRepository syncRepository,
                       SynchronizationServiceImpl localSyncService, ProductRepository productRepository) {
        this.config = config;
        this.dbManager = dbManager;
        this.syncRepository = syncRepository;
        this.localSyncService = localSyncService;
        this.productRepository = productRepository;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SyncWorker-" + config.getServerId());
            t.setDaemon(true);
            return t;
        });
    }

    public SyncManager(ServerConfig config, SyncRepository syncRepository, SynchronizationServiceImpl localSyncService, ProductRepository productRepository) {
        this(config, null, syncRepository, localSyncService, productRepository);
    }

    public SyncManager(ServerConfig config, SyncRepository syncRepository, SynchronizationServiceImpl localSyncService) {
        this(config, syncRepository, localSyncService, null);
    }

    public void start() {
        logger.info("Starting SyncManager for {} targeting peer {} at {}:{}",
                config.getServerId(), config.getPeerServerId(), config.getPeerHost(), config.getPeerRmiRegistryPort());
        
        // Immediate initial connection check & sync task
        scheduler.scheduleWithFixedDelay(this::syncCycle, 1000, config.getSyncIntervalMs(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        logger.info("Stopping SyncManager for {}", config.getServerId());
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void triggerImmediateSync() {
        try {
            if (!scheduler.isShutdown()) {
                scheduler.submit(this::pushPendingEvents);
            }
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        }
    }

    private synchronized void syncCycle() {
        try {
            boolean wasConnected = this.peerConnected;
            checkPeerConnection();

            if (this.peerConnected) {
                // If just reconnected, perform catch-up sync
                if (!wasConnected) {
                    logger.info(">>> PEER {} RECONNECTED! Starting catch-up synchronization...", config.getPeerServerId());
                    performCatchUpSync();
                }

                // Push locally pending events
                pushPendingEvents();
            }
        } catch (Exception e) {
            logger.warn("Exception in sync cycle: {}", e.getMessage());
        }
    }

    private void checkPeerConnection() {
        try {
            if (peerSyncService == null) {
                Registry registry = LocateRegistry.getRegistry(config.getPeerHost(), config.getPeerRmiRegistryPort());
                peerSyncService = (SynchronizationService) registry.lookup("SynchronizationService");
            }
            // Ping peer
            boolean ok = peerSyncService.ping();
            if (ok) {
                if (!peerConnected) {
                    logger.info("Successfully connected to peer server: {} at {}:{}",
                            config.getPeerServerId(), config.getPeerHost(), config.getPeerRmiRegistryPort());
                }
                peerConnected = true;
                localSyncService.setPeerReachable(true);
                return;
            }
        } catch (Exception e) {
            // Connection failed
            if (peerConnected) {
                logger.warn("Lost connection to peer server {}: {}", config.getPeerServerId(), e.getMessage());
            }
            peerConnected = false;
            peerSyncService = null;
            localSyncService.setPeerReachable(false);
        }
    }

    private void pushPendingEvents() {
        if (!peerConnected || peerSyncService == null) {
            return;
        }

        try {
            List<SyncEventDTO> pending = syncRepository.getPendingEvents(config.getPeerServerId(), 50);
            if (pending.isEmpty()) {
                return;
            }

            logger.info("[SYNC-OUT] Pushing {} pending events to peer {}", pending.size(), config.getPeerServerId());
            boolean success = peerSyncService.receiveEvents(pending);

            if (success) {
                List<String> eventIds = new ArrayList<>();
                for (SyncEventDTO ev : pending) {
                    eventIds.add(ev.getEventId());
                }
                syncRepository.acknowledgeEvents(eventIds);
                this.lastSuccessfulSyncTime = System.currentTimeMillis();
                localSyncService.updateLastSyncTimestamp();
                logger.info("[SYNC-OUT] Successfully synchronized {} events to peer {}", eventIds.size(), config.getPeerServerId());
            }
        } catch (Exception e) {
            logger.warn("[SYNC-OUT] Failed to push events to peer {}: {}", config.getPeerServerId(), e.getMessage());
            peerConnected = false;
            peerSyncService = null;
            localSyncService.setPeerReachable(false);
        }
    }

    private void performCatchUpSync() {
        try {
            // 1. First push our own pending events that accumulated while peer was offline
            pushPendingEvents();

            // 2. Also check if peer has any events for us
            List<SyncEventDTO> peerPending = peerSyncService.getPendingEvents(config.getServerId(), 100);
            if (peerPending != null && !peerPending.isEmpty()) {
                logger.info("[CATCH-UP] Pulling {} events from peer {}", peerPending.size(), config.getPeerServerId());
                localSyncService.receiveEvents(peerPending);
                List<String> ackIds = new ArrayList<>();
                for (SyncEventDTO ev : peerPending) {
                    ackIds.add(ev.getEventId());
                }
                peerSyncService.acknowledgeEvents(ackIds);
            }

            // 3. Reconcile complete sync history log so both servers have identical origin/target history
            syncMissingHistoryFromPeer();

            // 4. Full catalog reconciliation (guarantees both servers have ALL items)
            reconcileCatalogs();

            logger.info(">>> Catch-up synchronization complete with {}", config.getPeerServerId());
        } catch (Exception e) {
            logger.error("Error during catch-up sync: {}", e.getMessage(), e);
        }
    }

    private void syncMissingHistoryFromPeer() {
        if (peerSyncService == null || syncRepository == null) return;
        try {
            List<SyncEventDTO> peerHistory = peerSyncService.getEventsAfter(0);
            if (peerHistory == null || peerHistory.isEmpty()) return;

            int syncedCount = 0;
            for (SyncEventDTO ev : peerHistory) {
                if (!syncRepository.hasEvent(ev.getEventId())) {
                    dbManager.executeInTransaction(conn -> {
                        syncRepository.recordIncomingSyncEvent(conn, ev);
                        return null;
                    });
                    syncedCount++;
                }
            }
            if (syncedCount > 0) {
                logger.info("[HISTORY-SYNC] Reconciled {} historical sync events from peer {}", syncedCount, config.getPeerServerId());
            }
        } catch (Exception e) {
            logger.warn("Could not sync missing history from peer: {}", e.getMessage());
        }
    }

    private void reconcileCatalogs() {
        if (productRepository == null || peerSyncService == null) {
            return;
        }
        try {
            List<ProductDTO> peerProducts = peerSyncService.getAllActiveProducts();
            if (peerProducts == null || peerProducts.isEmpty()) {
                return;
            }

            int appliedCount = 0;
            long now = System.currentTimeMillis();

            for (ProductDTO peerProd : peerProducts) {
                ProductDTO localProd = productRepository.findById(peerProd.getId());
                if (localProd == null || localProd.getVersion() < peerProd.getVersion()) {
                    String origin = (peerProd.getLastModifiedByServer() != null && !peerProd.getLastModifiedByServer().trim().isEmpty())
                            ? peerProd.getLastModifiedByServer()
                            : config.getPeerServerId();
                    String target = origin.equals(config.getServerId()) ? config.getPeerServerId() : config.getServerId();

                    dbManager.executeInTransaction(conn -> {
                        productRepository.forceUpsert(conn, peerProd);
                        // Record event if not already present in sync table
                        String eventId = UUID.nameUUIDFromBytes(("RECONCILE-" + peerProd.getId() + "-v" + peerProd.getVersion()).getBytes()).toString();
                        if (!syncRepository.hasEvent(eventId)) {
                            SyncEventDTO ev = new SyncEventDTO(
                                    eventId,
                                    "PRODUCT",
                                    peerProd.getId(),
                                    (localProd == null) ? EventType.CREATE : EventType.UPDATE,
                                    JsonUtils.toJson(peerProd),
                                    origin,
                                    target,
                                    now,
                                    peerProd.getVersion(),
                                    SyncStatus.SUCCESS,
                                    0,
                                    null
                            );
                            syncRepository.recordIncomingSyncEvent(conn, ev);
                        }
                        return null;
                    });
                    appliedCount++;
                }
            }

            if (appliedCount > 0) {
                logger.info("[RECONCILE] Applied {} missing/updated products from peer {}", appliedCount, config.getPeerServerId());
            }
        } catch (Exception e) {
            logger.warn("Reconcile catalogs encountered error: {}", e.getMessage());
        }
    }

    public boolean isPeerConnected() {
        return peerConnected;
    }

    public long getLastSuccessfulSyncTime() {
        return lastSuccessfulSyncTime;
    }
}
