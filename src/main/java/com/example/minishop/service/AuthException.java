package com.example.minishop.service;

public abstract class AuthException extends AppException {

    protected AuthException(String message) {
        super(message);
    }
}