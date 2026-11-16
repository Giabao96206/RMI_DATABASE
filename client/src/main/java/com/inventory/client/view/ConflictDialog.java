package com.inventory.client.view;

import com.inventory.common.dto.ConflictDTO;
import com.inventory.common.dto.ProductDTO;
import com.inventory.common.util.JsonUtils;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ConflictDialog extends Dialog<ProductDTO> {

    private final ProductDTO localProduct;
    private final ProductDTO remoteProduct;

    private final TextField nameField = new TextField();
    private final TextField categoryField = new TextField();
    private final Spinner<Integer> quantitySpinner = new Spinner<>(0, 1000000, 1);
    private final TextField priceField = new TextField();

    public ConflictDialog(ConflictDTO conflict) {
        setTitle("Phát Hiện & Giải Quyết Xung Đột Dữ Liệu");
        setHeaderText("Xung đột xảy ra khi cả 2 Server cùng sửa đổi sản phẩm đồng thời.\nHãy chọn phiên bản bạn muốn giữ lại hoặc chỉnh sửa trực tiếp giá trị cuối cùng:");

        this.localProduct = JsonUtils.fromJson(conflict.getLocalDataJson(), ProductDTO.class);
        this.remoteProduct = JsonUtils.fromJson(conflict.getRemoteDataJson(), ProductDTO.class);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Label metaLabel = new Label("Mã Xung Đột: " + conflict.getConflictId() + 
                "\nThời điểm phát hiện: " + sdf.format(new Date(conflict.getDetectedAt())) +
                "\nLý do: " + conflict.getResolutionNote());
        metaLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #555555;");

        // Comparison Grid
        GridPane compareGrid = new GridPane();
        compareGrid.setHgap(15);
        compareGrid.setVgap(8);
        compareGrid.setStyle("-fx-background-color: #f7f9fc; -fx-padding: 12px; -fx-border-color: #dcdfe6; -fx-border-radius: 4px;");

        compareGrid.add(new Label("Thuộc Tính"), 0, 0);
        Label localHead = new Label("Dữ Liệu Server Hiện Tại (Local)");
        localHead.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Label remoteHead = new Label("Dữ Liệu Server Đối Tác (Remote)");
        remoteHead.setStyle("-fx-font-weight: bold; -fx-text-fill: #e67e22;");

        compareGrid.add(localHead, 1, 0);
        compareGrid.add(remoteHead, 2, 0);

        addRow(compareGrid, 1, "Mã SP", localProduct != null ? localProduct.getProductCode() : "N/A", remoteProduct != null ? remoteProduct.getProductCode() : "N/A");
        addRow(compareGrid, 2, "Tên SP", localProduct != null ? localProduct.getProductName() : "N/A", remoteProduct != null ? remoteProduct.getProductName() : "N/A");
        addRow(compareGrid, 3, "Danh Mục", localProduct != null ? localProduct.getCategory() : "N/A", remoteProduct != null ? remoteProduct.getCategory() : "N/A");
        addRow(compareGrid, 4, "Số Lượng", localProduct != null ? String.valueOf(localProduct.getQuantity()) : "N/A", remoteProduct != null ? String.valueOf(remoteProduct.getQuantity()) : "N/A");
        addRow(compareGrid, 5, "Đơn Giá", localProduct != null ? String.valueOf(localProduct.getPrice()) : "N/A", remoteProduct != null ? String.valueOf(remoteProduct.getPrice()) : "N/A");
        addRow(compareGrid, 6, "Phiên Bản", localProduct != null ? "v" + localProduct.getVersion() : "N/A", remoteProduct != null ? "v" + remoteProduct.getVersion() : "N/A");

        // Selection Radio Buttons
        ToggleGroup group = new ToggleGroup();
        RadioButton rbLocal = new RadioButton("Ưu tiên lấy toàn bộ dữ liệu Local");
        RadioButton rbRemote = new RadioButton("Ưu tiên lấy toàn bộ dữ liệu Remote");
        RadioButton rbCustom = new RadioButton("Tự tùy chỉnh thông tin hợp nhất bên dưới:");

        rbLocal.setToggleGroup(group);
        rbRemote.setToggleGroup(group);
        rbCustom.setToggleGroup(group);
        rbLocal.setSelected(true);

        quantitySpinner.setEditable(true);

        // Populate with local by default
        populateFields(localProduct != null ? localProduct : remoteProduct);

        rbLocal.setOnAction(e -> {
            if (localProduct != null) populateFields(localProduct);
        });
        rbRemote.setOnAction(e -> {
            if (remoteProduct != null) populateFields(remoteProduct);
        });

        // Edit Form
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);
        formGrid.add(new Label("Tên SP Giải Quyết:"), 0, 0);
        formGrid.add(nameField, 1, 0);
        formGrid.add(new Label("Danh Mục Giải Quyết:"), 0, 1);
        formGrid.add(categoryField, 1, 1);
        formGrid.add(new Label("Số Lượng Giải Quyết:"), 0, 2);
        formGrid.add(quantitySpinner, 1, 2);
        formGrid.add(new Label("Đơn Giá Giải Quyết:"), 0, 3);
        formGrid.add(priceField, 1, 3);

        root.getChildren().addAll(
                metaLabel,
                compareGrid,
                new Separator(),
                new HBox(15, rbLocal, rbRemote, rbCustom),
                formGrid
        );

        getDialogPane().setContent(root);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                ProductDTO base = (localProduct != null) ? localProduct : remoteProduct;
                ProductDTO resolved = new ProductDTO();
                resolved.setId(base.getId());
                resolved.setProductCode(base.getProductCode());
                resolved.setProductName(nameField.getText().trim());
                resolved.setCategory(categoryField.getText().trim());
                resolved.setQuantity(quantitySpinner.getValue());
                try {
                    resolved.setPrice(Double.parseDouble(priceField.getText().trim()));
                } catch (Exception e) {
                    resolved.setPrice(base.getPrice());
                }
                resolved.setCreatedAt(base.getCreatedAt());
                resolved.setUpdatedAt(System.currentTimeMillis());
                resolved.setVersion(Math.max(
                        localProduct != null ? localProduct.getVersion() : 1,
                        remoteProduct != null ? remoteProduct.getVersion() : 1
                ));
                resolved.setDeleted(false);
                return resolved;
            }
            return null;
        });
    }

    private void addRow(GridPane grid, int row, String prop, String val1, String val2) {
        grid.add(new Label(prop + ":"), 0, row);
        Label l1 = new Label(val1);
        Label l2 = new Label(val2);
        if (!val1.equals(val2)) {
            l1.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
            l2.setStyle("-fx-text-fill: #d35400; -fx-font-weight: bold;");
        }
        grid.add(l1, 1, row);
        grid.add(l2, 2, row);
    }

    private void populateFields(ProductDTO p) {
        if (p == null) return;
        nameField.setText(p.getProductName());
        categoryField.setText(p.getCategory());
        quantitySpinner.getValueFactory().setValue(p.getQuantity());
        priceField.setText(String.valueOf(p.getPrice()));
    }
}
