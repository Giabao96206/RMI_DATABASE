package com.inventory.common.dto;

import com.inventory.common.model.EventType;
import com.inventory.common.model.SyncStatus;

import java.io.Serializable;
import java.util.Objects;

public class SyncEventDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventId;
    private String entityType;
    private String entityId;
    private EventType eventType;
    private String payloadJson;
    private String originServerId;
    private String targetServerId;
    private long timestamp;
    private int version;
    private SyncStatus status;
    private int retryCount;
    private String lastError;

    public SyncEventDTO() {
    }

    public SyncEventDTO(String eventId, String entityType, String entityId, EventType eventType,
                        String payloadJson, String originServerId, String targetServerId,
                        long timestamp, int version, SyncStatus status, int retryCount, String lastError) {
        this.eventId = eventId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.originServerId = originServerId;
        this.targetServerId = targetServerId;
        this.timestamp = timestamp;
        this.version = version;
        this.status = status;
        this.retryCount = retryCount;
        this.lastError = lastError;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getOriginServerId() {
        return originServerId;
    }

    public void setOriginServerId(String originServerId) {
        this.originServerId = originServerId;
    }

    public String getTargetServerId() {
        return targetServerId;
    }

    public void setTargetServerId(String targetServerId) {
        this.targetServerId = targetServerId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public void setStatus(SyncStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SyncEventDTO that = (SyncEventDTO) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "SyncEventDTO{" +
                "eventId='" + eventId + '\'' +
                ", entityType='" + entityType + '\'' +
                ", entityId='" + entityId + '\'' +
                ", eventType=" + eventType +
                ", originServerId='" + originServerId + '\'' +
                ", targetServerId='" + targetServerId + '\'' +
                ", timestamp=" + timestamp +
                ", version=" + version +
                ", status=" + status +
                ", retryCount=" + retryCount +
                '}';
    }
}
