package com.inventory.common.service;

import com.inventory.common.dto.SyncEventDTO;
import com.inventory.common.dto.SyncStatusDTO;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote interface for Server to Server RMI communication.
 * Responsible for active replication, event delivery, idempotency acknowledgement,
 * and catch-up synchronization after network reconnection.
 */
public interface SynchronizationService extends Remote {

    /**
     * Deliver a batch of synchronization events from peer server.
     * The receiving server processes events idempotently and records them.
     *
     * @param events list of sync events to apply
     * @return true if all events were processed or handled safely (idempotent/conflict logged)
     */
    boolean receiveEvents(List<SyncEventDTO> events) throws RemoteException;

    /**
     * Request events that were created or modified after a specific epoch timestamp.
     * Used for catch-up synchronization during server startup / reconnect.
     */
    List<SyncEventDTO> getEventsAfter(long timestamp) throws RemoteException;

    /**
     * Retrieve events destined for the target server that are currently pending.
     */
    List<SyncEventDTO> getPendingEvents(String targetServerId, int limit) throws RemoteException;

    /**
     * Get the synchronization status of this server.
     */
    SyncStatusDTO getSyncStatus() throws RemoteException;

    /**
     * Acknowledge that the specified events have been successfully processed by peer.
     * The origin server marks these events as SUCCESS in its local synchronization_events table.
     */
    boolean acknowledgeEvents(List<String> eventIds) throws RemoteException;

    /**
     * Get identifier of this server (e.g. "SERVER_1", "SERVER_2").
     */
    String getServerId() throws RemoteException;

    /**
     * Server-to-server heartbeat ping.
     */
    boolean ping() throws RemoteException;
}
