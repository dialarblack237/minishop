package com.example.minishop.domain;

public enum OrderEvent {
    // Inventory side (CREATED → RESERVED / CANCELLED)
    InventoryReserved,
    InventoryRejected,
    // Payment side (RESERVED → PAID / CANCELLED)
    PaymentSucceeded,
    PaymentFailed,
    // Confirmation (PAID → CONFIRMED)
    OrderConfirmed,
    // Refund (CONFIRMED → REFUNDED)
    RefundRequested
}