package com.example.minishop.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

import java.util.List;

public record OrderRequest(
        @NotEmpty @Valid List<OrderLine> items
) {
    public record OrderLine(
            @NotNull Long productId,
            @Min(1) int quantity
    ) {}
}