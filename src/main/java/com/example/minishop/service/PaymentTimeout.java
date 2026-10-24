package com.example.minishop.service;

public class PaymentTimeout extends RuntimeException {

    private final long orderId;

    public PaymentTimeout(long orderId) {
        super("Payment provider did not respond in time for order " + orderId);
        this.orderId = orderId;
    }

    public long getOrderId() {
        return orderId;
    }
}