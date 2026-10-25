package com.example.minishop.service;

import com.example.minishop.domain.OutboxRow;
import com.example.minishop.domain.Product;
import com.example.minishop.domain.Reservation;
import com.example.minishop.event.InventoryReserved;
import com.example.minishop.event.OrderCreated;
import com.example.minishop.repository.OutboxRepository;
import com.example.minishop.repository.ProductRepository;
import com.example.minishop.repository.ReservationRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class InventoryConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryConsumer.class);

    private final ProductRepository productRepo;
    private final ReservationRepository reservationRepo;
    private final DedupeService dedupeService;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final PipelineMetrics metrics;

    public InventoryConsumer(ProductRepository productRepo,
                             ReservationRepository reservationRepo,
                             DedupeService dedupeService,
                             OutboxRepository outboxRepo,
                             ObjectMapper objectMapper,
                             PipelineMetrics metrics) {
        this.productRepo = productRepo;
        this.reservationRepo = reservationRepo;
        this.dedupeService = dedupeService;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "orders", groupId = "inventory-service")
    public void onMain(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.MAIN, ack);
    }

    @KafkaListener(topics = "orders.retry.5s", groupId = "inventory-service")
    public void onRetry5s(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.RETRY_5S, ack);
    }

    @KafkaListener(topics = "orders.retry.30s", groupId = "inventory-service")
    public void onRetry30s(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.RETRY_30S, ack);
    }

    @KafkaListener(topics = "orders.retry.5m", groupId = "inventory-service")
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
            if (!"OrderCreated".equals(root.path("type").asText("OrderCreated"))) {
                ack.acknowledge();
                metrics.recordConsumed();
                return;
            }

            OrderCreated event = objectMapper.readValue(payload, OrderCreated.class);
            dedupeService.recordIfNew(event.eventId(), "OrderCreated");

            List<InventoryReserved.Item> reservedItems = new ArrayList<>();
            for (OrderCreated.Item item : event.items()) {
                Product product = productRepo.findByIdForUpdate(item.productId())
                        .orElseThrow(() -> new ProductNotFound(item.productId()));

                if (product.getStock() < item.quantity()) {
                    throw new StockUnavailable(product.getId());
                }

                product.setStock(product.getStock() - item.quantity());
                productRepo.save(product);
                reservationRepo.save(new Reservation(event.orderId(), item.productId(), item.quantity()));
                reservedItems.add(new InventoryReserved.Item(item.productId(), item.quantity()));
            }

            InventoryReserved reserved = new InventoryReserved(
                    UUID.randomUUID(), event.orderId(), event.totalCents(), reservedItems);
            outboxRepo.save(OutboxRow.forEventWithTrace(
                    reserved.eventId(), "orders", objectMapper.writeValueAsString(reserved),
                    MDC.get("traceId")));

            ack.acknowledge();
            metrics.recordConsumed();
        } catch (DuplicateEvent dup) {
            ack.acknowledge();
            metrics.recordConsumed();
        } catch (Exception e) {
            log.warn("inventory failure on stage {}: {}", stage, e.getMessage());
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