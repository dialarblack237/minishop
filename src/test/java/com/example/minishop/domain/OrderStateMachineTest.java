package com.example.minishop.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderStateMachine.apply(from, event)")
class OrderStateMachineTest {

    // Two case shapes — allowed needs an expected target; refused needs nothing more.
    record AllowedCase(OrderState from, OrderEvent event, OrderState expected) {}
    record RefusedCase(OrderState from, OrderEvent event) {}

    // ── ALLOWED transitions — every row from the state machine's green side. ──
    @ParameterizedTest(name = "[{index}] {0} + {1} → {2}")
    @MethodSource("allowedCases")
    @DisplayName("allowed transitions move the order to the expected state")
    void allowedTransition(AllowedCase c) {
        var machine = new OrderStateMachine();
        assertThat(machine.apply(c.from(), c.event())).isEqualTo(c.expected());
    }

    static Stream<AllowedCase> allowedCases() {
        return Stream.of(
                new AllowedCase(OrderState.CREATED,   OrderEvent.InventoryReserved, OrderState.RESERVED),
                new AllowedCase(OrderState.CREATED,   OrderEvent.InventoryRejected, OrderState.CANCELLED),
                new AllowedCase(OrderState.RESERVED,  OrderEvent.PaymentSucceeded,  OrderState.PAID),
                new AllowedCase(OrderState.RESERVED,  OrderEvent.PaymentFailed,     OrderState.CANCELLED),
                new AllowedCase(OrderState.PAID,      OrderEvent.OrderConfirmed,    OrderState.CONFIRMED),
                new AllowedCase(OrderState.CONFIRMED, OrderEvent.RefundRequested,   OrderState.REFUNDED)
        );
    }

    // ── REFUSED pairs — every (state, event) NOT in allowedCases must throw. ──
    @ParameterizedTest(name = "[{index}] {0} + {1} → IllegalStateTransition")
    @MethodSource("refusedCases")
    @DisplayName("every (from, event) pair not in the allowed table is refused")
    void refusedTransition(RefusedCase c) {
        var machine = new OrderStateMachine();
        assertThatThrownBy(() -> machine.apply(c.from(), c.event()))
                .isInstanceOf(IllegalStateTransition.class)
                // The message check turns "some exception" into "THE exception, for THIS pair".
                .hasMessageContaining(c.from().name())
                .hasMessageContaining(c.event().name());
    }

    static Stream<RefusedCase> refusedCases() {
        // Generate every (state × event) pair, minus the allowed set — never hand-listed.
        // A new event or state is automatically covered here, unless an allowed row is added for it.
        Set<Map.Entry<OrderState, OrderEvent>> allowed = allowedCases()
                .map(a -> Map.entry(a.from(), a.event()))
                .collect(Collectors.toSet());

        return Arrays.stream(OrderState.values())
                .flatMap(from -> Arrays.stream(OrderEvent.values())
                        .filter(event -> !allowed.contains(Map.entry(from, event)))
                        .map(event -> new RefusedCase(from, event)));
    }
}