package com.example.minishop.web.dto;

import com.example.minishop.domain.Order;
import com.example.minishop.domain.OrderItem;
import com.example.minishop.domain.OrderState;

import java.time.Instant;
import java.util.List;

public record OrderDto(
        Long id,
        Long customerId,
        OrderState state,
        int totalCents,
        Instant createdAt,
        List<Item> items
) {
    public record Item(Long productId, int quantity, int unitPriceCents) {
        public static Item from(OrderItem oi) {
            return new Item(oi.getProduct().getId(), oi.getQuantity(), oi.getUnitPriceCents());
        }
    }

    public static OrderDto from(Order o) {
        return new OrderDto(
                o.getId(),
                o.getCustomer().getId(),
                o.getState(),
                o.getTotalCents(),
                o.getCreatedAt(),
                o.getItems().stream().map(Item::from).toList()
        );
    }
}