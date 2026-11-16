package com.inventory.common.dto;

import java.io.Serializable;

public class InventoryStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int totalProducts;
    private int totalStockQuantity;
    private double totalInventoryValue;
    private int lowStockCount;
    private int categoryCount;

    public InventoryStatsDTO() {
    }

    public InventoryStatsDTO(int totalProducts, int totalStockQuantity, double totalInventoryValue,
                             int lowStockCount, int categoryCount) {
        this.totalProducts = totalProducts;
        this.totalStockQuantity = totalStockQuantity;
        this.totalInventoryValue = totalInventoryValue;
        this.lowStockCount = lowStockCount;
        this.categoryCount = categoryCount;
    }

    public int getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(int totalProducts) {
        this.totalProducts = totalProducts;
    }

    public int getTotalStockQuantity() {
        return totalStockQuantity;
    }

    public void setTotalStockQuantity(int totalStockQuantity) {
        this.totalStockQuantity = totalStockQuantity;
    }

    public double getTotalInventoryValue() {
        return totalInventoryValue;
    }

    public void setTotalInventoryValue(double totalInventoryValue) {
        this.totalInventoryValue = totalInventoryValue;
    }

    public int getLowStockCount() {
        return lowStockCount;
    }

    public void setLowStockCount(int lowStockCount) {
        this.lowStockCount = lowStockCount;
    }

    public int getCategoryCount() {
        return categoryCount;
    }

    public void setCategoryCount(int categoryCount) {
        this.categoryCount = categoryCount;
    }

    @Override
    public String toString() {
        return "InventoryStatsDTO{" +
                "totalProducts=" + totalProducts +
                ", totalStockQuantity=" + totalStockQuantity +
                ", totalInventoryValue=" + totalInventoryValue +
                ", lowStockCount=" + lowStockCount +
                ", categoryCount=" + categoryCount +
                '}';
    }
}
