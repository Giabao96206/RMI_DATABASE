package com.inventory.common.dto;

import java.io.Serializable;

public class SyncStatusDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String serverId;
    private long currentTimestamp;
    private int pendingEventsCount;
    private int processedEventsCount;
    private int unresolvedConflictsCount;
    private long lastSyncTimestamp;
    private boolean peerReachable;

    public SyncStatusDTO() {
    }

    public SyncStatusDTO(String serverId, long currentTimestamp, int pendingEventsCount,
                         int processedEventsCount, int unresolvedConflictsCount,
                         long lastSyncTimestamp, boolean peerReachable) {
        this.serverId = serverId;
        this.currentTimestamp = currentTimestamp;
        this.pendingEventsCount = pendingEventsCount;
        this.processedEventsCount = processedEventsCount;
        this.unresolvedConflictsCount = unresolvedConflictsCount;
        this.lastSyncTimestamp = lastSyncTimestamp;
        this.peerReachable = peerReachable;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public long getCurrentTimestamp() {
        return currentTimestamp;
    }

    public void setCurrentTimestamp(long currentTimestamp) {
        this.currentTimestamp = currentTimestamp;
    }

    public int getPendingEventsCount() {
        return pendingEventsCount;
    }

    public void setPendingEventsCount(int pendingEventsCount) {
        this.pendingEventsCount = pendingEventsCount;
    }

    public int getProcessedEventsCount() {
        return processedEventsCount;
    }

    public void setProcessedEventsCount(int processedEventsCount) {
        this.processedEventsCount = processedEventsCount;
    }

    public int getUnresolvedConflictsCount() {
        return unresolvedConflictsCount;
    }

    public void setUnresolvedConflictsCount(int unresolvedConflictsCount) {
        this.unresolvedConflictsCount = unresolvedConflictsCount;
    }

    public long getLastSyncTimestamp() {
        return lastSyncTimestamp;
    }

    public void setLastSyncTimestamp(long lastSyncTimestamp) {
        this.lastSyncTimestamp = lastSyncTimestamp;
    }

    public boolean isPeerReachable() {
        return peerReachable;
    }

    public void setPeerReachable(boolean peerReachable) {
        this.peerReachable = peerReachable;
    }

    @Override
    public String toString() {
        return "SyncStatusDTO{" +
                "serverId='" + serverId + '\'' +
                ", pendingEventsCount=" + pendingEventsCount +
                ", processedEventsCount=" + processedEventsCount +
                ", unresolvedConflictsCount=" + unresolvedConflictsCount +
                ", peerReachable=" + peerReachable +
                '}';
    }
}
