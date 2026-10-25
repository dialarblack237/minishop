package com.example.minishop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "reservations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "product_id"}))
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt = Instant.now();

    protected Reservation() {}

    public Reservation(Long orderId, Long productId, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getId()          { return id; }
    public Long getOrderId()     { return orderId; }
    public Long getProductId()   { return productId; }
    public int getQuantity()     { return quantity; }
    public Instant getReservedAt() { return reservedAt; }
}