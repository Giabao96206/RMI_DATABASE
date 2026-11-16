package com.inventory.client.service;

import com.inventory.client.config.ClientConfig;
import com.inventory.common.dto.ConflictDTO;
import com.inventory.common.dto.InventoryStatsDTO;
import com.inventory.common.dto.ProductDTO;
import com.inventory.common.dto.ServerStatusDTO;
import com.inventory.common.dto.SyncEventDTO;
import com.inventory.common.exception.InventoryException;
import com.inventory.common.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RmiClientService {
    private static final Logger logger = LoggerFactory.getLogger(RmiClientService.class);

    private final ClientConfig config;
    private volatile String activeServerId;
    private volatile InventoryService server1Stub;
    private volatile InventoryService server2Stub;
    private volatile boolean server1Online = false;
    private volatile boolean server2Online = false;

    private final ScheduledExecutorService monitorExecutor;
    private final List<Consumer<String>> failoverListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> statusChangeListeners = new CopyOnWriteArrayList<>();

    public interface RmiOperation<T> {
        T execute(InventoryService service) throws RemoteException, InventoryException;
    }

    public RmiClientService(ClientConfig config) {
        this.config = config;
        this.activeServerId = config.getPreferredServer();
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ClientServerMonitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        connectBoth();
        monitorExecutor.scheduleWithFixedDelay(this::checkHealth, 2, 3, TimeUnit.SECONDS);
    }

    public void stop() {
        monitorExecutor.shutdownNow();
    }

    public synchronized void connectBoth() {
        // Connect to Server 1
        try {
            Registry reg1 = LocateRegistry.getRegistry(config.getServer1Host(), config.getServer1Port());
            this.server1Stub = (InventoryService) reg1.lookup("InventoryService");
            this.server1Online = server1Stub.ping();
            logger.info("Connected to Server 1 at {}:{}", config.getServer1Host(), config.getServer1Port());
        } catch (Exception e) {
            this.server1Stub = null;
            this.server1Online = false;
            logger.warn("Server 1 unavailable at {}:{}: {}", config.getServer1Host(), config.getServer1Port(), e.getMessage());
        }

        // Connect to Server 2
        try {
            Registry reg2 = LocateRegistry.getRegistry(config.getServer2Host(), config.getServer2Port());
            this.server2Stub = (InventoryService) reg2.lookup("InventoryService");
            this.server2Online = server2Stub.ping();
            logger.info("Connected to Server 2 at {}:{}", config.getServer2Host(), config.getServer2Port());
        } catch (Exception e) {
            this.server2Stub = null;
            this.server2Online = false;
            logger.warn("Server 2 unavailable at {}:{}: {}", config.getServer2Host(), config.getServer2Port(), e.getMessage());
        }

        // Ensure active server points to an online server
        if ("SERVER_1".equals(activeServerId) && !server1Online && server2Online) {
            switchServer("SERVER_2", "Server 1 is offline at startup");
        } else if ("SERVER_2".equals(activeServerId) && !server2Online && server1Online) {
            switchServer("SERVER_1", "Server 2 is offline at startup");
        }
    }

    private void checkHealth() {
        boolean s1Before = server1Online;
        boolean s2Before = server2Online;

        // Check S1
        try {
            if (server1Stub == null) {
                Registry reg1 = LocateRegistry.getRegistry(config.getServer1Host(), config.getServer1Port());
                server1Stub = (InventoryService) reg1.lookup("InventoryService");
            }
            server1Online = server1Stub.ping();
        } catch (Exception e) {
            server1Online = false;
            server1Stub = null;
        }

        // Check S2
        try {
            if (server2Stub == null) {
                Registry reg2 = LocateRegistry.getRegistry(config.getServer2Host(), config.getServer2Port());
                server2Stub = (InventoryService) reg2.lookup("InventoryService");
            }
            server2Online = server2Stub.ping();
        } catch (Exception e) {
            server2Online = false;
            server2Stub = null;
        }

        // If active server just dropped, auto failover
        if ("SERVER_1".equals(activeServerId) && !server1Online && server2Online) {
            switchServer("SERVER_2", "Automated failover: Server 1 is offline");
        } else if ("SERVER_2".equals(activeServerId) && !server2Online && server1Online) {
            switchServer("SERVER_1", "Automated failover: Server 2 is offline");
        }

        if (s1Before != server1Online || s2Before != server2Online) {
            notifyStatusChange();
        }
    }

    public synchronized void switchServer(String targetServerId, String reason) {
        if (!targetServerId.equals(this.activeServerId)) {
            logger.info(">>> SWITCHING ACTIVE SERVER: {} -> {} (Reason: {})", activeServerId, targetServerId, reason);
            this.activeServerId = targetServerId;
            for (Consumer<String> listener : failoverListeners) {
                try {
                    listener.accept("Switched to " + targetServerId + ": " + reason);
                } catch (Exception ignored) {}
            }
            notifyStatusChange();
        }
    }

    private InventoryService getActiveStub() throws InventoryException {
        if ("SERVER_1".equals(activeServerId)) {
            if (server1Stub != null && server1Online) return server1Stub;
            // Attempt fast reconnect
            try {
                Registry reg1 = LocateRegistry.getRegistry(config.getServer1Host(), config.getServer1Port());
                server1Stub = (InventoryService) reg1.lookup("InventoryService");
                server1Online = true;
                return server1Stub;
            } catch (Exception e) {
                server1Online = false;
                server1Stub = null;
                // Try failover to Server 2
                if (server2Stub != null || connectServer2()) {
                    switchServer("SERVER_2", "Server 1 unreachable, failing over to Server 2");
                    return server2Stub;
                }
            }
        } else {
            if (server2Stub != null && server2Online) return server2Stub;
            try {
                Registry reg2 = LocateRegistry.getRegistry(config.getServer2Host(), config.getServer2Port());
                server2Stub = (InventoryService) reg2.lookup("InventoryService");
                server2Online = true;
                return server2Stub;
            } catch (Exception e) {
                server2Online = false;
                server2Stub = null;
                // Try failover to Server 1
                if (server1Stub != null || connectServer1()) {
                    switchServer("SERVER_1", "Server 2 unreachable, failing over to Server 1");
                    return server1Stub;
                }
            }
        }
        throw new InventoryException("Both servers are currently unreachable! Please check network connections.");
    }

    private boolean connectServer1() {
        try {
            Registry reg1 = LocateRegistry.getRegistry(config.getServer1Host(), config.getServer1Port());
            server1Stub = (InventoryService) reg1.lookup("InventoryService");
            server1Online = server1Stub.ping();
            return server1Online;
        } catch (Exception e) {
            server1Online = false;
            return false;
        }
    }

    private boolean connectServer2() {
        try {
            Registry reg2 = LocateRegistry.getRegistry(config.getServer2Host(), config.getServer2Port());
            server2Stub = (InventoryService) reg2.lookup("InventoryService");
            server2Online = server2Stub.ping();
            return server2Online;
        } catch (Exception e) {
            server2Online = false;
            return false;
        }
    }

    public <T> T executeWithFailover(RmiOperation<T> operation) throws InventoryException {
        InventoryService stub = getActiveStub();
        try {
            return operation.execute(stub);
        } catch (RemoteException re) {
            logger.warn("RemoteException on active server {}: {}. Triggering failover...", activeServerId, re.getMessage());
            // Switch to peer server and retry once
            String alternativeServerId = "SERVER_1".equals(activeServerId) ? "SERVER_2" : "SERVER_1";
            switchServer(alternativeServerId, "Remote call failed on previous server: " + re.getMessage());
            InventoryService fallbackStub = getActiveStub();
            try {
                return operation.execute(fallbackStub);
            } catch (RemoteException fallbackRe) {
                logger.error("Call failed on fallback server as well: {}", fallbackRe.getMessage());
                throw new InventoryException("Communication failure across both servers: " + fallbackRe.getMessage(), fallbackRe);
            }
        }
    }

    // High level API
    public ProductDTO addProduct(ProductDTO product) throws InventoryException {
        return executeWithFailover(s -> s.addProduct(product));
    }

    public ProductDTO updateProduct(ProductDTO product) throws InventoryException {
        return executeWithFailover(s -> s.updateProduct(product));
    }

    public boolean deleteProduct(String productId) throws InventoryException {
        return executeWithFailover(s -> s.deleteProduct(productId));
    }

    public List<ProductDTO> getAllProducts() throws InventoryException {
        return executeWithFailover(InventoryService::getAllProducts);
    }

    public ProductDTO getProductById(String productId) throws InventoryException {
        return executeWithFailover(s -> s.getProductById(productId));
    }

    public List<ProductDTO> searchProducts(String keyword) throws InventoryException {
        return executeWithFailover(s -> s.searchProducts(keyword));
    }

    public InventoryStatsDTO getInventoryStatistics() throws InventoryException {
        return executeWithFailover(InventoryService::getInventoryStatistics);
    }

    public ServerStatusDTO getActiveServerStatus() throws InventoryException {
        return executeWithFailover(InventoryService::getServerStatus);
    }

    public ServerStatusDTO getServer1StatusDirect() {
        if (server1Stub != null && server1Online) {
            try {
                return server1Stub.getServerStatus();
            } catch (Exception ignored) {}
        }
        return new ServerStatusDTO("SERVER_1", config.getServer1Host(), config.getServer1Port(), false, 0, 0, 0, 0, "SERVER_2", false);
    }

    public ServerStatusDTO getServer2StatusDirect() {
        if (server2Stub != null && server2Online) {
            try {
                return server2Stub.getServerStatus();
            } catch (Exception ignored) {}
        }
        return new ServerStatusDTO("SERVER_2", config.getServer2Host(), config.getServer2Port(), false, 0, 0, 0, 0, "SERVER_1", false);
    }

    public List<SyncEventDTO> getSyncHistory(int limit) throws InventoryException {
        return executeWithFailover(s -> s.getSyncHistory(limit));
    }

    public List<ConflictDTO> getConflicts() throws InventoryException {
        return executeWithFailover(InventoryService::getConflicts);
    }

    public boolean resolveConflict(String conflictId, ProductDTO resolvedProduct) throws InventoryException {
        return executeWithFailover(s -> s.resolveConflict(conflictId, resolvedProduct));
    }

    public void addFailoverListener(Consumer<String> listener) {
        failoverListeners.add(listener);
    }

    public void addStatusChangeListener(Runnable listener) {
        statusChangeListeners.add(listener);
    }

    private void notifyStatusChange() {
        for (Runnable r : statusChangeListeners) {
            try {
                r.run();
            } catch (Exception ignored) {}
        }
    }

    public String getActiveServerId() {
        return activeServerId;
    }

    public boolean isServer1Online() {
        return server1Online;
    }

    public boolean isServer2Online() {
        return server2Online;
    }

    public ClientConfig getConfig() {
        return config;
    }
}
