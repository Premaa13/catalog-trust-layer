package com.catalogtrust.service;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String productId) {
        super("product_id not found: " + productId);
    }
}
