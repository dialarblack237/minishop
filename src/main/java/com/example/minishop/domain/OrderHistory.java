package com.example.minishop.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "order_history",
       indexes = {
           // Fast lookup for "show me the full journey of order 42".
           @Index(name = "ix_order_history_order_time",
                  columnList = "order_id, occurred_at")
       })
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderState fromState;

    // The state AFTER the move. Never null.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderState toState;

    @Column(nullable = false, length = 40)
    private String event;

    @Column(nullable = false)
    private Instant occurredAt;

    protected OrderHistory() {}

    public static OrderHistory of(Order order,
                                  OrderState from,
                                  OrderState to,
                                  String event) {
        OrderHistory h = new OrderHistory();
        h.order = order;
        h.fromState = from;      // can be null on the very first row
        h.toState = to;
        h.event = event;
        h.occurredAt = Instant.now();
        return h;
    }

    public Long getId()           { return id; }
    public Order getOrder()       { return order; }
    public OrderState getFromState() { return fromState; }
    public OrderState getToState()   { return toState; }
    public String getEvent()      { return event; }
    public Instant getOccurredAt() { return occurredAt; }
}