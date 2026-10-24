package com.example.minishop.service;

import com.example.minishop.domain.PaymentResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentSimulator {

    @Value("${app.payments.simulator.mode:approve}")
    private String mode;

    @Value("${app.payments.simulator.slow-seconds:2}")
    private int slowSeconds;

    public PaymentResult charge(long orderId, int amountCents) {
        return switch (mode) {
            case "approve" -> PaymentResult.APPROVED;
            case "decline" -> PaymentResult.DECLINED;
            case "slow" -> {
                try {
                    Thread.sleep(slowSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new PaymentTimeout(orderId);
            }
            default -> throw new IllegalStateException(
                    "Unknown app.payments.simulator.mode: " + mode);
        };
    }
}