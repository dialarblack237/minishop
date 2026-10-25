package com.example.minishop.service;

import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderEvent;
import com.example.minishop.domain.OrderHistory;
import com.example.minishop.domain.OrderState;
import com.example.minishop.domain.OrderStateMachine;
import com.example.minishop.domain.OutboxRow;
import com.example.minishop.domain.IllegalStateTransition;
import com.example.minishop.event.InventoryRejected;
import com.example.minishop.event.InventoryReserved;
import com.example.minishop.event.OrderCancelled;
import com.example.minishop.event.PaymentFailed;
import com.example.minishop.event.PaymentSucceeded;
import com.example.minishop.repository.OrderHistoryRepository;
import com.example.minishop.repository.OrderRepository;
import com.example.minishop.repository.OutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
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
    public void onMain(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.MAIN, ack);
    }

    @KafkaListener(topics = "orders.retry.5s", groupId = "order-state-service")
    public void onRetry5s(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.RETRY_5S, ack);
    }

    @KafkaListener(topics = "orders.retry.30s", groupId = "order-state-service")
    public void onRetry30s(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.RETRY_30S, ack);
    }

    @KafkaListener(topics = "orders.retry.5m", groupId = "order-state-service")
    public void onRetry5m(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.RETRY_5M, ack);
    }

    @Transactional
    public void consume(String payload, RetryStage stage, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText("InventoryReserved");

            switch (type) {
                case "InventoryReserved" -> {
                    InventoryReserved e = objectMapper.readValue(payload, InventoryReserved.class);
                    applyTransition(e.eventId(), e.orderId(), OrderEvent.InventoryReserved, null);
                }
                case "InventoryRejected" -> {
                    InventoryRejected e = objectMapper.readValue(payload, InventoryRejected.class);
                    applyTransition(e.eventId(), e.orderId(), OrderEvent.InventoryRejected, e.reason());
                }
                case "PaymentSucceeded" -> {
                    PaymentSucceeded e = objectMapper.readValue(payload, PaymentSucceeded.class);
                    applyTransition(e.eventId(), e.orderId(), OrderEvent.PaymentSucceeded, null);
                }
                case "PaymentFailed" -> {
                    PaymentFailed e = objectMapper.readValue(payload, PaymentFailed.class);
                    applyTransition(e.eventId(), e.orderId(), OrderEvent.PaymentFailed, e.reason());
                }
                default -> { }
            }
            ack.acknowledge();
        } catch (DuplicateEvent dup) {
            ack.acknowledge();
        } catch (IllegalStateTransition illegal) {
            log.info("illegal transition — dropping: {}", illegal.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("state failure on stage {}: {}", stage, e.getMessage());
            forward(payload, stage, ack);
        }
    }

    private void applyTransition(UUID sourceEventId, long orderId, OrderEvent event, String reason) throws Exception {
        dedupeService.recordIfNew(sourceEventId, event.name());

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

    private void forward(String payload, RetryStage stage, Acknowledgment ack) {
        try {
            outboxRepo.save(OutboxRow.forward(stage.nextTopic(), payload));
        } catch (Exception e) {
            log.error("forward failed — letting Kafka redeliver", e);
            return;
        }
        ack.acknowledge();
    }
}