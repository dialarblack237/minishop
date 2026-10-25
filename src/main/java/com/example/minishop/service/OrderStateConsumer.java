package com.example.minishop.service;

import com.example.minishop.domain.IllegalStateTransition;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
    public void onMain(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.MAIN, ack);
    }

    @KafkaListener(topics = "orders.retry.5s", groupId = "order-state-service")
    public void onRetry5s(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.RETRY_5S, ack);
    }

    @KafkaListener(topics = "orders.retry.30s", groupId = "order-state-service")
    public void onRetry30s(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.RETRY_30S, ack);
    }

    @KafkaListener(topics = "orders.retry.5m", groupId = "order-state-service")
    public void onRetry5m(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.RETRY_5M, ack);
    }

    @Transactional
    public void consume(ConsumerRecord<String, String> record, RetryStage stage, Acknowledgment ack) {
        String payload = record.value();
        int incomingAttempts = readAttempts(record);

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
            forward(payload, incomingAttempts, stage, ack);
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

    private void forward(String payload, int incomingAttempts, RetryStage stage, Acknowledgment ack) {
        try {
            int nextAttempts = incomingAttempts + 1;
            String reason = stage == RetryStage.RETRY_5M
                    ? DlqReason.EXHAUSTED_RETRIES.value()
                    : DlqReason.UNKNOWN.value();
            outboxRepo.save(OutboxRow.forwardWithAttempts(
                    stage.nextTopic(), payload, reason, nextAttempts));
        } catch (Exception e) {
            log.error("forward failed — letting Kafka redeliver", e);
            return;
        }
        ack.acknowledge();
    }

    private int readAttempts(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("attempts");
        if (header == null) return 0;
        try {
            return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}