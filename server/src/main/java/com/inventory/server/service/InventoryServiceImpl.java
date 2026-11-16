package com.inventory.server.service;

import com.inventory.common.dto.ConflictDTO;
import com.inventory.common.dto.InventoryStatsDTO;
import com.inventory.common.dto.ProductDTO;
import com.inventory.common.dto.ServerStatusDTO;
import com.inventory.common.dto.SyncEventDTO;
import com.inventory.common.exception.InventoryException;
import com.inventory.common.exception.ProductNotFoundException;
import com.inventory.common.model.EventType;
import com.inventory.common.model.SyncStatus;
import com.inventory.common.service.InventoryService;
import com.inventory.common.util.JsonUtils;
import com.inventory.server.config.ServerConfig;
import com.inventory.server.database.DatabaseManager;
import com.inventory.server.repository.ProductRepository;
import com.inventory.server.repository.SyncRepository;
import com.inventory.server.synchronization.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.UUID;

public class InventoryServiceImpl extends UnicastRemoteObject implements InventoryService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final ServerConfig config;
    private final DatabaseManager dbManager;
    private final ProductRepository productRepository;
    private final SyncRepository syncRepository;
    private final SyncManager syncManager;
    private final long startupTime;

    public InventoryServiceImpl(ServerConfig config, DatabaseManager dbManager,
                                ProductRepository productRepository, SyncRepository syncRepository,
                                SyncManager syncManager) throws RemoteException {
        super(config.getRmiExportPort());
        this.config = config;
        this.dbManager = dbManager;
        this.productRepository = productRepository;
        this.syncRepository = syncRepository;
        this.syncManager = syncManager;
        this.startupTime = System.currentTimeMillis();
    }

    @Override
    public ProductDTO addProduct(ProductDTO product) throws RemoteException, InventoryException {
        validateProduct(product);

        // Check product code uniqueness
        ProductDTO existing = productRepository.findByProductCode(product.getProductCode());
        if (existing != null) {
            throw new InventoryException("Product code already exists: " + product.getProductCode());
        }

        long now = System.currentTimeMillis();
        if (product.getId() == null || product.getId().trim().isEmpty()) {
            product.setId(UUID.randomUUID().toString());
        }
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        product.setVersion(1);
        product.setDeleted(false);
        product.setLastModifiedByServer(config.getServerId());

        SyncEventDTO syncEvent = new SyncEventDTO(
                UUID.randomUUID().toString(),
                "PRODUCT",
                product.getId(),
                EventType.CREATE,
                JsonUtils.toJson(product),
                config.getServerId(),
                config.getPeerServerId(),
                now,
                1,
                SyncStatus.PENDING,
                0,
                null
        );

        try {
            dbManager.executeInTransaction(conn -> {
                productRepository.insert(conn, product);
                syncRepository.insertEvent(conn, syncEvent);
                return null;
            });
            logger.info("[CLIENT-ACTION] Created product {} (code={}) on {}", product.getId(), product.getProductCode(), config.getServerId());
            syncManager.triggerImmediateSync();
            return product;
        } catch (Exception e) {
            logger.error("Failed to add product: {}", e.getMessage(), e);
            throw new InventoryException("Failed to add product: " + e.getMessage(), e);
        }
    }

    @Override
    public ProductDTO updateProduct(ProductDTO product) throws RemoteException, InventoryException {
        if (product.getId() == null || product.getId().trim().isEmpty()) {
            throw new InventoryException("Product ID is required for update.");
        }
        validateProduct(product);

        ProductDTO existing = productRepository.findById(product.getId());
        if (existing == null || existing.isDeleted()) {
            throw new ProductNotFoundException(product.getId());
        }

        // Check if product code changed to an already existing code
        ProductDTO codeOwner = productRepository.findByProductCode(product.getProductCode());
        if (codeOwner != null && !codeOwner.getId().equals(product.getId())) {
            throw new InventoryException("Product code already in use by another product: " + product.getProductCode());
        }

        long now = System.currentTimeMillis();
        product.setUpdatedAt(now);
        product.setLastModifiedByServer(config.getServerId());

        try {
            dbManager.executeInTransaction(conn -> {
                boolean updated = productRepository.update(conn, product);
                if (!updated) {
                    throw new InventoryException("Optimistic locking conflict: Product was modified by another transaction. Please reload.");
                }

                SyncEventDTO syncEvent = new SyncEventDTO(
                        UUID.randomUUID().toString(),
                        "PRODUCT",
                        product.getId(),
                        EventType.UPDATE,
                        JsonUtils.toJson(product),
                        config.getServerId(),
                        config.getPeerServerId(),
                        now,
                        product.getVersion(),
                        SyncStatus.PENDING,
                        0,
                        null
                );
                syncRepository.insertEvent(conn, syncEvent);
                return null;
            });
            logger.info("[CLIENT-ACTION] Updated product {} (v{}) on {}", product.getId(), product.getVersion(), config.getServerId());
            syncManager.triggerImmediateSync();
            return product;
        } catch (InventoryException ie) {
            throw ie;
        } catch (Exception e) {
            logger.error("Failed to update product: {}", e.getMessage(), e);
            throw new InventoryException("Failed to update product: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteProduct(String productId) throws RemoteException, InventoryException {
        if (productId == null || productId.trim().isEmpty()) {
            throw new InventoryException("Product ID is required for deletion.");
        }

        ProductDTO existing = productRepository.findById(productId);
        if (existing == null || existing.isDeleted()) {
            throw new ProductNotFoundException(productId);
        }

        long now = System.currentTimeMillis();
        SyncEventDTO syncEvent = new SyncEventDTO(
                UUID.randomUUID().toString(),
                "PRODUCT",
                productId,
                EventType.DELETE,
                JsonUtils.toJson(existing),
                config.getServerId(),
                config.getPeerServerId(),
                now,
                existing.getVersion() + 1,
                SyncStatus.PENDING,
                0,
                null
        );

        try {
            dbManager.executeInTransaction(conn -> {
                boolean deleted = productRepository.softDelete(conn, productId, now, config.getServerId());
                if (!deleted) {
                    throw new ProductNotFoundException(productId);
                }
                syncRepository.insertEvent(conn, syncEvent);
                return null;
            });
            logger.info("[CLIENT-ACTION] Deleted product {} on {}", productId, config.getServerId());
            syncManager.triggerImmediateSync();
            return true;
        } catch (InventoryException ie) {
            throw ie;
        } catch (Exception e) {
            logger.error("Failed to delete product: {}", e.getMessage(), e);
            throw new InventoryException("Failed to delete product: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ProductDTO> getAllProducts() throws RemoteException {
        return productRepository.findAll(false);
    }

    @Override
    public ProductDTO getProductById(String productId) throws RemoteException {
        return productRepository.findById(productId);
    }

    @Override
    public List<ProductDTO> searchProducts(String keyword) throws RemoteException {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllProducts();
        }
        return productRepository.search(keyword.trim());
    }

    @Override
    public InventoryStatsDTO getInventoryStatistics() throws RemoteException {
        return productRepository.getStatistics();
    }

    @Override
    public ServerStatusDTO getServerStatus() throws RemoteException {
        int totalProducts = productRepository.findAll(false).size();
        int pendingEvents = syncRepository.getPendingCount();
        long lastSync = syncManager.getLastSuccessfulSyncTime();
        boolean peerConnected = syncManager.isPeerConnected();

        return new ServerStatusDTO(
                config.getServerId(),
                config.getServerHost(),
                config.getRmiRegistryPort(),
                true,
                System.currentTimeMillis() - startupTime,
                totalProducts,
                pendingEvents,
                lastSync,
                config.getPeerServerId(),
                peerConnected
        );
    }

    @Override
    public List<SyncEventDTO> getSyncHistory(int limit) throws RemoteException {
        return syncRepository.getRecentHistory(limit);
    }

    @Override
    public List<ConflictDTO> getConflicts() throws RemoteException {
        return syncRepository.getConflicts(true);
    }

    @Override
    public boolean resolveConflict(String conflictId, ProductDTO resolvedProduct) throws RemoteException, InventoryException {
        if (conflictId == null || resolvedProduct == null) {
            throw new InventoryException("Conflict ID and resolved product are required.");
        }

        long now = System.currentTimeMillis();
        resolvedProduct.setUpdatedAt(now);
        resolvedProduct.setVersion(resolvedProduct.getVersion() + 1);
        resolvedProduct.setLastModifiedByServer(config.getServerId());

        try {
            dbManager.executeInTransaction(conn -> {
                productRepository.forceUpsert(conn, resolvedProduct);
                syncRepository.markConflictResolved(conn, conflictId, "Resolved by admin on " + config.getServerId());

                SyncEventDTO syncEvent = new SyncEventDTO(
                        UUID.randomUUID().toString(),
                        "PRODUCT",
                        resolvedProduct.getId(),
                        EventType.UPDATE,
                        JsonUtils.toJson(resolvedProduct),
                        config.getServerId(),
                        config.getPeerServerId(),
                        now,
                        resolvedProduct.getVersion(),
                        SyncStatus.PENDING,
                        0,
                        null
                );
                syncRepository.insertEvent(conn, syncEvent);
                return null;
            });
            logger.info("[CONFLICT-RESOLVED] Conflict {} resolved with product {}", conflictId, resolvedProduct.getId());
            syncManager.triggerImmediateSync();
            return true;
        } catch (Exception e) {
            logger.error("Failed to resolve conflict {}: {}", conflictId, e.getMessage(), e);
            throw new InventoryException("Failed to resolve conflict: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    private void validateProduct(ProductDTO p) throws InventoryException {
        if (p == null) {
            throw new InventoryException("Product cannot be null.");
        }
        if (p.getProductCode() == null || p.getProductCode().trim().isEmpty()) {
            throw new InventoryException("Product code cannot be empty.");
        }
        if (p.getProductName() == null || p.getProductName().trim().isEmpty()) {
            throw new InventoryException("Product name cannot be empty.");
        }
        if (p.getCategory() == null || p.getCategory().trim().isEmpty()) {
            throw new InventoryException("Product category cannot be empty.");
        }
        if (p.getQuantity() < 0) {
            throw new InventoryException("Product quantity cannot be negative.");
        }
        if (p.getPrice() < 0.0) {
            throw new InventoryException("Product price cannot be negative.");
        }
    }
}
