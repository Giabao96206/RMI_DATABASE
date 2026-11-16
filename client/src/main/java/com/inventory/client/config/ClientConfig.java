package com.inventory.client.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(ClientConfig.class);

    private String server1Host;
    private int server1Port;
    private String server2Host;
    private int server2Port;
    private String preferredServer; // "SERVER_1" or "SERVER_2"
    private int connectionTimeoutMs;

    public static ClientConfig load(String path) {
        Properties props = new Properties();
        File file = new File(path);
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
                logger.info("Loaded client config from: {}", file.getAbsolutePath());
            } catch (IOException e) {
                logger.warn("Could not read client config file {}, using defaults", path);
            }
        } else {
            try (InputStream in = ClientConfig.class.getClassLoader().getResourceAsStream(path)) {
                if (in != null) {
                    props.load(in);
                    logger.info("Loaded client config from classpath: {}", path);
                }
            } catch (Exception ignored) {}
        }

        ClientConfig config = new ClientConfig();
        config.server1Host = getProperty(props, "server1.host", "127.0.0.1");
        config.server1Port = Integer.parseInt(getProperty(props, "server1.rmi.port", "1099"));
        config.server2Host = getProperty(props, "server2.host", "127.0.0.1");
        config.server2Port = Integer.parseInt(getProperty(props, "server2.rmi.port", "1100"));
        config.preferredServer = getProperty(props, "client.preferred.server", "SERVER_1");
        config.connectionTimeoutMs = Integer.parseInt(getProperty(props, "client.connection.timeout.ms", "3000"));

        return config;
    }

    private static String getProperty(Properties props, String key, String defaultValue) {
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp.trim();
        }
        return props.getProperty(key, defaultValue).trim();
    }

    public String getServer1Host() {
        return server1Host;
    }

    public int getServer1Port() {
        return server1Port;
    }

    public String getServer2Host() {
        return server2Host;
    }

    public int getServer2Port() {
        return server2Port;
    }

    public String getPreferredServer() {
        return preferredServer;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }
}
