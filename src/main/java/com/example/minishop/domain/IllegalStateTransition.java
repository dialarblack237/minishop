package com.example.minishop.domain;

public class IllegalStateTransition extends RuntimeException {

    private final OrderState from;
    private final OrderEvent event;

    public IllegalStateTransition(OrderState from, OrderEvent event) {
        super("Illegal state transition: " + from + " + " + event);
        this.from = from;
        this.event = event;
    }

    public OrderState getFrom()   { return from; }
    public OrderEvent getEvent()  { return event; }
}