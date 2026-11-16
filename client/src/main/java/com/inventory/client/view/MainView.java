package com.inventory.client.view;

import com.inventory.client.model.ProductModel;
import com.inventory.client.service.RmiClientService;
import com.inventory.common.dto.ConflictDTO;
import com.inventory.common.dto.InventoryStatsDTO;
import com.inventory.common.dto.ProductDTO;
import com.inventory.common.dto.ServerStatusDTO;
import com.inventory.common.dto.SyncEventDTO;
import com.inventory.common.exception.InventoryException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MainView extends BorderPane {
    private static final Logger logger = LoggerFactory.getLogger(MainView.class);
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final RmiClientService clientService;

    // Table Models
    private final ObservableList<ProductModel> productList = FXCollections.observableArrayList();
    private final TableView<ProductModel> productTable = new TableView<>(productList);

    private final ObservableList<SyncEventDTO> syncHistoryList = FXCollections.observableArrayList();
    private final TableView<SyncEventDTO> syncHistoryTable = new TableView<>(syncHistoryList);

    private final ObservableList<ConflictDTO> conflictList = FXCollections.observableArrayList();
    private final TableView<ConflictDTO> conflictTable = new TableView<>(conflictList);

    // UI Controls
    private final Label activeServerBadge = new Label();
    private final Button switchServerBtn = new Button();
    private final TextField searchField = new TextField();
    private final Label statusMessage = new Label("Sẵn sàng");
    private final TextArea failoverLogArea = new TextArea();

    // Stats Labels
    private final Label totalProductsLabel = new Label("0");
    private final Label totalQuantityLabel = new Label("0");
    private final Label totalValueLabel = new Label("0 đ");

    // Server Cards
    private final Label s1StatusBadge = new Label("Đang kiểm tra...");
    private final Label s1AddressLabel = new Label();
    private final Label s1ProductsLabel = new Label("0");
    private final Label s1PendingLabel = new Label("0");
    private final Label s1PeerLinkLabel = new Label("Không xác định");

    private final Label s2StatusBadge = new Label("Đang kiểm tra...");
    private final Label s2AddressLabel = new Label();
    private final Label s2ProductsLabel = new Label("0");
    private final Label s2PendingLabel = new Label("0");
    private final Label s2PeerLinkLabel = new Label("Không xác định");

    public MainView(RmiClientService clientService) {
        this.clientService = clientService;

        // Header
        setTop(buildHeader());

        // Center: TabPane
        TabPane tabPane = new TabPane();
        tabPane.getTabs().addAll(
                buildProductTab(),
                buildMonitorTab(),
                buildSyncTab()
        );
        setCenter(tabPane);

        // Bottom: Status Bar
        setBottom(buildStatusBar());

        // Register client service listeners
        clientService.addFailoverListener(msg -> Platform.runLater(() -> {
            logFailover(msg);
            updateHeaderBadges();
            refreshAllAsync();
        }));

        clientService.addStatusChangeListener(() -> Platform.runLater(() -> {
            updateHeaderBadges();
            updateServerCards();
        }));

        // Initial Data Load
        updateHeaderBadges();
        refreshAllAsync();
    }

    private Node buildHeader() {
        HBox header = new HBox(20);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(3);
        Label title = new Label("QUẢN LÝ KHO PHÂN TÁN (JAVA RMI)");
        title.getStyleClass().add("header-title");
        Label subtitle = new Label("Kiến trúc phân tán 2 Server Active-Active | Tự động đồng bộ & Failover chịu lỗi");
        subtitle.getStyleClass().add("header-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        activeServerBadge.getStyleClass().addAll("badge-online");
        activeServerBadge.setStyle("-fx-font-size: 13px; -fx-padding: 6px 14px;");

        switchServerBtn.getStyleClass().add("btn-secondary");
        switchServerBtn.setOnAction(e -> handleManualSwitch());

        header.getChildren().addAll(titleBox, spacer, activeServerBadge, switchServerBtn);
        return header;
    }

    private Tab buildProductTab() {
        Tab tab = new Tab("📦 Quản Lý Kho Hàng");
        tab.setClosable(false);

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));

        // Toolbar
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("Tìm kiếm theo mã, tên sản phẩm hoặc danh mục...");
        searchField.setPrefWidth(320);
        searchField.setOnAction(e -> handleSearch());

        Button searchBtn = new Button("🔍 Tìm Kiếm");
        searchBtn.getStyleClass().add("btn-secondary");
        searchBtn.setOnAction(e -> handleSearch());

        Button clearBtn = new Button("✖ Xóa Lọc");
        clearBtn.getStyleClass().add("btn-secondary");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            refreshProductsAsync();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ Thêm Mới");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> handleAddProduct());

        Button editBtn = new Button("✏ Chỉnh Sửa");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setOnAction(e -> handleEditProduct());

        Button deleteBtn = new Button("🗑 Xóa");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> handleDeleteProduct());

        Button refreshBtn = new Button("🔄 Làm Mới");
        refreshBtn.getStyleClass().add("btn-success");
        refreshBtn.setOnAction(e -> refreshAllAsync());

        toolbar.getChildren().addAll(searchField, searchBtn, clearBtn, spacer, addBtn, editBtn, deleteBtn, refreshBtn);

        // Product Table
        setupProductTable();
        VBox.setVgrow(productTable, Priority.ALWAYS);

        // Stats Strip
        HBox statsStrip = new HBox(20);
        statsStrip.setAlignment(Pos.CENTER_LEFT);
        statsStrip.getChildren().addAll(
                createMetricCard("Tổng Mặt Hàng:", totalProductsLabel),
                createMetricCard("Tổng Tồn Kho:", totalQuantityLabel),
                createMetricCard("Tổng Giá Trị Kho:", totalValueLabel)
        );

        root.getChildren().addAll(toolbar, productTable, statsStrip);
        tab.setContent(root);
        return tab;
    }

    private void setupProductTable() {
        TableColumn<ProductModel, String> colCode = new TableColumn<>("Mã Sản Phẩm");
        colCode.setCellValueFactory(cell -> cell.getValue().productCodeProperty());
        colCode.setPrefWidth(130);

        TableColumn<ProductModel, String> colName = new TableColumn<>("Tên Sản Phẩm");
        colName.setCellValueFactory(cell -> cell.getValue().productNameProperty());
        colName.setPrefWidth(240);

        TableColumn<ProductModel, String> colCategory = new TableColumn<>("Danh Mục");
        colCategory.setCellValueFactory(cell -> cell.getValue().categoryProperty());
        colCategory.setPrefWidth(140);

        TableColumn<ProductModel, Number> colQty = new TableColumn<>("Số Lượng");
        colQty.setCellValueFactory(cell -> cell.getValue().quantityProperty());
        colQty.setPrefWidth(100);

        TableColumn<ProductModel, String> colPrice = new TableColumn<>("Đơn Giá (VNĐ)");
        colPrice.setCellValueFactory(cell -> cell.getValue().formattedPriceProperty());
        colPrice.setPrefWidth(140);

        TableColumn<ProductModel, Number> colVer = new TableColumn<>("Phiên Bản");
        colVer.setCellValueFactory(cell -> cell.getValue().versionProperty());
        colVer.setPrefWidth(90);

        TableColumn<ProductModel, String> colServer = new TableColumn<>("Server Sửa Cuối");
        colServer.setCellValueFactory(cell -> cell.getValue().lastModifiedByServerProperty());
        colServer.setPrefWidth(130);

        TableColumn<ProductModel, String> colUpdated = new TableColumn<>("Cập Nhật Lúc");
        colUpdated.setCellValueFactory(cell -> cell.getValue().formattedUpdatedAtProperty());
        colUpdated.setPrefWidth(160);

        productTable.getColumns().addAll(List.of(colCode, colName, colCategory, colQty, colPrice, colVer, colServer, colUpdated));
        productTable.setPlaceholder(new Label("Không có sản phẩm nào trong kho"));
    }

    private HBox createMetricCard(String label, Label valueLabel) {
        HBox box = new HBox(8);
        box.getStyleClass().add("metric-pill");
        box.setAlignment(Pos.CENTER_LEFT);
        Label l = new Label(label);
        l.getStyleClass().add("metric-label");
        valueLabel.getStyleClass().add("metric-value");
        box.getChildren().addAll(l, valueLabel);
        return box;
    }

    private Tab buildMonitorTab() {
        Tab tab = new Tab("🖥 Giám Sát Cụm Server (Failover)");
        tab.setClosable(false);

        VBox root = new VBox(16);
        root.setPadding(new Insets(16));

        // 2 Cards Side by side
        HBox cardsBox = new HBox(20);
        cardsBox.getChildren().addAll(
                buildServerCard("SERVER 1", s1StatusBadge, s1AddressLabel, s1ProductsLabel, s1PendingLabel, s1PeerLinkLabel),
                buildServerCard("SERVER 2", s2StatusBadge, s2AddressLabel, s2ProductsLabel, s2PendingLabel, s2PeerLinkLabel)
        );
        HBox.setHgrow(cardsBox.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(cardsBox.getChildren().get(1), Priority.ALWAYS);

        // Failover & Event Log
        Label logTitle = new Label("Nhật Ký Chuyển Đổi Dự Phòng (Failover) & Kết Nối Mạng:");
        logTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");

        failoverLogArea.setEditable(false);
        failoverLogArea.setWrapText(true);
        failoverLogArea.setPrefRowCount(10);
        failoverLogArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(failoverLogArea, Priority.ALWAYS);

        Button pingBtn = new Button("⚡ Kiểm Tra Kết Nối Cả 2 Server");
        pingBtn.getStyleClass().add("btn-primary");
        pingBtn.setOnAction(e -> {
            CompletableFuture.runAsync(clientService::connectBoth)
                    .thenRun(() -> Platform.runLater(() -> {
                        updateServerCards();
                        updateHeaderBadges();
                        logFailover("Đã làm mới trạng thái kiểm tra kết nối cả 2 Server.");
                    }));
        });

        root.getChildren().addAll(cardsBox, pingBtn, logTitle, failoverLogArea);
        tab.setContent(root);
        return tab;
    }

    private VBox buildServerCard(String name, Label statusBadge, Label addressLabel, Label prodLabel, Label pendingLabel, Label peerLabel) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        HBox head = new HBox(10);
        head.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(name);
        title.getStyleClass().add("card-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        head.getChildren().addAll(title, sp, statusBadge);

        GridPane info = new GridPane();
        info.setHgap(12);
        info.setVgap(8);
        info.add(new Label("Địa chỉ RMI:"), 0, 0);
        info.add(addressLabel, 1, 0);
        info.add(new Label("Sản phẩm tại DB:"), 0, 1);
        info.add(prodLabel, 1, 1);
        info.add(new Label("Event chờ đồng bộ:"), 0, 2);
        info.add(pendingLabel, 1, 2);
        info.add(new Label("Liên kết tới Peer:"), 0, 3);
        info.add(peerLabel, 1, 3);

        card.getChildren().addAll(head, new Separator(), info);
        return card;
    }

    private Tab buildSyncTab() {
        Tab tab = new Tab("🔄 Lịch Sử Đồng Bộ & Xung Đột");
        tab.setClosable(false);

        VBox root = new VBox(14);
        root.setPadding(new Insets(16));

        Label syncTitle = new Label("Nhật Ký Các Sự Kiện Đồng Bộ (Synchronization Events):");
        syncTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        setupSyncTable();
        VBox.setVgrow(syncHistoryTable, Priority.ALWAYS);

        Label conflictTitle = new Label("Danh Sách Xung Đột Dữ Liệu Cần Quản Trị Viên Xử Lý:");
        conflictTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #b91c1c;");

        setupConflictTable();
        conflictTable.setPrefHeight(180);

        HBox conflictBar = new HBox(10);
        Button resolveBtn = new Button("⚙ Giải Quyết Xung Đột Được Chọn");
        resolveBtn.getStyleClass().add("btn-primary");
        resolveBtn.setOnAction(e -> handleResolveConflict());
        conflictBar.getChildren().add(resolveBtn);

        root.getChildren().addAll(syncTitle, syncHistoryTable, conflictTitle, conflictTable, conflictBar);
        tab.setContent(root);
        return tab;
    }

    private void setupSyncTable() {
        TableColumn<SyncEventDTO, String> colId = new TableColumn<>("Mã Event");
        colId.setCellValueFactory(new PropertyValueFactory<>("eventId"));
        colId.setPrefWidth(260);

        TableColumn<SyncEventDTO, String> colType = new TableColumn<>("Loại Thao Tác");
        colType.setCellValueFactory(new PropertyValueFactory<>("eventType"));
        colType.setPrefWidth(110);

        TableColumn<SyncEventDTO, String> colOrigin = new TableColumn<>("Server Nguồn");
        colOrigin.setCellValueFactory(new PropertyValueFactory<>("originServerId"));
        colOrigin.setPrefWidth(110);

        TableColumn<SyncEventDTO, String> colTarget = new TableColumn<>("Server Đích");
        colTarget.setCellValueFactory(new PropertyValueFactory<>("targetServerId"));
        colTarget.setPrefWidth(110);

        TableColumn<SyncEventDTO, String> colTime = new TableColumn<>("Thời Gian");
        colTime.setCellValueFactory(cell -> {
            long ts = cell.getValue().getTimestamp();
            return new javafx.beans.property.SimpleStringProperty(SDF.format(new Date(ts)));
        });
        colTime.setPrefWidth(160);

        TableColumn<SyncEventDTO, String> colStatus = new TableColumn<>("Trạng Thái");
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setPrefWidth(120);

        syncHistoryTable.getColumns().addAll(List.of(colId, colType, colOrigin, colTarget, colTime, colStatus));
    }

    private void setupConflictTable() {
        TableColumn<ConflictDTO, String> colId = new TableColumn<>("Mã Xung Đột");
        colId.setCellValueFactory(new PropertyValueFactory<>("conflictId"));
        colId.setPrefWidth(260);

        TableColumn<ConflictDTO, String> colEntity = new TableColumn<>("Mã Thực Thể (Product ID)");
        colEntity.setCellValueFactory(new PropertyValueFactory<>("entityId"));
        colEntity.setPrefWidth(260);

        TableColumn<ConflictDTO, String> colTime = new TableColumn<>("Thời Điểm Phát Hiện");
        colTime.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(SDF.format(new Date(cell.getValue().getDetectedAt()))));
        colTime.setPrefWidth(160);

        TableColumn<ConflictDTO, String> colNote = new TableColumn<>("Ghi Chú Xung Đột");
        colNote.setCellValueFactory(new PropertyValueFactory<>("resolutionNote"));
        colNote.setPrefWidth(280);

        conflictTable.getColumns().addAll(List.of(colId, colEntity, colTime, colNote));
        conflictTable.setPlaceholder(new Label("Không có xung đột dữ liệu nào chưa xử lý. Hệ thống nhất quán."));
    }

    private Node buildStatusBar() {
        HBox bar = new HBox(20);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label autoLabel = new Label("Cơ chế Failover: BẬT | Tự động chuyển Server khi có sự cố");
        bar.getChildren().addAll(statusMessage, spacer, autoLabel);
        return bar;
    }

    // Handlers
    private void handleAddProduct() {
        ProductDialog dialog = new ProductDialog(null);
        Optional<ProductDTO> result = dialog.showAndWait();
        result.ifPresent(product -> {
            setStatus("Đang lưu sản phẩm mới...");
            CompletableFuture.runAsync(() -> {
                try {
                    clientService.addProduct(product);
                    Platform.runLater(() -> {
                        setStatus("Thêm sản phẩm thành công!");
                        refreshAllAsync();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Lỗi Thêm Sản Phẩm", e.getMessage()));
                }
            });
        });
    }

    private void handleEditProduct() {
        ProductModel selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Chưa Chọn Sản Phẩm", "Vui lòng chọn một sản phẩm từ danh sách để sửa.");
            return;
        }

        ProductDialog dialog = new ProductDialog(selected.toDto());
        Optional<ProductDTO> result = dialog.showAndWait();
        result.ifPresent(updatedProduct -> {
            setStatus("Đang lưu cập nhật sản phẩm...");
            CompletableFuture.runAsync(() -> {
                try {
                    clientService.updateProduct(updatedProduct);
                    Platform.runLater(() -> {
                        setStatus("Cập nhật sản phẩm thành công!");
                        refreshAllAsync();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Lỗi Cập Nhật", e.getMessage()));
                }
            });
        });
    }

    private void handleDeleteProduct() {
        ProductModel selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Chưa Chọn Sản Phẩm", "Vui lòng chọn một sản phẩm từ danh sách để xóa.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác Nhận Xóa Sản Phẩm");
        alert.setHeaderText("Bạn có chắc chắn muốn xóa sản phẩm sau?");
        alert.setContentText(selected.getProductCode() + " - " + selected.getProductName());

        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            setStatus("Đang xóa sản phẩm...");
            CompletableFuture.runAsync(() -> {
                try {
                    clientService.deleteProduct(selected.getId());
                    Platform.runLater(() -> {
                        setStatus("Xóa sản phẩm thành công!");
                        refreshAllAsync();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Lỗi Xóa", e.getMessage()));
                }
            });
        }
    }

    private void handleSearch() {
        String keyword = searchField.getText().trim();
        setStatus("Đang tìm kiếm...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return clientService.searchProducts(keyword);
            } catch (Exception e) {
                logger.error("Search failed", e);
                return List.<ProductDTO>of();
            }
        }).thenAccept(results -> Platform.runLater(() -> {
            productList.clear();
            for (ProductDTO p : results) {
                productList.add(new ProductModel(p));
            }
            setStatus("Tìm thấy " + results.size() + " sản phẩm.");
        }));
    }

    private void handleManualSwitch() {
        String current = clientService.getActiveServerId();
        String target = "SERVER_1".equals(current) ? "SERVER_2" : "SERVER_1";
        clientService.switchServer(target, "Người dùng chuyển đổi thủ công trên giao diện");
        logFailover("Chuyển server kết nối thủ công sang " + target);
        updateHeaderBadges();
        refreshAllAsync();
    }

    private void handleResolveConflict() {
        ConflictDTO selected = conflictTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Chưa Chọn Xung Đột", "Vui lòng chọn một bản ghi xung đột từ danh sách để xử lý.");
            return;
        }

        ConflictDialog dialog = new ConflictDialog(selected);
        Optional<ProductDTO> result = dialog.showAndWait();
        result.ifPresent(resolved -> {
            setStatus("Đang áp dụng giải pháp giải quyết xung đột...");
            CompletableFuture.runAsync(() -> {
                try {
                    clientService.resolveConflict(selected.getConflictId(), resolved);
                    Platform.runLater(() -> {
                        setStatus("Đã giải quyết xung đột thành công!");
                        refreshAllAsync();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showError("Lỗi Giải Quyết Xung Đột", e.getMessage()));
                }
            });
        });
    }

    // Refreshers
    public void refreshAllAsync() {
        refreshProductsAsync();
        refreshStatsAsync();
        refreshSyncHistoryAsync();
        refreshConflictsAsync();
        updateServerCards();
    }

    private void refreshProductsAsync() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return clientService.getAllProducts();
            } catch (Exception e) {
                logger.error("Failed to load products: {}", e.getMessage());
                return List.<ProductDTO>of();
            }
        }).thenAccept(list -> Platform.runLater(() -> {
            productList.clear();
            for (ProductDTO p : list) {
                productList.add(new ProductModel(p));
            }
        }));
    }

    private void refreshStatsAsync() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return clientService.getInventoryStatistics();
            } catch (Exception e) {
                return new InventoryStatsDTO(0, 0, 0.0, 0, 0);
            }
        }).thenAccept(stats -> Platform.runLater(() -> {
            totalProductsLabel.setText(String.valueOf(stats.getTotalProducts()));
            totalQuantityLabel.setText(String.format("%,d", stats.getTotalStockQuantity()));
            totalValueLabel.setText(String.format("%,.0f đ", stats.getTotalInventoryValue()));
        }));
    }

    private void refreshSyncHistoryAsync() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return clientService.getSyncHistory(50);
            } catch (Exception e) {
                return List.<SyncEventDTO>of();
            }
        }).thenAccept(list -> Platform.runLater(() -> {
            syncHistoryList.clear();
            syncHistoryList.addAll(list);
        }));
    }

    private void refreshConflictsAsync() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return clientService.getConflicts();
            } catch (Exception e) {
                return List.<ConflictDTO>of();
            }
        }).thenAccept(list -> Platform.runLater(() -> {
            conflictList.clear();
            conflictList.addAll(list);
        }));
    }

    private void updateHeaderBadges() {
        String active = clientService.getActiveServerId();
        boolean isS1 = "SERVER_1".equals(active);
        boolean isOnline = isS1 ? clientService.isServer1Online() : clientService.isServer2Online();

        activeServerBadge.setText("KẾT NỐI HIỆN TẠI: " + active + (isOnline ? " (ONLINE)" : " (OFFLINE)"));
        activeServerBadge.getStyleClass().removeAll("badge-online", "badge-offline");
        activeServerBadge.getStyleClass().add(isOnline ? "badge-online" : "badge-offline");

        String alt = isS1 ? "SERVER_2" : "SERVER_1";
        switchServerBtn.setText("Chuyển Sang " + alt);
    }

    private void updateServerCards() {
        CompletableFuture.supplyAsync(() -> {
            ServerStatusDTO s1 = clientService.getServer1StatusDirect();
            ServerStatusDTO s2 = clientService.getServer2StatusDirect();
            return new ServerStatusDTO[]{s1, s2};
        }).thenAccept(statuses -> Platform.runLater(() -> {
            ServerStatusDTO s1 = statuses[0];
            ServerStatusDTO s2 = statuses[1];

            // Update S1
            s1AddressLabel.setText(s1.getHost() + ":" + s1.getRmiPort());
            s1ProductsLabel.setText(String.valueOf(s1.getTotalProducts()));
            s1PendingLabel.setText(String.valueOf(s1.getPendingSyncEvents()));
            s1PeerLinkLabel.setText(s1.isPeerConnected() ? "ĐÃ KẾT NỐI (Sync Active)" : "MẤT KẾT NỐI (Offline)");
            s1PeerLinkLabel.setStyle(s1.isPeerConnected() ? "-fx-text-fill: #10b981; -fx-font-weight: bold;" : "-fx-text-fill: #ef4444; -fx-font-weight: bold;");
            s1StatusBadge.setText(s1.isOnline() ? "ONLINE" : "OFFLINE");
            s1StatusBadge.getStyleClass().removeAll("badge-online", "badge-offline");
            s1StatusBadge.getStyleClass().add(s1.isOnline() ? "badge-online" : "badge-offline");

            // Update S2
            s2AddressLabel.setText(s2.getHost() + ":" + s2.getRmiPort());
            s2ProductsLabel.setText(String.valueOf(s2.getTotalProducts()));
            s2PendingLabel.setText(String.valueOf(s2.getPendingSyncEvents()));
            s2PeerLinkLabel.setText(s2.isPeerConnected() ? "ĐÃ KẾT NỐI (Sync Active)" : "MẤT KẾT NỐI (Offline)");
            s2PeerLinkLabel.setStyle(s2.isPeerConnected() ? "-fx-text-fill: #10b981; -fx-font-weight: bold;" : "-fx-text-fill: #ef4444; -fx-font-weight: bold;");
            s2StatusBadge.setText(s2.isOnline() ? "ONLINE" : "OFFLINE");
            s2StatusBadge.getStyleClass().removeAll("badge-online", "badge-offline");
            s2StatusBadge.getStyleClass().add(s2.isOnline() ? "badge-online" : "badge-offline");
        }));
    }

    private void logFailover(String message) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        failoverLogArea.appendText("[" + time + "] " + message + "\n");
    }

    private void setStatus(String message) {
        statusMessage.setText(message);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
