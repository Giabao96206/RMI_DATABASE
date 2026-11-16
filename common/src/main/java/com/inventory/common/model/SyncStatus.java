package com.inventory.common.model;

import java.io.Serializable;

public enum SyncStatus implements Serializable {
    PENDING,
    SUCCESS,
    FAILED,
    CONFLICT
}
