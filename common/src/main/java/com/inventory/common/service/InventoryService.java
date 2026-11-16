package com.inventory.common.service;

import com.inventory.common.dto.ConflictDTO;
import com.inventory.common.dto.InventoryStatsDTO;
import com.inventory.common.dto.ProductDTO;
import com.inventory.common.dto.ServerStatusDTO;
import com.inventory.common.dto.SyncEventDTO;
import com.inventory.common.exception.InventoryException;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote interface for Client to Server RMI communication.
 * Provides all inventory management, monitoring, and conflict resolution operations.
 */
public interface InventoryService extends Remote {

    /**
     * Add a new product to inventory.
     * Persists in local database and queues a synchronization event for peer server.
     */
    ProductDTO addProduct(ProductDTO product) throws RemoteException, InventoryException;

    /**
     * Update an existing product.
     * Checks optimistic concurrency version and queues synchronization event.
     */
    ProductDTO updateProduct(ProductDTO product) throws RemoteException, InventoryException;

    /**
     * Soft delete a product by ID.
     */
    boolean deleteProduct(String productId) throws RemoteException, InventoryException;

    /**
     * Get all active (non-deleted) products.
     */
    List<ProductDTO> getAllProducts() throws RemoteException;

    /**
     * Get product by UUID.
     */
    ProductDTO getProductById(String productId) throws RemoteException;

    /**
     * Search products by product code or name.
     */
    List<ProductDTO> searchProducts(String keyword) throws RemoteException;

    /**
     * Compute and return inventory statistics (total items, total quantity, total value, etc.).
     */
    InventoryStatsDTO getInventoryStatistics() throws RemoteException;

    /**
     * Get current server health, RMI ports, pending sync queues, and peer connectivity status.
     */
    ServerStatusDTO getServerStatus() throws RemoteException;

    /**
     * Retrieve recent synchronization history log.
     */
    List<SyncEventDTO> getSyncHistory(int limit) throws RemoteException;

    /**
     * Get list of detected unresolved data conflicts.
     */
    List<ConflictDTO> getConflicts() throws RemoteException;

    /**
     * Resolve a data conflict manually with chosen product data.
     */
    boolean resolveConflict(String conflictId, ProductDTO resolvedProduct) throws RemoteException, InventoryException;

    /**
     * Heartbeat check.
     */
    boolean ping() throws RemoteException;
}
