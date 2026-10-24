package com.example.minishop.service;

import java.util.List;
import java.util.Map;

public class ValidationException extends AppException {

    private final List<Map<String, String>> fieldErrors;

    public ValidationException(String message, List<Map<String, String>> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }

    public List<Map<String, String>> fieldErrors() {
        return fieldErrors;
    }
}