package com.inventory.common.dto;

import java.io.Serializable;
import java.util.Objects;

public class ProductDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String productCode;
    private String productName;
    private String category;
    private int quantity;
    private double price;
    private long createdAt;
    private long updatedAt;
    private int version;
    private boolean deleted;
    private String lastModifiedByServer;

    public ProductDTO() {
    }

    public ProductDTO(String id, String productCode, String productName, String category,
                      int quantity, double price, long createdAt, long updatedAt,
                      int version, boolean deleted, String lastModifiedByServer) {
        this.id = id;
        this.productCode = productCode;
        this.productName = productName;
        this.category = category;
        this.quantity = quantity;
        this.price = price;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.deleted = deleted;
        this.lastModifiedByServer = lastModifiedByServer;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getLastModifiedByServer() {
        return lastModifiedByServer;
    }

    public void setLastModifiedByServer(String lastModifiedByServer) {
        this.lastModifiedByServer = lastModifiedByServer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductDTO that = (ProductDTO) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProductDTO{" +
                "id='" + id + '\'' +
                ", productCode='" + productCode + '\'' +
                ", productName='" + productName + '\'' +
                ", category='" + category + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", version=" + version +
                ", deleted=" + deleted +
                ", lastModifiedByServer='" + lastModifiedByServer + '\'' +
                '}';
    }
}
