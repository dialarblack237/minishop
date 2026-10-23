package com.example.minishop.web.dto;

import com.example.minishop.domain.Customer;

import java.time.Instant;

public record CustomerDto(
        Long id,
        String email,
        String displayName,
        String role,
        Instant createdAt
) {
    public static CustomerDto from(Customer c) {
        return new CustomerDto(
                c.getId(),
                c.getEmail(),
                c.getDisplayName(),
                c.getRole(),
                c.getCreatedAt()
        );
    }
}