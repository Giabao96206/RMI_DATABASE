package com.inventory.server.repository;

import com.inventory.common.dto.ConflictDTO;
import com.inventory.common.dto.SyncEventDTO;
import com.inventory.common.model.EventType;
import com.inventory.common.model.SyncStatus;
import com.inventory.server.database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SyncRepository {
    private static final Logger logger = LoggerFactory.getLogger(SyncRepository.class);
    private final DatabaseManager dbManager;

    public SyncRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void insertEvent(Connection conn, SyncEventDTO event) throws SQLException {
        String sql = """
            INSERT INTO synchronization_events (
                event_id, entity_type, entity_id, event_type, payload_json, 
                origin_server_id, target_server_id, timestamp, version, status, retry_count, last_error
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.getEventId());
            ps.setString(2, event.getEntityType());
            ps.setString(3, event.getEntityId());
            ps.setString(4, event.getEventType().name());
            ps.setString(5, event.getPayloadJson());
            ps.setString(6, event.getOriginServerId());
            ps.setString(7, event.getTargetServerId());
            ps.setLong(8, event.getTimestamp());
            ps.setInt(9, event.getVersion());
            ps.setString(10, event.getStatus().name());
            ps.setInt(11, event.getRetryCount());
            ps.setString(12, event.getLastError());
            ps.executeUpdate();
        }
    }

    public List<SyncEventDTO> getPendingEvents(String targetServerId, int limit) {
        List<SyncEventDTO> list = new ArrayList<>();
        String sql = """
            SELECT * FROM synchronization_events 
            WHERE status = 'PENDING' AND target_server_id = ?
            ORDER BY timestamp ASC LIMIT ?
        """;
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetServerId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSyncEvent(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching pending sync events for target {}", targetServerId, e);
        }
        return list;
    }

    public void updateEventStatus(String eventId, SyncStatus status, String lastError) {
        String sql = """
            UPDATE synchronization_events 
            SET status = ?, last_error = ?, retry_count = retry_count + 1
            WHERE event_id = ?
        """;
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, lastError);
            ps.setString(3, eventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating sync event status: id={}, status={}", eventId, status, e);
        }
    }

    public void acknowledgeEvents(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) return;
        String sql = "UPDATE synchronization_events SET status = 'SUCCESS', last_error = NULL WHERE event_id = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String id : eventIds) {
                ps.setString(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            logger.error("Error acknowledging sync events", e);
        }
    }

    public boolean hasEvent(String eventId) {
        String sql = "SELECT 1 FROM synchronization_events WHERE event_id = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking sync event existence: {}", eventId, e);
        }
        return false;
    }

    public boolean isEventProcessed(String eventId) {
        String sql = "SELECT 1 FROM processed_events WHERE event_id = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Error checking processed event: {}", eventId, e);
        }
        return false;
    }

    public void recordProcessedEvent(Connection conn, String eventId, String originServerId, SyncStatus status) throws SQLException {
        String sql = "INSERT OR IGNORE INTO processed_events (event_id, origin_server_id, processed_at, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.setString(2, originServerId);
            ps.setLong(3, System.currentTimeMillis());
            ps.setString(4, status.name());
            ps.executeUpdate();
        }
    }

    public void recordIncomingSyncEvent(Connection conn, SyncEventDTO event) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO synchronization_events (
                event_id, entity_type, entity_id, event_type, payload_json, 
                origin_server_id, target_server_id, timestamp, version, status, retry_count, last_error
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', 0, NULL)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.getEventId());
            ps.setString(2, event.getEntityType());
            ps.setString(3, event.getEntityId());
            ps.setString(4, event.getEventType().name());
            ps.setString(5, event.getPayloadJson());
            ps.setString(6, event.getOriginServerId());
            ps.setString(7, event.getTargetServerId());
            ps.setLong(8, event.getTimestamp());
            ps.setInt(9, event.getVersion());
            ps.executeUpdate();
        }
    }

    public void recordConflict(Connection conn, ConflictDTO conflict) throws SQLException {
        String sql = """
            INSERT INTO sync_conflicts (conflict_id, event_id, entity_id, local_data, remote_data, detected_at, resolved, resolution_note)
            VALUES (?, ?, ?, ?, ?, ?, 0, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conflict.getConflictId());
            ps.setString(2, conflict.getEventId());
            ps.setString(3, conflict.getEntityId());
            ps.setString(4, conflict.getLocalDataJson());
            ps.setString(5, conflict.getRemoteDataJson());
            ps.setLong(6, conflict.getDetectedAt());
            ps.setString(7, conflict.getResolutionNote());
            ps.executeUpdate();
        }
    }

    public List<ConflictDTO> getConflicts(boolean unresolvedOnly) {
        List<ConflictDTO> list = new ArrayList<>();
        String sql = unresolvedOnly ?
                "SELECT * FROM sync_conflicts WHERE resolved = 0 ORDER BY detected_at DESC" :
                "SELECT * FROM sync_conflicts ORDER BY detected_at DESC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new ConflictDTO(
                        rs.getString("conflict_id"),
                        rs.getString("event_id"),
                        rs.getString("entity_id"),
                        rs.getString("local_data"),
                        rs.getString("remote_data"),
                        rs.getLong("detected_at"),
                        rs.getInt("resolved") == 1,
                        rs.getString("resolution_note")
                ));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving sync conflicts", e);
        }
        return list;
    }

    public boolean markConflictResolved(Connection conn, String conflictId, String note) throws SQLException {
        String sql = "UPDATE sync_conflicts SET resolved = 1, resolution_note = ? WHERE conflict_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, note);
            ps.setString(2, conflictId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean markConflictResolved(String conflictId, String note) {
        try (Connection conn = dbManager.getConnection()) {
            return markConflictResolved(conn, conflictId, note);
        } catch (SQLException e) {
            logger.error("Error marking conflict resolved: {}", conflictId, e);
        }
        return false;
    }

    public List<SyncEventDTO> getRecentHistory(int limit) {
        List<SyncEventDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM synchronization_events ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSyncEvent(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying sync history", e);
        }
        return list;
    }

    public List<SyncEventDTO> getEventsAfter(long timestamp) {
        List<SyncEventDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM synchronization_events WHERE timestamp >= ? ORDER BY timestamp ASC";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, timestamp);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSyncEvent(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting events after timestamp: {}", timestamp, e);
        }
        return list;
    }

    public int getPendingCount() {
        String sql = "SELECT COUNT(*) FROM synchronization_events WHERE status = 'PENDING'";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("Error getting pending events count", e);
        }
        return 0;
    }

    public int getProcessedCount() {
        String sql = "SELECT COUNT(*) FROM processed_events";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("Error getting processed events count", e);
        }
        return 0;
    }

    public int getUnresolvedConflictCount() {
        String sql = "SELECT COUNT(*) FROM sync_conflicts WHERE resolved = 0";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("Error getting unresolved conflicts count", e);
        }
        return 0;
    }

    private SyncEventDTO mapSyncEvent(ResultSet rs) throws SQLException {
        return new SyncEventDTO(
                rs.getString("event_id"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                EventType.valueOf(rs.getString("event_type")),
                rs.getString("payload_json"),
                rs.getString("origin_server_id"),
                rs.getString("target_server_id"),
                rs.getLong("timestamp"),
                rs.getInt("version"),
                SyncStatus.valueOf(rs.getString("status")),
                rs.getInt("retry_count"),
                rs.getString("last_error")
        );
    }
}
