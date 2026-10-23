package com.example.minishop.service;

public class CustomerNotFound extends RuntimeException {
    public CustomerNotFound() {
        super("Customer not found");
    }
}