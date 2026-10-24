package com.example.minishop.service;

public class Forbidden extends AuthException {

    public Forbidden() {
        super("Access denied");
    }
}