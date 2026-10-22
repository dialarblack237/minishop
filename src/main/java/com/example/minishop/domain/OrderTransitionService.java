package com.example.minishop.domain;

import org.springframework.stereotype.Service;

@Service
public class OrderTransitionService {

    private final OrderStateMachine stateMachine;

    public OrderTransitionService(OrderStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    public OrderState next(OrderState from, OrderEvent event) {
        return stateMachine.apply(from, event);
    }
}