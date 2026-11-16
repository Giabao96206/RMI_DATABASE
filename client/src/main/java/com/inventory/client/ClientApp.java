package com.inventory.client;

import com.inventory.client.config.ClientConfig;
import com.inventory.client.service.RmiClientService;
import com.inventory.client.view.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(ClientApp.class);

    private RmiClientService clientService;

    @Override
    public void start(Stage primaryStage) {
        try {
            // Determine config path from application parameters
            Parameters params = getParameters();
            String configPath = (params.getRaw().isEmpty()) ? "client.properties" : params.getRaw().get(0);
            ClientConfig config = ClientConfig.load(configPath);

            logger.info("Starting Distributed Warehouse Management Client...");
            this.clientService = new RmiClientService(config);
            this.clientService.start();

            MainView mainView = new MainView(clientService);
            Scene scene = new Scene(mainView, 1150, 720);

            // Load CSS
            try {
                String css = getClass().getResource("/css/style.css").toExternalForm();
                scene.getStylesheets().add(css);
            } catch (Exception e) {
                logger.warn("Could not load CSS stylesheet: {}", e.getMessage());
            }

            primaryStage.setTitle("Hệ Thống Quản Lý Kho Phân Tán (Java RMI 2-Server Sync)");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(950);
            primaryStage.setMinHeight(600);

            primaryStage.setOnCloseRequest(e -> {
                logger.info("Closing application window...");
                if (clientService != null) {
                    clientService.stop();
                }
            });

            primaryStage.show();
            logger.info("Application window displayed successfully.");

        } catch (Exception e) {
            logger.error("Failed to start Client application: {}", e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        if (clientService != null) {
            clientService.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
