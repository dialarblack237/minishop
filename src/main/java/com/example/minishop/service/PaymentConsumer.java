package com.example.minishop.service;

import com.example.minishop.domain.OutboxRow;
import com.example.minishop.domain.PaymentResult;
import com.example.minishop.event.InventoryReserved;
import com.example.minishop.event.PaymentFailed;
import com.example.minishop.event.PaymentSucceeded;
import com.example.minishop.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
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
    @Transactional
    public void onInventoryReserved(String payload) throws Exception {
        InventoryReserved event = objectMapper.readValue(payload, InventoryReserved.class);

        try {
            dedupeService.recordIfNew(event.eventId(), "InventoryReserved");
        } catch (DuplicateEvent dup) {
            log.info("skipping duplicate InventoryReserved {}", event.eventId());
            return;
        }

        try {
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
        } catch (PaymentTimeout timeout) {
            PaymentFailed failed = new PaymentFailed(
                    UUID.randomUUID(), event.orderId(), "timeout");
            outboxRepo.save(OutboxRow.forEvent(
                    failed.eventId(), "orders", objectMapper.writeValueAsString(failed)));
        }
    }
}