package com.inventory.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ServerConfig {
    private static final Logger logger = LoggerFactory.getLogger(ServerConfig.class);

    private String serverId;
    private String serverHost;
    private int rmiRegistryPort;
    private int rmiExportPort;
    private String dbPath;
    private String peerServerId;
    private String peerHost;
    private int peerRmiRegistryPort;
    private int syncIntervalMs;

    public static ServerConfig load(String configFilePath) {
        Properties props = new Properties();
        File configFile = new File(configFilePath);
        if (configFile.exists()) {
            try (InputStream in = new FileInputStream(configFile)) {
                props.load(in);
                logger.info("Loaded configuration from external file: {}", configFile.getAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to read configuration file: {}, falling back to defaults", configFilePath, e);
            }
        } else {
            // Try loading from classpath
            try (InputStream in = ServerConfig.class.getClassLoader().getResourceAsStream(configFilePath)) {
                if (in != null) {
                    props.load(in);
                    logger.info("Loaded configuration from classpath resource: {}", configFilePath);
                } else {
                    logger.warn("Configuration file not found: {}, using built-in defaults", configFilePath);
                }
            } catch (IOException e) {
                logger.error("Error loading classpath config resource: {}", configFilePath, e);
            }
        }

        ServerConfig config = new ServerConfig();
        config.serverId = getProperty(props, "server.id", "SERVER_1");
        config.serverHost = getProperty(props, "server.host", "127.0.0.1");
        config.rmiRegistryPort = Integer.parseInt(getProperty(props, "server.rmi.registry.port", "1099"));
        config.rmiExportPort = Integer.parseInt(getProperty(props, "server.rmi.export.port", "10991"));
        config.dbPath = getProperty(props, "server.db.path", "data/inventory_" + config.serverId.toLowerCase() + ".db");

        config.peerServerId = getProperty(props, "peer.server.id", "SERVER_2");
        config.peerHost = getProperty(props, "peer.server.host", "127.0.0.1");
        config.peerRmiRegistryPort = Integer.parseInt(getProperty(props, "peer.server.rmi.registry.port", "1100"));
        config.syncIntervalMs = Integer.parseInt(getProperty(props, "server.sync.interval.ms", "2000"));

        return config;
    }

    private static String getProperty(Properties props, String key, String defaultValue) {
        // System property overrides file property
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp.trim();
        }
        return props.getProperty(key, defaultValue).trim();
    }

    public String getServerId() {
        return serverId;
    }

    public String getServerHost() {
        return serverHost;
    }

    public int getRmiRegistryPort() {
        return rmiRegistryPort;
    }

    public int getRmiExportPort() {
        return rmiExportPort;
    }

    public String getDbPath() {
        return dbPath;
    }

    public String getPeerServerId() {
        return peerServerId;
    }

    public String getPeerHost() {
        return peerHost;
    }

    public int getPeerRmiRegistryPort() {
        return peerRmiRegistryPort;
    }

    public int getSyncIntervalMs() {
        return syncIntervalMs;
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "serverId='" + serverId + '\'' +
                ", serverHost='" + serverHost + '\'' +
                ", rmiRegistryPort=" + rmiRegistryPort +
                ", rmiExportPort=" + rmiExportPort +
                ", dbPath='" + dbPath + '\'' +
                ", peerServerId='" + peerServerId + '\'' +
                ", peerHost='" + peerHost + '\'' +
                ", peerRmiRegistryPort=" + peerRmiRegistryPort +
                ", syncIntervalMs=" + syncIntervalMs +
                '}';
    }
}
