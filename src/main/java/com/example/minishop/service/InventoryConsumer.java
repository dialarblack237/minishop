package com.example.minishop.service;

import com.example.minishop.domain.OutboxRow;
import com.example.minishop.domain.Product;
import com.example.minishop.event.InventoryRejected;
import com.example.minishop.event.InventoryReserved;
import com.example.minishop.event.OrderCreated;
import com.example.minishop.repository.OutboxRepository;
import com.example.minishop.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class InventoryConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryConsumer.class);

    private final ProductRepository productRepo;
    private final DedupeService dedupeService;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public InventoryConsumer(ProductRepository productRepo,
                             DedupeService dedupeService,
                             OutboxRepository outboxRepo,
                             ObjectMapper objectMapper) {
        this.productRepo = productRepo;
        this.dedupeService = dedupeService;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "orders", groupId = "inventory-service")
    @Transactional
    public void onOrderCreated(String payload) throws Exception {
        OrderCreated event = objectMapper.readValue(payload, OrderCreated.class);

        try {
            dedupeService.recordIfNew(event.eventId(), "OrderCreated");
        } catch (DuplicateEvent dup) {
            log.info("skipping duplicate OrderCreated {}", event.eventId());
            return;
        }

        List<InventoryReserved.Item> reservedItems = new ArrayList<>();
        for (OrderCreated.Item item : event.items()) {
            try {
                Product product = productRepo.findByIdForUpdate(item.productId())
                        .orElseThrow(() -> new ProductNotFound(item.productId()));

                if (product.getStock() < item.quantity()) {
                    throw new StockUnavailable(product.getId());
                }

                product.setStock(product.getStock() - item.quantity());
                productRepo.save(product);
                reservedItems.add(new InventoryReserved.Item(item.productId(), item.quantity()));
            } catch (ProductNotFound | StockUnavailable rejected) {
                InventoryRejected reject = new InventoryRejected(
                        UUID.randomUUID(),
                        event.orderId(),
                        rejected instanceof StockUnavailable ? "out-of-stock" : "product-missing",
                        item.productId());
                outboxRepo.save(OutboxRow.forEvent(
                        reject.eventId(), "orders", objectMapper.writeValueAsString(reject)));
                return;
            }
        }

        InventoryReserved reserved = new InventoryReserved(
                UUID.randomUUID(),
                event.orderId(),
                event.totalCents(),
                reservedItems);
        outboxRepo.save(OutboxRow.forEvent(
                reserved.eventId(), "orders", objectMapper.writeValueAsString(reserved)));
    }
}