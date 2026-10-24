package com.example.minishop.service;

public class CustomerNotFound extends NotFoundException {

    public CustomerNotFound() {
        super("Customer not found");
    }
}