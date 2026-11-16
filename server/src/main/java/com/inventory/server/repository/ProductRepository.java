package com.inventory.server.repository;

import com.inventory.common.dto.InventoryStatsDTO;
import com.inventory.common.dto.ProductDTO;
import com.inventory.server.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository {
    private static final Logger logger = LoggerFactory.getLogger(ProductRepository.class);
    private final DatabaseManager dbManager;

    public ProductRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void insert(Connection conn, ProductDTO p) throws SQLException {
        String sql = """
            INSERT INTO products (id, product_code, product_name, category, quantity, price, created_at, updated_at, version, is_deleted, last_modified_by_server)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getId());
            ps.setString(2, p.getProductCode());
            ps.setString(3, p.getProductName());
            ps.setString(4, p.getCategory());
            ps.setInt(5, p.getQuantity());
            ps.setDouble(6, p.getPrice());
            ps.setLong(7, p.getCreatedAt());
            ps.setLong(8, p.getUpdatedAt());
            ps.setInt(9, p.getVersion());
            ps.setInt(10, p.isDeleted() ? 1 : 0);
            ps.setString(11, p.getLastModifiedByServer());
            ps.executeUpdate();
        }
    }

    public boolean update(Connection conn, ProductDTO p) throws SQLException {
        String sql = """
            UPDATE products 
            SET product_code = ?, product_name = ?, category = ?, quantity = ?, price = ?, updated_at = ?, version = ?, is_deleted = ?, last_modified_by_server = ?
            WHERE id = ? AND version = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getProductCode());
            ps.setString(2, p.getProductName());
            ps.setString(3, p.getCategory());
            ps.setInt(4, p.getQuantity());
            ps.setDouble(5, p.getPrice());
            ps.setLong(6, p.getUpdatedAt());
            ps.setInt(7, p.getVersion() + 1); // increment version
            ps.setInt(8, p.isDeleted() ? 1 : 0);
            ps.setString(9, p.getLastModifiedByServer());
            ps.setString(10, p.getId());
            ps.setInt(11, p.getVersion()); // optimistic lock condition
            int rows = ps.executeUpdate();
            if (rows > 0) {
                p.setVersion(p.getVersion() + 1);
                return true;
            }
            return false;
        }
    }

    /**
     * Force update is used during synchronization when applying remote state directly.
     */
    public void forceUpsert(Connection conn, ProductDTO p) throws SQLException {
        String sql = """
            INSERT INTO products (id, product_code, product_name, category, quantity, price, created_at, updated_at, version, is_deleted, last_modified_by_server)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                product_code = excluded.product_code,
                product_name = excluded.product_name,
                category = excluded.category,
                quantity = excluded.quantity,
                price = excluded.price,
                updated_at = excluded.updated_at,
                version = excluded.version,
                is_deleted = excluded.is_deleted,
                last_modified_by_server = excluded.last_modified_by_server
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getId());
            ps.setString(2, p.getProductCode());
            ps.setString(3, p.getProductName());
            ps.setString(4, p.getCategory());
            ps.setInt(5, p.getQuantity());
            ps.setDouble(6, p.getPrice());
            ps.setLong(7, p.getCreatedAt());
            ps.setLong(8, p.getUpdatedAt());
            ps.setInt(9, p.getVersion());
            ps.setInt(10, p.isDeleted() ? 1 : 0);
            ps.setString(11, p.getLastModifiedByServer());
            ps.executeUpdate();
        }
    }

    public boolean softDelete(Connection conn, String productId, long updatedAt, String modifiedByServer) throws SQLException {
        String sql = """
            UPDATE products 
            SET is_deleted = 1, updated_at = ?, version = version + 1, last_modified_by_server = ?
            WHERE id = ? AND is_deleted = 0
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, updatedAt);
            ps.setString(2, modifiedByServer);
            ps.setString(3, productId);
            return ps.executeUpdate() > 0;
        }
    }

    public ProductDTO findById(Connection conn, String productId) throws SQLException {
        String sql = "SELECT * FROM products WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public ProductDTO findById(String productId) {
        try (Connection conn = dbManager.getConnection()) {
            return findById(conn, productId);
        } catch (SQLException e) {
            logger.error("Error finding product by id: {}", productId, e);
        }
        return null;
    }

    public ProductDTO findByProductCode(String code) {
        String sql = "SELECT * FROM products WHERE product_code = ? AND is_deleted = 0";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding product by code: {}", code, e);
        }
        return null;
    }

    public List<ProductDTO> findAll(boolean includeDeleted) {
        List<ProductDTO> list = new ArrayList<>();
        String sql = includeDeleted ? 
                "SELECT * FROM products ORDER BY product_name ASC" : 
                "SELECT * FROM products WHERE is_deleted = 0 ORDER BY product_name ASC";
        try (Connection conn = dbManager.getConnection(); 
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("Error querying all products", e);
        }
        return list;
    }

    public List<ProductDTO> search(String keyword) {
        List<ProductDTO> list = new ArrayList<>();
        String sql = """
            SELECT * FROM products 
            WHERE is_deleted = 0 AND (product_code LIKE ? OR product_name LIKE ? OR category LIKE ?)
            ORDER BY product_name ASC
        """;
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + keyword + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching products with keyword: {}", keyword, e);
        }
        return list;
    }

    public InventoryStatsDTO getStatistics() {
        String sql = """
            SELECT 
                COUNT(*) as total_items,
                SUM(quantity) as total_qty,
                SUM(quantity * price) as total_val,
                SUM(CASE WHEN quantity < 10 THEN 1 ELSE 0 END) as low_stock,
                COUNT(DISTINCT category) as categories
            FROM products 
            WHERE is_deleted = 0
        """;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new InventoryStatsDTO(
                        rs.getInt("total_items"),
                        rs.getInt("total_qty"),
                        rs.getDouble("total_val"),
                        rs.getInt("low_stock"),
                        rs.getInt("categories")
                );
            }
        } catch (SQLException e) {
            logger.error("Error calculating inventory statistics", e);
        }
        return new InventoryStatsDTO(0, 0, 0.0, 0, 0);
    }

    private ProductDTO mapRow(ResultSet rs) throws SQLException {
        return new ProductDTO(
                rs.getString("id"),
                rs.getString("product_code"),
                rs.getString("product_name"),
                rs.getString("category"),
                rs.getInt("quantity"),
                rs.getDouble("price"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                rs.getInt("version"),
                rs.getInt("is_deleted") == 1,
                rs.getString("last_modified_by_server")
        );
    }
}
