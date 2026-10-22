package com.example.minishop.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderState state = OrderState.CREATED;

    @Column(nullable = false)
    private int totalCents;

    @Version
    private int version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(Customer customer) {
        this.customer = customer;
    }

    // Helper — adds an item AND wires both sides of the relationship.
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public Long getId()                { return id; }
    public Customer getCustomer()      { return customer; }
    public OrderState getState()       { return state; }
    public void setState(OrderState v) { this.state = v; }
    public int getTotalCents()         { return totalCents; }
    public void setTotalCents(int v)   { this.totalCents = v; }
    public int getVersion()            { return version; }
    public List<OrderItem> getItems()  { return items; }
}