package com.example.minishop.service;

public abstract class ConflictException extends AppException {

    protected ConflictException(String message) {
        super(message);
    }
}