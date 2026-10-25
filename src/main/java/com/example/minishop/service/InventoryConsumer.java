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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    public InventoryConsumer(ProductRepository productRepo,
                             ReservationRepository reservationRepo,
                             DedupeService dedupeService,
                             OutboxRepository outboxRepo,
                             ObjectMapper objectMapper) {
        this.productRepo = productRepo;
        this.reservationRepo = reservationRepo;
        this.dedupeService = dedupeService;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "orders", groupId = "inventory-service")
    public void onMain(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.MAIN, ack);
    }

    @KafkaListener(topics = "orders.retry.5s", groupId = "inventory-service")
    public void onRetry5s(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.RETRY_5S, ack);
    }

    @KafkaListener(topics = "orders.retry.30s", groupId = "inventory-service")
    public void onRetry30s(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.RETRY_30S, ack);
    }

    @KafkaListener(topics = "orders.retry.5m", groupId = "inventory-service")
    public void onRetry5m(String payload, Acknowledgment ack) {
        consume(payload, RetryStage.RETRY_5M, ack);
    }

    @Transactional
    public void consume(String payload, RetryStage stage, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!"OrderCreated".equals(root.path("type").asText("OrderCreated"))) {
                ack.acknowledge();
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
            outboxRepo.save(OutboxRow.forEvent(
                    reserved.eventId(), "orders", objectMapper.writeValueAsString(reserved)));

            ack.acknowledge();
        } catch (DuplicateEvent dup) {
            log.info("skipping duplicate OrderCreated");
            ack.acknowledge();
        } catch (Exception e) {
            log.warn("inventory failure on stage {}: {}", stage, e.getMessage());
            forward(payload, stage, ack);
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