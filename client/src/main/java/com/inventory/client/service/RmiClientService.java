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
        connectServer1();
        connectServer2();

        // Ensure active server points to an online server if possible
        if ("SERVER_1".equals(activeServerId) && !server1Online && server2Online) {
            switchServer("SERVER_2", "Server 1 chưa sẵn sàng lúc khởi động");
        } else if ("SERVER_2".equals(activeServerId) && !server2Online && server1Online) {
            switchServer("SERVER_1", "Server 2 chưa sẵn sàng lúc khởi động");
        }
    }

    private synchronized boolean connectServer1() {
        try {
            Registry reg1 = LocateRegistry.getRegistry(config.getServer1Host(), config.getServer1Port());
            InventoryService stub = (InventoryService) reg1.lookup("InventoryService");
            stub.ping();
            this.server1Stub = stub;
            this.server1Online = true;
            logger.info("Connected to Server 1 at {}:{}", config.getServer1Host(), config.getServer1Port());
            return true;
        } catch (Exception e) {
            this.server1Stub = null;
            this.server1Online = false;
            logger.debug("Server 1 unavailable at {}:{}: {}", config.getServer1Host(), config.getServer1Port(), e.getMessage());
            return false;
        }
    }

    private synchronized boolean connectServer2() {
        try {
            Registry reg2 = LocateRegistry.getRegistry(config.getServer2Host(), config.getServer2Port());
            InventoryService stub = (InventoryService) reg2.lookup("InventoryService");
            stub.ping();
            this.server2Stub = stub;
            this.server2Online = true;
            logger.info("Connected to Server 2 at {}:{}", config.getServer2Host(), config.getServer2Port());
            return true;
        } catch (Exception e) {
            this.server2Stub = null;
            this.server2Online = false;
            logger.debug("Server 2 unavailable at {}:{}: {}", config.getServer2Host(), config.getServer2Port(), e.getMessage());
            return false;
        }
    }

    private void checkHealth() {
        boolean s1Before = server1Online;
        boolean s2Before = server2Online;

        // Check S1
        if (server1Stub != null) {
            try {
                server1Stub.ping();
                server1Online = true;
            } catch (Exception e) {
                server1Online = false;
                server1Stub = null;
            }
        } else {
            connectServer1();
        }

        // Check S2
        if (server2Stub != null) {
            try {
                server2Stub.ping();
                server2Online = true;
            } catch (Exception e) {
                server2Online = false;
                server2Stub = null;
            }
        } else {
            connectServer2();
        }

        // If current active server is offline, switch to online peer
        if ("SERVER_1".equals(activeServerId) && !server1Online && server2Online) {
            switchServer("SERVER_2", "Tự động failover: Server 1 mất kết nối");
        } else if ("SERVER_2".equals(activeServerId) && !server2Online && server1Online) {
            switchServer("SERVER_1", "Tự động failover: Server 2 mất kết nối");
        }

        if (server1Online || server2Online || s1Before != server1Online || s2Before != server2Online) {
            notifyStatusChange();
        }
    }

    public synchronized void switchServer(String targetServerId, String reason) {
        if (!targetServerId.equals(this.activeServerId)) {
            logger.info(">>> SWITCHING ACTIVE SERVER: {} -> {} (Reason: {})", activeServerId, targetServerId, reason);
            this.activeServerId = targetServerId;
            for (Consumer<String> listener : failoverListeners) {
                try {
                    listener.accept("Chuyển sang " + targetServerId + ": " + reason);
                } catch (Exception ignored) {}
            }
            notifyStatusChange();
        }
    }

    private synchronized InventoryService getValidStubFor(String serverId) {
        if ("SERVER_1".equals(serverId)) {
            if (server1Stub != null) {
                try {
                    server1Stub.ping();
                    server1Online = true;
                    return server1Stub;
                } catch (Exception e) {
                    server1Stub = null;
                    server1Online = false;
                }
            }
            if (connectServer1()) {
                return server1Stub;
            }
        } else {
            if (server2Stub != null) {
                try {
                    server2Stub.ping();
                    server2Online = true;
                    return server2Stub;
                } catch (Exception e) {
                    server2Stub = null;
                    server2Online = false;
                }
            }
            if (connectServer2()) {
                return server2Stub;
            }
        }
        return null;
    }

    private synchronized InventoryService getActiveStub() throws InventoryException {
        // Try active server
        InventoryService stub = getValidStubFor(activeServerId);
        if (stub != null) {
            return stub;
        }

        // Fallback to peer server
        String alt = "SERVER_1".equals(activeServerId) ? "SERVER_2" : "SERVER_1";
        InventoryService altStub = getValidStubFor(alt);
        if (altStub != null) {
            switchServer(alt, "Máy chủ trước đó không khả dụng");
            return altStub;
        }

        throw new InventoryException("Không thể kết nối đến cả 2 Server (" +
                config.getServer1Host() + ":" + config.getServer1Port() + " và " +
                config.getServer2Host() + ":" + config.getServer2Port() + "). Vui lòng kiểm tra Server đang chạy và kết nối Tailscale.");
    }

    public synchronized <T> T executeWithFailover(RmiOperation<T> operation) throws InventoryException {
        InventoryService stub = getActiveStub();
        try {
            return operation.execute(stub);
        } catch (RemoteException re) {
            logger.warn("Remote call on active server {} failed: {}. Triggering failover...", activeServerId, re.getMessage());
            
            // Invalidate failed server stub
            if ("SERVER_1".equals(activeServerId)) {
                server1Stub = null;
                server1Online = false;
            } else {
                server2Stub = null;
                server2Online = false;
            }

            // Switch to peer server
            String alternativeServerId = "SERVER_1".equals(activeServerId) ? "SERVER_2" : "SERVER_1";
            InventoryService fallbackStub = getValidStubFor(alternativeServerId);
            if (fallbackStub != null) {
                switchServer(alternativeServerId, "Failover sau khi cuộc gọi RMI gặp lỗi: " + re.getMessage());
                try {
                    return operation.execute(fallbackStub);
                } catch (RemoteException fallbackRe) {
                    logger.error("Call failed on fallback server {} as well: {}", alternativeServerId, fallbackRe.getMessage());
                    throw new InventoryException("Lỗi giao tiếp trên cả 2 máy chủ: " + fallbackRe.getMessage(), fallbackRe);
                }
            } else {
                throw new InventoryException("Máy chủ " + activeServerId + " gặp lỗi và máy chủ dự phòng " + alternativeServerId + " cũng không khả dụng.");
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
            } catch (Exception e) {
                server1Stub = null;
                server1Online = false;
            }
        }
        return new ServerStatusDTO("SERVER_1", config.getServer1Host(), config.getServer1Port(), false, 0, 0, 0, 0, "SERVER_2", false);
    }

    public ServerStatusDTO getServer2StatusDirect() {
        if (server2Stub != null && server2Online) {
            try {
                return server2Stub.getServerStatus();
            } catch (Exception e) {
                server2Stub = null;
                server2Online = false;
            }
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
