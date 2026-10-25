package com.example.minishop.service;

import com.example.minishop.domain.Customer;
import com.example.minishop.domain.IdemRow;
import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderHistory;
import com.example.minishop.domain.OrderItem;
import com.example.minishop.domain.OrderState;
import com.example.minishop.domain.OutboxRow;
import com.example.minishop.domain.Product;
import com.example.minishop.event.OrderCreated;
import com.example.minishop.repository.OrderHistoryRepository;
import com.example.minishop.repository.OrderRepository;
import com.example.minishop.repository.OutboxRepository;
import com.example.minishop.repository.ProductRepository;
import com.example.minishop.web.dto.OrderDto;
import com.example.minishop.web.dto.OrderRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final OrderHistoryRepository historyRepo;
    private final OutboxRepository outboxRepo;
    private final ProductRepository productRepo;
    private final IdemService idemService;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepo,
                        OrderHistoryRepository historyRepo,
                        OutboxRepository outboxRepo,
                        ProductRepository productRepo,
                        IdemService idemService,
                        ObjectMapper objectMapper) {
        this.orderRepo = orderRepo;
        this.historyRepo = historyRepo;
        this.outboxRepo = outboxRepo;
        this.productRepo = productRepo;
        this.idemService = idemService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderDto placeOrderIdempotent(String idempotencyKey, OrderRequest req, Customer customer) throws Exception {
        IdemRow claim = idemService.claim(idempotencyKey);

        if (!claim.getResponseJson().equals("{}")) {
            return idemService.readResponse(claim);
        }

        OrderDto created = placeOrder(req, customer);
        idemService.attachResponse(idempotencyKey, created);
        return created;
    }

    @Transactional
    public OrderDto placeOrder(OrderRequest req, Customer customer) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("cart must not be empty");
        }

        Order order = new Order(customer);
        order.setState(OrderState.CREATED);

        int total = 0;
        for (OrderRequest.OrderLine line : req.items()) {
            Product product = productRepo.findById(line.productId())
                    .orElseThrow(() -> new ProductNotFound(line.productId()));

            if (product.getStock() < line.quantity()) {
                throw new StockUnavailable(product.getId());
            }

            OrderItem item = new OrderItem(product, product.getPriceCents(), line.quantity());
            order.addItem(item);
            total += product.getPriceCents() * line.quantity();
        }
        order.setTotalCents(total);

        Order saved = orderRepo.save(order);
        historyRepo.save(OrderHistory.of(saved, null, OrderState.CREATED, "OrderCreated"));

        UUID eventId = UUID.randomUUID();
        OrderCreated event = OrderCreated.from(eventId, saved);
        outboxRepo.save(OutboxRow.forEvent(eventId, "orders", toJson(event)));

        return OrderDto.from(saved);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}