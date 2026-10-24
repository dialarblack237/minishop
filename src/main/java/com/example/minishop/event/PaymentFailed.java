package com.example.minishop.event;

import java.util.UUID;

public record PaymentFailed(
        UUID eventId,
        long orderId,
        String reason
) {}