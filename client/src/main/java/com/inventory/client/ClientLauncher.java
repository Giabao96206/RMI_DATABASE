package com.inventory.client;

import javafx.application.Application;

/**
 * Standard launcher class without extending javafx.application.Application.
 * This is required for creating runnable shaded fat jars in JavaFX 11+ without module-path errors.
 */
public class ClientLauncher {
    public static void main(String[] args) {
        Application.launch(ClientApp.class, args);
    }
}
