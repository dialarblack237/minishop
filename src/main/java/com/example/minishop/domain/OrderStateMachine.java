package com.example.minishop.domain;

import org.springframework.stereotype.Component;

@Component
public class OrderStateMachine {

    // Returns the next state, or throws IllegalStateTransition.
    // Because this method never touches the database, it can
    //   be tested with a table-driven test (Module 10.1.3):
    //   one line per allowed (from, event) pair.
    public OrderState apply(OrderState from, OrderEvent event) {
        return switch (from) {
            case CREATED -> switch (event) {
                case InventoryReserved -> OrderState.RESERVED;
                case InventoryRejected -> OrderState.CANCELLED;
                // Any other event from CREATED is a bug — refuse it.
                default -> throw new IllegalStateTransition(from, event);
            };
            case RESERVED -> switch (event) {
                case PaymentSucceeded -> OrderState.PAID;
                case PaymentFailed    -> OrderState.CANCELLED;
                default -> throw new IllegalStateTransition(from, event);
            };
            case PAID -> switch (event) {
                case OrderConfirmed -> OrderState.CONFIRMED;
                default -> throw new IllegalStateTransition(from, event);
            };
            case CONFIRMED -> switch (event) {
                case RefundRequested -> OrderState.REFUNDED;
                default -> throw new IllegalStateTransition(from, event);
            };
            // Terminal states: no outgoing arrows in v1.
            case CANCELLED, REFUNDED ->
                throw new IllegalStateTransition(from, event);
        };
    }
}