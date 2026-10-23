package com.example.minishop.service;

public class EmailTaken extends RuntimeException {
    public EmailTaken(String email) {
        super("Email already registered: " + email);
    }
}