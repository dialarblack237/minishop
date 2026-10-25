package com.example.minishop.service;

import com.example.minishop.event.InventoryRejected;

public class ReservationFailed extends RuntimeException {

    private final InventoryRejected event;

    public ReservationFailed(InventoryRejected event) {
        super("Reservation failed: " + event.reason() + " on product " + event.productId());
        this.event = event;
    }

    public InventoryRejected getEvent() {
        return event;
    }
}