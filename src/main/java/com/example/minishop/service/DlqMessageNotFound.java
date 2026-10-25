package com.example.minishop.service;

public class DlqMessageNotFound extends NotFoundException {

    public DlqMessageNotFound(long id) {
        super("DLQ message not found: " + id);
    }
}