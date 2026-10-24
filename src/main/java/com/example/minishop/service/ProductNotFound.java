package com.example.minishop.service;

public class ProductNotFound extends RuntimeException {

    private final long id;

    public ProductNotFound(long id) {
        super("Product not found: " + id);
        this.id = id;
    }

    public long getId() {
        return id;
    }
}