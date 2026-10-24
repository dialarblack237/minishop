package com.example.minishop.event;

import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderItem;

import java.util.List;
import java.util.UUID;

public record OrderCreated(
        UUID eventId,
        long orderId,
        long customerId,
        int totalCents,
        List<Item> items
) {
    public record Item(long productId, int quantity, int unitPriceCents) {
        public static Item from(OrderItem oi) {
            return new Item(oi.getProduct().getId(), oi.getQuantity(), oi.getUnitPriceCents());
        }
    }

    public static OrderCreated from(UUID eventId, Order order) {
        return new OrderCreated(
                eventId,
                order.getId(),
                order.getCustomer().getId(),
                order.getTotalCents(),
                order.getItems().stream().map(Item::from).toList()
        );
    }
}