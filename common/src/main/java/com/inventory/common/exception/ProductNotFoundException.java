package com.inventory.common.exception;

public class ProductNotFoundException extends InventoryException {
    private static final long serialVersionUID = 1L;

    public ProductNotFoundException(String productId) {
        super("Product not found with id: " + productId);
    }
}
