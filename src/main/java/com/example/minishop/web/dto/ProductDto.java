package com.example.minishop.web.dto;

import com.example.minishop.domain.Product;

import java.time.Instant;

public record ProductDto(
        Long id,
        String name,
        int priceCents,
        int stock,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductDto from(Product p) {
        return new ProductDto(
                p.getId(),
                p.getName(),
                p.getPriceCents(),
                p.getStock(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}