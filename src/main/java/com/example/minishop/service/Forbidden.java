package com.example.minishop.service;

public class Forbidden extends RuntimeException {
    public Forbidden() {
        super("Access denied");
    }
}