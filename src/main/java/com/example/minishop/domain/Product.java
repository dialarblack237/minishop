package com.example.minishop.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int priceCents;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Product() {}

    public Product(String name, int priceCents, int stock) {
        this.name = name;
        this.priceCents = priceCents;
        this.stock = stock;
    }

    public Long getId()                 { return id; }
    public String getName()             { return name; }
    public void setName(String v)       { this.name = v; }
    public int getPriceCents()          { return priceCents; }
    public void setPriceCents(int v)    { this.priceCents = v; }
    public int getStock()               { return stock; }
    public void setStock(int v)         { this.stock = v; }
    public Instant getCreatedAt()       { return createdAt; }
}