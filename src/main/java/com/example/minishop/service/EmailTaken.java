package com.example.minishop.service;

public class EmailTaken extends ConflictException {

    public EmailTaken(String email) {
        super("Email already registered: " + email);
    }
}