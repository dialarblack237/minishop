package com.example.minishop.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Order — the central entity. One row per purchase.
// Table name is 'orders' because ORDER is a reserved SQL keyword.
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many orders belong to one customer.
    //   LAZY: do not load the customer unless asked — avoids
    //   pulling 10k customer rows to read one order.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // STRING: stores 'CONFIRMED', not a number. Numbers break
    //   when someone adds a value in the middle of the enum.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderState state = OrderState.CREATED;

    @Column(nullable = false)
    private int totalCents;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    // Optimistic lock (Lesson 4.3.3). JPA bumps this on each
    //   update; a second concurrent writer gets OptimisticLockException
    //   instead of silently overwriting.
    @Version
    private int version;

    // One order has many items. cascade ALL + orphanRemoval:
    //   saving the order saves its items; deleting the order
    //   deletes its items (nothing orphaned).
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public Order(Customer customer) {
        this.customer = customer;
    }

    // Helper method: adds an item AND wires both sides
    //   of the relationship. Using this method instead of
    //   direct list manipulation prevents the classic
    //   'items added but order_id null' bug.
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public Long getId()             { return id; }
    public Customer getCustomer()   { return customer; }
    public OrderState getState()    { return state; }
    public void setState(OrderState v) { this.state = v; }
    public int getTotalCents()      { return totalCents; }
    public void setTotalCents(int v) { this.totalCents = v; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }
    public void touchUpdatedAt()    { this.updatedAt = Instant.now(); }
    public int getVersion()         { return version; }
    public List<OrderItem> getItems() { return items; }
}