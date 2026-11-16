package com.inventory.server.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    private final String dbUrl;
    private final Object lock = new Object();

    public DatabaseManager(String dbPath) {
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (created) {
                logger.info("Created directory for database: {}", parentDir.getAbsolutePath());
            }
        }
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
            stmt.execute("PRAGMA busy_timeout=5000;");
            stmt.execute("PRAGMA foreign_keys=ON;");
        }
        return conn;
    }

    private void initializeDatabase() {
        logger.info("Initializing SQLite database at {}", dbUrl);
        synchronized (lock) {
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                // Table: products
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS products (
                        id VARCHAR(36) PRIMARY KEY,
                        product_code VARCHAR(50) NOT NULL UNIQUE,
                        product_name VARCHAR(255) NOT NULL,
                        category VARCHAR(100) NOT NULL,
                        quantity INTEGER NOT NULL DEFAULT 0,
                        price REAL NOT NULL DEFAULT 0.0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        version INTEGER NOT NULL DEFAULT 1,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        last_modified_by_server VARCHAR(50) NOT NULL
                    );
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_code ON products(product_code);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_deleted ON products(is_deleted);");

                // Table: synchronization_events (Outbox log for replication to peer)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS synchronization_events (
                        event_id VARCHAR(36) PRIMARY KEY,
                        entity_type VARCHAR(50) NOT NULL,
                        entity_id VARCHAR(36) NOT NULL,
                        event_type VARCHAR(20) NOT NULL,
                        payload_json TEXT NOT NULL,
                        origin_server_id VARCHAR(50) NOT NULL,
                        target_server_id VARCHAR(50) NOT NULL,
                        timestamp INTEGER NOT NULL,
                        version INTEGER NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        retry_count INTEGER NOT NULL DEFAULT 0,
                        last_error TEXT
                    );
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_sync_status ON synchronization_events(status);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_sync_timestamp ON synchronization_events(timestamp);");

                // Table: processed_events (Idempotency log of events received from peer)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS processed_events (
                        event_id VARCHAR(36) PRIMARY KEY,
                        origin_server_id VARCHAR(50) NOT NULL,
                        processed_at INTEGER NOT NULL,
                        status VARCHAR(20) NOT NULL
                    );
                """);

                // Table: sync_conflicts (Log of detected concurrent conflicts)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sync_conflicts (
                        conflict_id VARCHAR(36) PRIMARY KEY,
                        event_id VARCHAR(36) NOT NULL,
                        entity_id VARCHAR(36) NOT NULL,
                        local_data TEXT NOT NULL,
                        remote_data TEXT NOT NULL,
                        detected_at INTEGER NOT NULL,
                        resolved INTEGER NOT NULL DEFAULT 0,
                        resolution_note TEXT
                    );
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_conflicts_resolved ON sync_conflicts(resolved);");

                logger.info("SQLite database tables initialized successfully.");
            } catch (SQLException e) {
                logger.error("Failed to initialize database tables", e);
                throw new RuntimeException("Database initialization error", e);
            }
        }
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T doInTransaction(Connection conn) throws SQLException, Exception;
    }

    public <T> T executeInTransaction(TransactionCallback<T> callback) throws Exception {
        synchronized (lock) {
            Connection conn = null;
            boolean originalAutoCommit = true;
            try {
                conn = getConnection();
                originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                T result = callback.doInTransaction(conn);

                conn.commit();
                return result;
            } catch (Exception e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                        logger.warn("Transaction rolled back due to error: {}", e.getMessage());
                    } catch (SQLException rollbackEx) {
                        logger.error("Failed to rollback transaction", rollbackEx);
                    }
                }
                throw e;
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(originalAutoCommit);
                        conn.close();
                    } catch (SQLException ex) {
                        logger.error("Failed to close connection", ex);
                    }
                }
            }
        }
    }
}
