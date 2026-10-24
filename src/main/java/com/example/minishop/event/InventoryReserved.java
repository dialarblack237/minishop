package com.example.minishop.event;

import java.util.List;
import java.util.UUID;

public record InventoryReserved(
        UUID eventId,
        long orderId,
        int amountCents,
        List<Item> items
) {
    public record Item(long productId, int quantity) {}
}