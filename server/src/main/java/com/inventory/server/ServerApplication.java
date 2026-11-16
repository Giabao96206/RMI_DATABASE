package com.inventory.server;

import com.inventory.server.config.ServerConfig;
import com.inventory.server.database.DatabaseManager;
import com.inventory.server.repository.ProductRepository;
import com.inventory.server.repository.SyncRepository;
import com.inventory.server.service.InventoryServiceImpl;
import com.inventory.server.synchronization.SyncManager;
import com.inventory.server.synchronization.SynchronizationServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class ServerApplication {
    private static final Logger logger = LoggerFactory.getLogger(ServerApplication.class);

    private final ServerConfig config;
    private DatabaseManager dbManager;
    private SyncManager syncManager;
    private Registry registry;
    private InventoryServiceImpl inventoryService;
    private SynchronizationServiceImpl syncService;
    private volatile boolean running = false;

    public ServerApplication(ServerConfig config) {
        this.config = config;
    }

    public synchronized void start() {
        if (running) return;
        try {
            logger.info("======================================================");
            logger.info("   Starting Distributed Inventory Server: {}", config.getServerId());
            logger.info("======================================================");

            // Configure java.rmi.server.hostname for multi-machine LAN communication
            System.setProperty("java.rmi.server.hostname", config.getServerHost());
            logger.info("RMI Hostname set to: {}", config.getServerHost());

            // Initialize SQLite DB
            this.dbManager = new DatabaseManager(config.getDbPath());
            ProductRepository productRepo = new ProductRepository(dbManager);
            SyncRepository syncRepo = new SyncRepository(dbManager);

            // Seed initial 20 sample products if database is empty
            com.inventory.server.database.DataSeeder.seedIfEmpty(dbManager, productRepo, syncRepo, config);

            // Initialize Synchronization Service (for Server-to-Server RMI calls)
            this.syncService = new SynchronizationServiceImpl(config, dbManager, productRepo, syncRepo);

            // Initialize Background Sync Manager
            this.syncManager = new SyncManager(config, dbManager, syncRepo, syncService, productRepo);
            this.syncManager.start();

            // Initialize Inventory Service (for Client RMI calls)
            this.inventoryService = new InventoryServiceImpl(config, dbManager, productRepo, syncRepo, syncManager);

            // Start or locate RMI Registry
            try {
                this.registry = LocateRegistry.createRegistry(config.getRmiRegistryPort());
                logger.info("Created new RMI Registry on port {}", config.getRmiRegistryPort());
            } catch (Exception e) {
                logger.info("RMI Registry already exists on port {}, locating...", config.getRmiRegistryPort());
                this.registry = LocateRegistry.getRegistry(config.getRmiRegistryPort());
            }

            // Bind Remote Services to Registry
            this.registry.rebind("InventoryService", inventoryService);
            this.registry.rebind("SynchronizationService", syncService);
            logger.info("Bound 'InventoryService' and 'SynchronizationService' to RMI Registry on port {}", config.getRmiRegistryPort());

            this.running = true;

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "ShutdownHook-" + config.getServerId()));

            logger.info(">>> Server [{}] is ONLINE and ready to accept Client and Peer requests.", config.getServerId());
            logger.info(">>> Local DB: {}", config.getDbPath());
            logger.info(">>> Peer Target: {} ({}:{})", config.getPeerServerId(), config.getPeerHost(), config.getPeerRmiRegistryPort());
            logger.info("======================================================");

        } catch (Exception e) {
            logger.error("FATAL: Failed to start server: {}", e.getMessage(), e);
            throw new RuntimeException("Server start failure", e);
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        logger.info("Shutting down server {}...", config.getServerId());
        if (syncManager != null) {
            syncManager.stop();
        }
        try {
            if (registry != null) {
                try { registry.unbind("InventoryService"); } catch (Exception ignored) {}
                try { registry.unbind("SynchronizationService"); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            logger.warn("Error unbinding RMI services: {}", e.getMessage());
        }
        try {
            if (inventoryService != null) {
                UnicastRemoteObject.unexportObject(inventoryService, true);
            }
            if (syncService != null) {
                UnicastRemoteObject.unexportObject(syncService, true);
            }
        } catch (Exception e) {
            logger.warn("Error unexporting remote objects: {}", e.getMessage());
        }
        logger.info("Server {} has been stopped cleanly.", config.getServerId());
    }

    public static void main(String[] args) {
        String configFile = (args.length > 0) ? args[0] : "server1.properties";
        ServerConfig config = ServerConfig.load(configFile);
        ServerApplication app = new ServerApplication(config);
        app.start();
    }
}
