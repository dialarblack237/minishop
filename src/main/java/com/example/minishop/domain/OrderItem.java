package com.example.minishop.domain;

import jakarta.persistence.*;

// OrderItem — one line on a receipt.
// The FROZEN unit price lives here, not on Product (Lesson 1.4.1).
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The parent side of the relationship. Many items → one order.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Each line points at one product. Many items → one product.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // The price AT THE MOMENT of ordering. Never read Product.priceCents
    //   to compute a historical total — always read this field.
    @Column(nullable = false)
    private int unitPriceCents;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {}

    public OrderItem(Product product, int unitPriceCents, int quantity) {
        this.product = product;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
    }

    public Long getId()               { return id; }
    public Order getOrder()           { return order; }
    public void setOrder(Order v)     { this.order = v; }
    public Product getProduct()       { return product; }
    public int getUnitPriceCents()    { return unitPriceCents; }
    public int getQuantity()          { return quantity; }
}