package com.example.minishop.service;

import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderHistory;
import com.example.minishop.domain.OrderState;
import com.example.minishop.domain.Product;
import com.example.minishop.domain.Reservation;
import com.example.minishop.event.OrderCancelled;
import com.example.minishop.repository.OrderHistoryRepository;
import com.example.minishop.repository.OrderRepository;
import com.example.minishop.repository.ProductRepository;
import com.example.minishop.repository.ReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CancelCompensationConsumer {

    private static final Logger log = LoggerFactory.getLogger(CancelCompensationConsumer.class);

    private final ReservationRepository reservationRepo;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final OrderHistoryRepository historyRepo;
    private final DedupeService dedupeService;
    private final ObjectMapper objectMapper;

    public CancelCompensationConsumer(ReservationRepository reservationRepo,
                                      ProductRepository productRepo,
                                      OrderRepository orderRepo,
                                      OrderHistoryRepository historyRepo,
                                      DedupeService dedupeService,
                                      ObjectMapper objectMapper) {
        this.reservationRepo = reservationRepo;
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
        this.historyRepo = historyRepo;
        this.dedupeService = dedupeService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "orders", groupId = "cancel-compensation-service")
    @Transactional
    public void onOrderCancelled(String payload) throws Exception {
        OrderCancelled event = objectMapper.readValue(payload, OrderCancelled.class);

        try {
            dedupeService.recordIfNew(event.eventId(), "OrderCancelled");
        } catch (DuplicateEvent dup) {
            log.info("skipping duplicate OrderCancelled {}", event.eventId());
            return;
        }

        List<Reservation> reservations = reservationRepo.findByOrderId(event.orderId());
        if (reservations.isEmpty()) {
            log.info("nothing to compensate for order {} ({})", event.orderId(), event.reason());
            return;
        }

        for (Reservation r : reservations) {
            Product product = productRepo.findByIdForUpdate(r.getProductId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Product not found: " + r.getProductId()));
            product.setStock(product.getStock() + r.getQuantity());
            productRepo.save(product);
            reservationRepo.delete(r);
        }

        Order order = orderRepo.getReferenceById(event.orderId());
        historyRepo.save(OrderHistory.of(
                order,
                OrderState.CANCELLED,
                OrderState.CANCELLED,
                "StockReleased"));

        log.info("released {} reservations for cancelled order {}",
                reservations.size(), event.orderId());
    }
}