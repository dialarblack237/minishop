package com.example.minishop.event;

import java.util.UUID;

public record PaymentSucceeded(
        UUID eventId,
        long orderId,
        long paymentId,
        int amountCents
) {}