package com.example.minishop.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IllegalStateTransition")
class IllegalStateTransitionTest {

    @Test
    @DisplayName("message names both the from state and the event")
    void message_namesFromAndEvent() {
        IllegalStateTransition ex = new IllegalStateTransition(
                OrderState.CREATED, OrderEvent.PaymentSucceeded);

        assertThat(ex).hasMessageContaining("CREATED");
        assertThat(ex).hasMessageContaining("PaymentSucceeded");
    }

    @Test
    @DisplayName("throwing it from the state machine preserves the from state and event")
    void stateMachine_throwsWithCorrectStates() {
        OrderStateMachine machine = new OrderStateMachine();

        assertThatThrownBy(() -> machine.apply(OrderState.PAID, OrderEvent.InventoryReserved))
                .isInstanceOf(IllegalStateTransition.class)
                .hasMessageContaining("PAID")
                .hasMessageContaining("InventoryReserved");
    }

    @Test
    @DisplayName("terminal states refuse every event")
    void terminalStates_refuseEveryEvent() {
        OrderStateMachine machine = new OrderStateMachine();

        for (OrderState terminal : new OrderState[]{OrderState.CANCELLED, OrderState.REFUNDED}) {
            for (OrderEvent event : OrderEvent.values()) {
                assertThatThrownBy(() -> machine.apply(terminal, event))
                        .as("%s + %s must be refused", terminal, event)
                        .isInstanceOf(IllegalStateTransition.class);
            }
        }
    }
}