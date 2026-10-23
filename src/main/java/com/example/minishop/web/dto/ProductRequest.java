package com.example.minishop.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Min(0) int priceCents,
        @Min(0) int stock
) {}