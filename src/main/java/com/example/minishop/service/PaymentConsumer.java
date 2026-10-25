package com.example.minishop.service;

import com.example.minishop.domain.OutboxRow;
import com.example.minishop.domain.PaymentResult;
import com.example.minishop.event.InventoryReserved;
import com.example.minishop.event.PaymentFailed;
import com.example.minishop.event.PaymentSucceeded;
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
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    private final PaymentSimulator simulator;
    private final DedupeService dedupeService;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public PaymentConsumer(PaymentSimulator simulator,
                           DedupeService dedupeService,
                           OutboxRepository outboxRepo,
                           ObjectMapper objectMapper) {
        this.simulator = simulator;
        this.dedupeService = dedupeService;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "orders", groupId = "payment-service")
    public void onMain(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.MAIN, ack);
    }

    @KafkaListener(topics = "orders.retry.5s", groupId = "payment-service")
    public void onRetry5s(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.RETRY_5S, ack);
    }

    @KafkaListener(topics = "orders.retry.30s", groupId = "payment-service")
    public void onRetry30s(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.RETRY_30S, ack);
    }

    @KafkaListener(topics = "orders.retry.5m", groupId = "payment-service")
    public void onRetry5m(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.RETRY_5M, ack);
    }

    @Transactional
    public void consume(String payload, RetryStage stage, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!"InventoryReserved".equals(root.path("type").asText("InventoryReserved"))) {
                ack.acknowledge();
                return;
            }

            InventoryReserved event = objectMapper.readValue(payload, InventoryReserved.class);
            dedupeService.recordIfNew(event.eventId(), "InventoryReserved");

            PaymentResult result = simulator.charge(event.orderId(), event.amountCents());

            if (result == PaymentResult.APPROVED) {
                PaymentSucceeded succeeded = new PaymentSucceeded(
                        UUID.randomUUID(), event.orderId(), 0L, event.amountCents());
                outboxRepo.save(OutboxRow.forEvent(
                        succeeded.eventId(), "orders", objectMapper.writeValueAsString(succeeded)));
            } else {
                PaymentFailed failed = new PaymentFailed(
                        UUID.randomUUID(), event.orderId(), result.name().toLowerCase());
                outboxRepo.save(OutboxRow.forEvent(
                        failed.eventId(), "orders", objectMapper.writeValueAsString(failed)));
            }
            ack.acknowledge();
        } catch (DuplicateEvent dup) {
            ack.acknowledge();
        } catch (PaymentTimeout timeout) {
            PaymentFailed failed = new PaymentFailed(
                    UUID.randomUUID(), 0L, "timeout");
            try {
                outboxRepo.save(OutboxRow.forEvent(
                        failed.eventId(), "orders", objectMapper.writeValueAsString(failed)));
            } catch (Exception ignored) {}
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("payment failure on stage {}: {}", stage, e.getMessage());
            forward(payload, stage, ack);
        }
    }

    private void forward(String payload, RetryStage stage, Acknowledgment ack) {
        try {
            String reason = stage == RetryStage.RETRY_5M
                    ? DlqReason.EXHAUSTED_RETRIES.value()
                    : DlqReason.UNKNOWN.value();
            outboxRepo.save(OutboxRow.forward(stage.nextTopic(), payload, reason));
        } catch (Exception e) {
            log.error("forward failed — letting Kafka redeliver", e);
            return;
        }
        ack.acknowledge();
    }
}