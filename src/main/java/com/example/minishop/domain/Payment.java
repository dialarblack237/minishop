package com.example.minishop.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Payment — the record of one payment attempt.
// One payment per order in v1 (Lesson 1.4.1).
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false)
    private int amountCents;

    // APPROVED / DECLINED / SLOW — same rule as OrderState.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentResult result;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Payment() {}

    public Payment(Order order, int amountCents, PaymentResult result) {
        this.order = order;
        this.amountCents = amountCents;
        this.result = result;
    }

    public Long getId()              { return id; }
    public Order getOrder()          { return order; }
    public int getAmountCents()      { return amountCents; }
    public PaymentResult getResult() { return result; }
    public Instant getCreatedAt()    { return createdAt; }
}