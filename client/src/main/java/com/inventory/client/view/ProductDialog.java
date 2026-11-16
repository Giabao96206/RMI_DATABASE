package com.inventory.client.view;

import com.inventory.common.dto.ProductDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class ProductDialog extends Dialog<ProductDTO> {

    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final ComboBox<String> categoryCombo = new ComboBox<>();
    private final Spinner<Integer> quantitySpinner = new Spinner<>(0, 1000000, 1);
    private final TextField priceField = new TextField();
    private final ProductDTO originalProduct;

    public ProductDialog(ProductDTO product) {
        this.originalProduct = product;
        boolean isEdit = (product != null);

        setTitle(isEdit ? "Chỉnh Sửa Thông Tin Sản Phẩm" : "Thêm Sản Phẩm Mới Vào Kho");
        setHeaderText(isEdit ? "Cập nhật thông tin cho mã: " + product.getProductCode() : "Nhập đầy đủ thông tin sản phẩm mới:");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 25, 20, 25));

        categoryCombo.setEditable(true);
        categoryCombo.getItems().addAll("Điện Tử", "Linh Kiện", "Gia Dụng", "Phụ Kiện", "Văn Phòng Phẩm", "Thời Trang");
        categoryCombo.setValue("Điện Tử");

        quantitySpinner.setEditable(true);

        codeField.setPromptText("VD: SP-001");
        nameField.setPromptText("VD: Laptop Lenovo ThinkPad");
        priceField.setPromptText("VD: 15500000");

        if (isEdit) {
            codeField.setText(product.getProductCode());
            codeField.setDisable(true); // Don't change product code in edit mode
            nameField.setText(product.getProductName());
            categoryCombo.setValue(product.getCategory());
            quantitySpinner.getValueFactory().setValue(product.getQuantity());
            priceField.setText(String.valueOf(product.getPrice()));
        }

        grid.add(new Label("Mã Sản Phẩm (*):"), 0, 0);
        grid.add(codeField, 1, 0);

        grid.add(new Label("Tên Sản Phẩm (*):"), 0, 1);
        grid.add(nameField, 1, 1);

        grid.add(new Label("Danh Mục (*):"), 0, 2);
        grid.add(categoryCombo, 1, 2);

        grid.add(new Label("Số Lượng Tồn Kho:"), 0, 3);
        grid.add(quantitySpinner, 1, 3);

        grid.add(new Label("Đơn Giá (VNĐ):"), 0, 4);
        grid.add(priceField, 1, 4);

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText(isEdit ? "Lưu Thay Đổi" : "Tạo Mới");

        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String code = codeField.getText().trim();
                String name = nameField.getText().trim();
                String category = categoryCombo.getValue();
                String priceStr = priceField.getText().trim();

                if (code.isEmpty() || name.isEmpty() || category == null || category.trim().isEmpty()) {
                    showError("Dữ liệu không hợp lệ", "Mã sản phẩm, tên và danh mục không được để trống!");
                    return null;
                }

                double price;
                try {
                    price = Double.parseDouble(priceStr);
                    if (price < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    showError("Đơn giá không hợp lệ", "Đơn giá phải là số dương lớn hơn hoặc bằng 0!");
                    return null;
                }

                ProductDTO dto = (originalProduct != null) ? originalProduct : new ProductDTO();
                dto.setProductCode(code);
                dto.setProductName(name);
                dto.setCategory(category.trim());
                dto.setQuantity(quantitySpinner.getValue());
                dto.setPrice(price);

                return dto;
            }
            return null;
        });
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
