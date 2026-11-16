package com.inventory.client.model;

import com.inventory.common.dto.ProductDTO;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ProductModel {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty productCode = new SimpleStringProperty();
    private final StringProperty productName = new SimpleStringProperty();
    private final StringProperty category = new SimpleStringProperty();
    private final IntegerProperty quantity = new SimpleIntegerProperty();
    private final DoubleProperty price = new SimpleDoubleProperty();
    private final IntegerProperty version = new SimpleIntegerProperty();
    private final BooleanProperty deleted = new SimpleBooleanProperty();
    private final StringProperty lastModifiedByServer = new SimpleStringProperty();
    private final StringProperty formattedUpdatedAt = new SimpleStringProperty();
    private final StringProperty formattedPrice = new SimpleStringProperty();

    private ProductDTO originalDto;

    public ProductModel(ProductDTO dto) {
        updateFromDto(dto);
    }

    public void updateFromDto(ProductDTO dto) {
        this.originalDto = dto;
        this.id.set(dto.getId());
        this.productCode.set(dto.getProductCode());
        this.productName.set(dto.getProductName());
        this.category.set(dto.getCategory());
        this.quantity.set(dto.getQuantity());
        this.price.set(dto.getPrice());
        this.version.set(dto.getVersion());
        this.deleted.set(dto.isDeleted());
        this.lastModifiedByServer.set(dto.getLastModifiedByServer());
        this.formattedUpdatedAt.set(DATE_FORMAT.format(new Date(dto.getUpdatedAt())));
        this.formattedPrice.set(String.format("%,.2f", dto.getPrice()));
    }

    public ProductDTO toDto() {
        ProductDTO dto = new ProductDTO();
        dto.setId(id.get());
        dto.setProductCode(productCode.get());
        dto.setProductName(productName.get());
        dto.setCategory(category.get());
        dto.setQuantity(quantity.get());
        dto.setPrice(price.get());
        dto.setVersion(version.get());
        dto.setDeleted(deleted.get());
        dto.setLastModifiedByServer(lastModifiedByServer.get());
        if (originalDto != null) {
            dto.setCreatedAt(originalDto.getCreatedAt());
            dto.setUpdatedAt(originalDto.getUpdatedAt());
        }
        return dto;
    }

    public String getId() { return id.get(); }
    public StringProperty idProperty() { return id; }

    public String getProductCode() { return productCode.get(); }
    public StringProperty productCodeProperty() { return productCode; }

    public String getProductName() { return productName.get(); }
    public StringProperty productNameProperty() { return productName; }

    public String getCategory() { return category.get(); }
    public StringProperty categoryProperty() { return category; }

    public int getQuantity() { return quantity.get(); }
    public IntegerProperty quantityProperty() { return quantity; }

    public double getPrice() { return price.get(); }
    public DoubleProperty priceProperty() { return price; }

    public int getVersion() { return version.get(); }
    public IntegerProperty versionProperty() { return version; }

    public boolean isDeleted() { return deleted.get(); }
    public BooleanProperty deletedProperty() { return deleted; }

    public String getLastModifiedByServer() { return lastModifiedByServer.get(); }
    public StringProperty lastModifiedByServerProperty() { return lastModifiedByServer; }

    public String getFormattedUpdatedAt() { return formattedUpdatedAt.get(); }
    public StringProperty formattedUpdatedAtProperty() { return formattedUpdatedAt; }

    public String getFormattedPrice() { return formattedPrice.get(); }
    public StringProperty formattedPriceProperty() { return formattedPrice; }

    public ProductDTO getOriginalDto() { return originalDto; }
}
