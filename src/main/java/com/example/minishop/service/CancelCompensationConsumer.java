package com.example.minishop.service;

import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderHistory;
import com.example.minishop.domain.OrderState;
import com.example.minishop.domain.OutboxRow;
import com.example.minishop.domain.Product;
import com.example.minishop.domain.Reservation;
import com.example.minishop.event.OrderCancelled;
import com.example.minishop.repository.OrderHistoryRepository;
import com.example.minishop.repository.OrderRepository;
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
import java.util.List;

@Component
public class CancelCompensationConsumer {

    private static final Logger log = LoggerFactory.getLogger(CancelCompensationConsumer.class);

    private final ReservationRepository reservationRepo;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final OrderHistoryRepository historyRepo;
    private final DedupeService dedupeService;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final PipelineMetrics metrics;

    public CancelCompensationConsumer(ReservationRepository reservationRepo,
                                      ProductRepository productRepo,
                                      OrderRepository orderRepo,
                                      OrderHistoryRepository historyRepo,
                                      DedupeService dedupeService,
                                      OutboxRepository outboxRepo,
                                      ObjectMapper objectMapper,
                                      PipelineMetrics metrics) {
        this.reservationRepo = reservationRepo;
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
        this.historyRepo = historyRepo;
        this.dedupeService = dedupeService;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "orders", groupId = "cancel-compensation-service")
    public void onMain(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.MAIN, ack);
    }

    @KafkaListener(topics = "orders.retry.5s", groupId = "cancel-compensation-service")
    public void onRetry5s(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.RETRY_5S, ack);
    }

    @KafkaListener(topics = "orders.retry.30s", groupId = "cancel-compensation-service")
    public void onRetry30s(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consume(record, RetryStage.RETRY_30S, ack);
    }

    @KafkaListener(topics = "orders.retry.5m", groupId = "cancel-compensation-service")
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
            if (!"OrderCancelled".equals(root.path("type").asText("OrderCancelled"))) {
                ack.acknowledge();
                metrics.recordConsumed();
                return;
            }

            OrderCancelled event = objectMapper.readValue(payload, OrderCancelled.class);
            dedupeService.recordIfNew(event.eventId(), "OrderCancelled");

            List<Reservation> reservations = reservationRepo.findByOrderId(event.orderId());
            if (reservations.isEmpty()) {
                ack.acknowledge();
                metrics.recordConsumed();
                return;
            }

            for (Reservation r : reservations) {
                Product product = productRepo.findByIdForUpdate(r.getProductId())
                        .orElseThrow(() -> new IllegalStateException("Product not found: " + r.getProductId()));
                product.setStock(product.getStock() + r.getQuantity());
                productRepo.save(product);
                reservationRepo.delete(r);
            }

            Order order = orderRepo.getReferenceById(event.orderId());
            historyRepo.save(OrderHistory.of(order, OrderState.CANCELLED, OrderState.CANCELLED, "StockReleased"));

            ack.acknowledge();
            metrics.recordConsumed();
        } catch (DuplicateEvent dup) {
            ack.acknowledge();
            metrics.recordConsumed();
        } catch (Exception e) {
            log.warn("cancel-compensation failure on stage {}: {}", stage, e.getMessage());
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