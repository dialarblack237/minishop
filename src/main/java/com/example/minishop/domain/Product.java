package com.example.minishop.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Money stored as integer cents (Lesson 1.4.1: never float).
    @Column(nullable = false)
    private int priceCents;

    // Stock count — the schema adds CHECK (stock >= 0).
    @Column(nullable = false)
    private int stock;

    @Version
    private int version;

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
    public int getVersion()             { return version; }
}