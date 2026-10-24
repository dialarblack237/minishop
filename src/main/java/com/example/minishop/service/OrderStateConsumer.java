package com.example.minishop.service;

import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderEvent;
import com.example.minishop.domain.OrderHistory;
import com.example.minishop.domain.OrderState;
import com.example.minishop.domain.OrderStateMachine;
import com.example.minishop.event.InventoryRejected;
import com.example.minishop.event.InventoryReserved;
import com.example.minishop.event.PaymentFailed;
import com.example.minishop.event.PaymentSucceeded;
import com.example.minishop.repository.OrderHistoryRepository;
import com.example.minishop.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class OrderStateConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderStateConsumer.class);

    private final OrderRepository orderRepo;
    private final OrderHistoryRepository historyRepo;
    private final OrderStateMachine stateMachine;
    private final DedupeService dedupeService;
    private final ObjectMapper objectMapper;

    public OrderStateConsumer(OrderRepository orderRepo,
                              OrderHistoryRepository historyRepo,
                              OrderStateMachine stateMachine,
                              DedupeService dedupeService,
                              ObjectMapper objectMapper) {
        this.orderRepo = orderRepo;
        this.historyRepo = historyRepo;
        this.stateMachine = stateMachine;
        this.dedupeService = dedupeService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "orders", groupId = "order-state-service")
    @Transactional
    public void onInventoryReserved(String payload) throws Exception {
        InventoryReserved event = objectMapper.readValue(payload, InventoryReserved.class);
        applyTransition(event.eventId(), event.orderId(), OrderEvent.InventoryReserved);
    }

    @KafkaListener(topics = "orders", groupId = "order-state-service")
    @Transactional
    public void onInventoryRejected(String payload) throws Exception {
        InventoryRejected event = objectMapper.readValue(payload, InventoryRejected.class);
        applyTransition(event.eventId(), event.orderId(), OrderEvent.InventoryRejected);
    }

    @KafkaListener(topics = "orders", groupId = "order-state-service")
    @Transactional
    public void onPaymentSucceeded(String payload) throws Exception {
        PaymentSucceeded event = objectMapper.readValue(payload, PaymentSucceeded.class);
        applyTransition(event.eventId(), event.orderId(), OrderEvent.PaymentSucceeded);
    }

    @KafkaListener(topics = "orders", groupId = "order-state-service")
    @Transactional
    public void onPaymentFailed(String payload) throws Exception {
        PaymentFailed event = objectMapper.readValue(payload, PaymentFailed.class);
        applyTransition(event.eventId(), event.orderId(), OrderEvent.PaymentFailed);
    }

    private void applyTransition(UUID eventId, long orderId, OrderEvent event) {
        try {
            dedupeService.recordIfNew(eventId, event.name());
        } catch (DuplicateEvent dup) {
            log.info("skipping duplicate {} for order {}", event.name(), orderId);
            return;
        }

        Order order = orderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        OrderState before = order.getState();
        OrderState after = stateMachine.apply(before, event);

        order.setState(after);
        orderRepo.save(order);
        historyRepo.save(OrderHistory.of(order, before, after, event.name()));

        log.info("order {} transitioned {} -> {} via {}", orderId, before, after, event);
    }
}