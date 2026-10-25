package com.example.minishop.service;

import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderEvent;
import com.example.minishop.domain.OrderHistory;
import com.example.minishop.domain.OrderState;
import com.example.minishop.domain.OrderStateMachine;
import com.example.minishop.domain.OutboxRow;
import com.example.minishop.event.InventoryRejected;
import com.example.minishop.event.InventoryReserved;
import com.example.minishop.event.OrderCancelled;
import com.example.minishop.event.PaymentFailed;
import com.example.minishop.event.PaymentSucceeded;
import com.example.minishop.repository.OrderHistoryRepository;
import com.example.minishop.repository.OrderRepository;
import com.example.minishop.repository.OutboxRepository;
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
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public OrderStateConsumer(OrderRepository orderRepo,
                              OrderHistoryRepository historyRepo,
                              OrderStateMachine stateMachine,
                              DedupeService dedupeService,
                              OutboxRepository outboxRepo,
                              ObjectMapper objectMapper) {
        this.orderRepo = orderRepo;
        this.historyRepo = historyRepo;
        this.stateMachine = stateMachine;
        this.dedupeService = dedupeService;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "orders", groupId = "order-state-service")
    @Transactional
    public void onInventoryReserved(String payload) throws Exception {
        InventoryReserved event = objectMapper.readValue(payload, InventoryReserved.class);
        applyTransition(event.eventId(), event.orderId(), OrderEvent.InventoryReserved, null);
    }

    @KafkaListener(topics = "orders", groupId = "order-state-service")
    @Transactional
    public void onInventoryRejected(String payload) throws Exception {
        InventoryRejected event = objectMapper.readValue(payload, InventoryRejected.class);
        applyTransition(event.eventId(), event.orderId(), OrderEvent.InventoryRejected, event.reason());
    }

    @KafkaListener(topics = "orders", groupId = "order-state-service")
    @Transactional
    public void onPaymentSucceeded(String payload) throws Exception {
        PaymentSucceeded event = objectMapper.readValue(payload, PaymentSucceeded.class);
        applyTransition(event.eventId(), event.orderId(), OrderEvent.PaymentSucceeded, null);
    }

    @KafkaListener(topics = "orders", groupId = "order-state-service")
    @Transactional
    public void onPaymentFailed(String payload) throws Exception {
        PaymentFailed event = objectMapper.readValue(payload, PaymentFailed.class);
        applyTransition(event.eventId(), event.orderId(), OrderEvent.PaymentFailed, event.reason());
    }

    private void applyTransition(UUID sourceEventId, long orderId, OrderEvent event, String reason) throws Exception {
        try {
            dedupeService.recordIfNew(sourceEventId, event.name());
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

        if (after == OrderState.CANCELLED) {
            OrderCancelled cancelled = new OrderCancelled(
                    UUID.randomUUID(), orderId, reason == null ? "unknown" : reason);
            outboxRepo.save(OutboxRow.forEvent(
                    cancelled.eventId(), "orders", objectMapper.writeValueAsString(cancelled)));
        }
    }
}