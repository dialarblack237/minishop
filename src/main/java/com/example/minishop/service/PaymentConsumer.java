package com.example.minishop.service;

import com.example.minishop.domain.OutboxRow;
import com.example.minishop.domain.PaymentResult;
import com.example.minishop.event.InventoryReserved;
import com.example.minishop.event.PaymentFailed;
import com.example.minishop.event.PaymentSucceeded;
import com.example.minishop.repository.OutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    private final PaymentSimulator simulator;
    private final DedupeService dedupeService;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final PipelineMetrics metrics;

    public PaymentConsumer(PaymentSimulator simulator,
                           DedupeService dedupeService,
                           OutboxRepository outboxRepo,
                           ObjectMapper objectMapper,
                           PipelineMetrics metrics) {
        this.simulator = simulator;
        this.dedupeService = dedupeService;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "orders", groupId = "payment-service")
    public void onMain(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.MAIN, ack);
    }

    @KafkaListener(topics = "orders.retry.5s", groupId = "payment-service")
    public void onRetry5s(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.RETRY_5S, ack);
    }

    @KafkaListener(topics = "orders.retry.30s", groupId = "payment-service")
    public void onRetry30s(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.RETRY_30S, ack);
    }

    @KafkaListener(topics = "orders.retry.5m", groupId = "payment-service")
    public void onRetry5m(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.RETRY_5M, ack);
    }

    @Transactional
    public void consume(ConsumerRecord<String, String> record, RetryStage stage, Acknowledgment ack) {
        String payload = record.value();
        int incomingAttempts = readAttempts(record);
        Header traceHeader = record.headers().lastHeader("traceId");
        String traceId = traceHeader == null ? "missing" : new String(traceHeader.value(), StandardCharsets.UTF_8);
        MDC.put("traceId", traceId);

        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!"InventoryReserved".equals(root.path("type").asText("InventoryReserved"))) {
                ack.acknowledge();
                metrics.recordConsumed();
                return;
            }

            InventoryReserved event = objectMapper.readValue(payload, InventoryReserved.class);
            dedupeService.recordIfNew(event.eventId(), "InventoryReserved");

            PaymentResult result = simulator.charge(event.orderId(), event.amountCents());

            if (result == PaymentResult.APPROVED) {
                PaymentSucceeded succeeded = new PaymentSucceeded(
                        UUID.randomUUID(), event.orderId(), 0L, event.amountCents());
                outboxRepo.save(OutboxRow.forEventWithTrace(
                        succeeded.eventId(), "orders", objectMapper.writeValueAsString(succeeded),
                        MDC.get("traceId")));
            } else {
                PaymentFailed failed = new PaymentFailed(
                        UUID.randomUUID(), event.orderId(), result.name().toLowerCase());
                outboxRepo.save(OutboxRow.forEventWithTrace(
                        failed.eventId(), "orders", objectMapper.writeValueAsString(failed),
                        MDC.get("traceId")));
            }
            ack.acknowledge();
            metrics.recordConsumed();
        } catch (DuplicateEvent dup) {
            ack.acknowledge();
            metrics.recordConsumed();
        } catch (PaymentTimeout timeout) {
            PaymentFailed failed = new PaymentFailed(
                    UUID.randomUUID(), 0L, "timeout");
            try {
                outboxRepo.save(OutboxRow.forEventWithTrace(
                        failed.eventId(), "orders", objectMapper.writeValueAsString(failed),
                        MDC.get("traceId")));
            } catch (Exception ignored) {}
            ack.acknowledge();
            metrics.recordConsumed();
        } catch (Exception e) {
            log.warn("payment failure on stage {}: {}", stage, e.getMessage());
            forward(payload, incomingAttempts, stage, ack);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void forward(String payload, int incomingAttempts, RetryStage stage, Acknowledgment ack) {
        try {
            int nextAttempts = incomingAttempts + 1;
            String reason = stage == RetryStage.RETRY_5M
                    ? DlqReason.EXHAUSTED_RETRIES.value()
                    : DlqReason.UNKNOWN.value();
            outboxRepo.save(OutboxRow.forwardWithAttemptsAndTrace(
                    stage.nextTopic(), payload, reason, nextAttempts, MDC.get("traceId")));
            metrics.recordFailed();
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