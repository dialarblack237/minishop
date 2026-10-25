package com.example.minishop.event;

import java.util.UUID;

public record OrderCancelled(
        UUID eventId,
        long orderId,
        String reason
) {}