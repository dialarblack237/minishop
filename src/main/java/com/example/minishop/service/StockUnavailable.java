package com.example.minishop.service;

public class StockUnavailable extends ConflictException {

    private final long productId;

    public StockUnavailable(long productId) {
        super("Insufficient stock for product " + productId);
        this.productId = productId;
    }

    public long getProductId() {
        return productId;
    }
}