package com.inventory.common.exception;

public class ConflictException extends InventoryException {
    private static final long serialVersionUID = 1L;

    private final String conflictId;
    private final String entityId;

    public ConflictException(String conflictId, String entityId, String message) {
        super(message);
        this.conflictId = conflictId;
        this.entityId = entityId;
    }

    public String getConflictId() {
        return conflictId;
    }

    public String getEntityId() {
        return entityId;
    }
}
