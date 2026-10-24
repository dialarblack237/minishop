package com.example.minishop.service;

public abstract class NotFoundException extends AppException {

    protected NotFoundException(String message) {
        super(message);
    }
}