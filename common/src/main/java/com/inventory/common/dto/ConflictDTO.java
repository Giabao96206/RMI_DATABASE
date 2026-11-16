package com.inventory.common.dto;

import java.io.Serializable;

public class ConflictDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String conflictId;
    private String eventId;
    private String entityId;
    private String localDataJson;
    private String remoteDataJson;
    private long detectedAt;
    private boolean resolved;
    private String resolutionNote;

    public ConflictDTO() {
    }

    public ConflictDTO(String conflictId, String eventId, String entityId,
                       String localDataJson, String remoteDataJson, long detectedAt,
                       boolean resolved, String resolutionNote) {
        this.conflictId = conflictId;
        this.eventId = eventId;
        this.entityId = entityId;
        this.localDataJson = localDataJson;
        this.remoteDataJson = remoteDataJson;
        this.detectedAt = detectedAt;
        this.resolved = resolved;
        this.resolutionNote = resolutionNote;
    }

    public String getConflictId() {
        return conflictId;
    }

    public void setConflictId(String conflictId) {
        this.conflictId = conflictId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getLocalDataJson() {
        return localDataJson;
    }

    public void setLocalDataJson(String localDataJson) {
        this.localDataJson = localDataJson;
    }

    public String getRemoteDataJson() {
        return remoteDataJson;
    }

    public void setRemoteDataJson(String remoteDataJson) {
        this.remoteDataJson = remoteDataJson;
    }

    public long getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(long detectedAt) {
        this.detectedAt = detectedAt;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }

    @Override
    public String toString() {
        return "ConflictDTO{" +
                "conflictId='" + conflictId + '\'' +
                ", eventId='" + eventId + '\'' +
                ", entityId='" + entityId + '\'' +
                ", detectedAt=" + detectedAt +
                ", resolved=" + resolved +
                '}';
    }
}
