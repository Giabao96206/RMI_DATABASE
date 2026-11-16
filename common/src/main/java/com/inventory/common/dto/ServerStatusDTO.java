package com.inventory.common.dto;

import java.io.Serializable;

public class ServerStatusDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String serverId;
    private String host;
    private int rmiPort;
    private boolean online;
    private long uptimeMillis;
    private int totalProducts;
    private int pendingSyncEvents;
    private long lastSyncTime;
    private String peerServerId;
    private boolean peerConnected;

    public ServerStatusDTO() {
    }

    public ServerStatusDTO(String serverId, String host, int rmiPort, boolean online,
                           long uptimeMillis, int totalProducts, int pendingSyncEvents,
                           long lastSyncTime, String peerServerId, boolean peerConnected) {
        this.serverId = serverId;
        this.host = host;
        this.rmiPort = rmiPort;
        this.online = online;
        this.uptimeMillis = uptimeMillis;
        this.totalProducts = totalProducts;
        this.pendingSyncEvents = pendingSyncEvents;
        this.lastSyncTime = lastSyncTime;
        this.peerServerId = peerServerId;
        this.peerConnected = peerConnected;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getRmiPort() {
        return rmiPort;
    }

    public void setRmiPort(int rmiPort) {
        this.rmiPort = rmiPort;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public long getUptimeMillis() {
        return uptimeMillis;
    }

    public void setUptimeMillis(long uptimeMillis) {
        this.uptimeMillis = uptimeMillis;
    }

    public int getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(int totalProducts) {
        this.totalProducts = totalProducts;
    }

    public int getPendingSyncEvents() {
        return pendingSyncEvents;
    }

    public void setPendingSyncEvents(int pendingSyncEvents) {
        this.pendingSyncEvents = pendingSyncEvents;
    }

    public long getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(long lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public String getPeerServerId() {
        return peerServerId;
    }

    public void setPeerServerId(String peerServerId) {
        this.peerServerId = peerServerId;
    }

    public boolean isPeerConnected() {
        return peerConnected;
    }

    public void setPeerConnected(boolean peerConnected) {
        this.peerConnected = peerConnected;
    }

    @Override
    public String toString() {
        return "ServerStatusDTO{" +
                "serverId='" + serverId + '\'' +
                ", host='" + host + '\'' +
                ", rmiPort=" + rmiPort +
                ", online=" + online +
                ", totalProducts=" + totalProducts +
                ", pendingSyncEvents=" + pendingSyncEvents +
                ", peerConnected=" + peerConnected +
                '}';
    }
}
