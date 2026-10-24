package com.example.minishop.event;

import java.util.UUID;

public record InventoryRejected(
        UUID eventId,
        long orderId,
        String reason,
        Long productId
) {}