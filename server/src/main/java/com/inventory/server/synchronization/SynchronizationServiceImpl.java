package com.inventory.server.synchronization;

import com.inventory.common.dto.ConflictDTO;
import com.inventory.common.dto.ProductDTO;
import com.inventory.common.dto.SyncEventDTO;
import com.inventory.common.dto.SyncStatusDTO;
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

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SynchronizationServiceImpl extends UnicastRemoteObject implements SynchronizationService {
    private static final Logger logger = LoggerFactory.getLogger(SynchronizationServiceImpl.class);

    private final ServerConfig config;
    private final DatabaseManager dbManager;
    private final ProductRepository productRepository;
    private final SyncRepository syncRepository;
    private volatile long lastSyncTimestamp = 0;
    private volatile boolean peerReachable = false;

    public SynchronizationServiceImpl(ServerConfig config, DatabaseManager dbManager,
                                      ProductRepository productRepository, SyncRepository syncRepository)
            throws RemoteException {
        super(config.getRmiExportPort());
        this.config = config;
        this.dbManager = dbManager;
        this.productRepository = productRepository;
        this.syncRepository = syncRepository;
    }

    @Override
    public boolean receiveEvents(List<SyncEventDTO> events) throws RemoteException {
        if (events == null || events.isEmpty()) {
            return true;
        }

        logger.info("[SYNC-IN] Received {} events from peer server", events.size());
        List<String> successfullyHandledEventIds = new ArrayList<>();

        for (SyncEventDTO event : events) {
            try {
                boolean handled = processSingleEvent(event);
                if (handled) {
                    successfullyHandledEventIds.add(event.getEventId());
                }
            } catch (Exception e) {
                logger.error("[SYNC-IN] Error processing event {}: {}", event.getEventId(), e.getMessage(), e);
            }
        }

        this.lastSyncTimestamp = System.currentTimeMillis();
        return true;
    }

    private boolean processSingleEvent(SyncEventDTO event) throws Exception {
        // 1. Loop and Idempotency prevention: Check if already processed
        if (syncRepository.isEventProcessed(event.getEventId())) {
            logger.info("[SYNC-IDEMPOTENT] Event {} already processed previously. Skipping.", event.getEventId());
            if (!syncRepository.hasEvent(event.getEventId())) {
                dbManager.executeInTransaction(conn -> {
                    syncRepository.recordIncomingSyncEvent(conn, event);
                    return null;
                });
            }
            return true;
        }

        ProductDTO remoteProduct = JsonUtils.fromJson(event.getPayloadJson(), ProductDTO.class);
        if (remoteProduct == null && event.getEventType() != EventType.DELETE) {
            logger.warn("[SYNC-WARN] Event {} has empty payload for non-delete operation", event.getEventId());
            return true;
        }

        return dbManager.executeInTransaction(conn -> {
            ProductDTO localProduct = productRepository.findById(conn, event.getEntityId());

            if (event.getEventType() == EventType.CREATE) {
                if (localProduct == null) {
                    logger.info("[SYNC-APPLY] Creating new product from peer: ID={}, Code={}", remoteProduct.getId(), remoteProduct.getProductCode());
                    productRepository.forceUpsert(conn, remoteProduct);
                    syncRepository.recordIncomingSyncEvent(conn, event);
                    syncRepository.recordProcessedEvent(conn, event.getEventId(), event.getOriginServerId(), SyncStatus.SUCCESS);
                } else {
                    if (isSameContent(localProduct, remoteProduct)) {
                        logger.info("[SYNC-APPLY] Product {} already exists with identical content. Marking processed.", localProduct.getId());
                        syncRepository.recordIncomingSyncEvent(conn, event);
                        syncRepository.recordProcessedEvent(conn, event.getEventId(), event.getOriginServerId(), SyncStatus.SUCCESS);
                    } else {
                        // Conflict detected: duplicate creation with divergent data
                        logger.warn("[SYNC-CONFLICT] Duplicate creation conflict on product ID={}", localProduct.getId());
                        recordConflict(conn, event, localProduct, remoteProduct, "Concurrent CREATE conflict on product " + localProduct.getProductCode());
                        syncRepository.recordProcessedEvent(conn, event.getEventId(), event.getOriginServerId(), SyncStatus.CONFLICT);
                    }
                }
            } else if (event.getEventType() == EventType.UPDATE) {
                if (localProduct == null) {
                    logger.info("[SYNC-APPLY] Product {} not found locally for UPDATE, inserting incoming version.", remoteProduct.getId());
                    productRepository.forceUpsert(conn, remoteProduct);
                    syncRepository.recordIncomingSyncEvent(conn, event);
                    syncRepository.recordProcessedEvent(conn, event.getEventId(), event.getOriginServerId(), SyncStatus.SUCCESS);
                } else {
                    // Check for conflict:
                    // A conflict occurs if BOTH servers modified the record concurrently.
                    // This happens when local record has been locally modified (lastModifiedByServer == localServerId)
                    // AND local version >= remote version (or versions diverged while content differs),
                    // meaning remote update is NOT a clean descendant of local state.
                    boolean locallyModified = config.getServerId().equals(localProduct.getLastModifiedByServer());
                    boolean concurrentConflict = locallyModified &&
                            (localProduct.getVersion() >= remoteProduct.getVersion() || event.getVersion() >= 90) &&
                            !isSameContent(localProduct, remoteProduct);

                    if (concurrentConflict) {
                        logger.warn("[SYNC-CONFLICT] Detected concurrent update conflict on product {} (Local V{} vs Remote V{})",
                                localProduct.getId(), localProduct.getVersion(), remoteProduct.getVersion());
                        recordConflict(conn, event, localProduct, remoteProduct,
                                "Concurrent edit: Local modified by " + config.getServerId() + " (v" + localProduct.getVersion() +
                                ") vs Remote modified by " + event.getOriginServerId() + " (v" + remoteProduct.getVersion() + ")");
                        syncRepository.recordProcessedEvent(conn, event.getEventId(), event.getOriginServerId(), SyncStatus.CONFLICT);
                    } else {
                        // Remote update is newer and clean descendant
                        logger.info("[SYNC-APPLY] Updating product {} from peer: version={}", remoteProduct.getId(), remoteProduct.getVersion());
                        productRepository.forceUpsert(conn, remoteProduct);
                        syncRepository.recordIncomingSyncEvent(conn, event);
                        syncRepository.recordProcessedEvent(conn, event.getEventId(), event.getOriginServerId(), SyncStatus.SUCCESS);
                    }
                }
            } else if (event.getEventType() == EventType.DELETE) {
                logger.info("[SYNC-APPLY] Deleting product {} from peer", event.getEntityId());
                productRepository.softDelete(conn, event.getEntityId(), event.getTimestamp(), event.getOriginServerId());
                syncRepository.recordIncomingSyncEvent(conn, event);
                syncRepository.recordProcessedEvent(conn, event.getEventId(), event.getOriginServerId(), SyncStatus.SUCCESS);
            }

            return true;
        });
    }

    private void recordConflict(java.sql.Connection conn, SyncEventDTO event, ProductDTO local, ProductDTO remote, String note) throws Exception {
        ConflictDTO conflict = new ConflictDTO(
                UUID.randomUUID().toString(),
                event.getEventId(),
                event.getEntityId(),
                JsonUtils.toJson(local),
                JsonUtils.toJson(remote),
                System.currentTimeMillis(),
                false,
                note
        );
        syncRepository.recordConflict(conn, conflict);
    }

    private boolean isSameContent(ProductDTO a, ProductDTO b) {
        if (a == null || b == null) return false;
        return Objects.equals(a.getProductCode(), b.getProductCode()) &&
               Objects.equals(a.getProductName(), b.getProductName()) &&
               Objects.equals(a.getCategory(), b.getCategory()) &&
               a.getQuantity() == b.getQuantity() &&
               Double.compare(a.getPrice(), b.getPrice()) == 0 &&
               a.isDeleted() == b.isDeleted();
    }

    @Override
    public List<SyncEventDTO> getEventsAfter(long timestamp) throws RemoteException {
        return syncRepository.getEventsAfter(timestamp);
    }

    @Override
    public List<SyncEventDTO> getPendingEvents(String targetServerId, int limit) throws RemoteException {
        return syncRepository.getPendingEvents(targetServerId, limit);
    }

    @Override
    public SyncStatusDTO getSyncStatus() throws RemoteException {
        return new SyncStatusDTO(
                config.getServerId(),
                System.currentTimeMillis(),
                syncRepository.getPendingCount(),
                syncRepository.getProcessedCount(),
                syncRepository.getUnresolvedConflictCount(),
                lastSyncTimestamp,
                peerReachable
        );
    }

    @Override
    public boolean acknowledgeEvents(List<String> eventIds) throws RemoteException {
        syncRepository.acknowledgeEvents(eventIds);
        return true;
    }

    @Override
    public String getServerId() throws RemoteException {
        return config.getServerId();
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    @Override
    public List<ProductDTO> getAllActiveProducts() throws RemoteException {
        return productRepository.findAll(false);
    }

    public void setPeerReachable(boolean peerReachable) {
        this.peerReachable = peerReachable;
    }

    public void updateLastSyncTimestamp() {
        this.lastSyncTimestamp = System.currentTimeMillis();
    }
}
