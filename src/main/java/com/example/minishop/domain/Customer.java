package com.example.minishop.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "customers",
       uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role = "CUSTOMER";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Customer() {}

    public Customer(String email, String displayName, String passwordHash) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    public Long getId()                        { return id; }
    public String getEmail()                   { return email; }
    public void setEmail(String v)             { this.email = v; }
    public String getDisplayName()             { return displayName; }
    public void setDisplayName(String v)       { this.displayName = v; }
    public String getPasswordHash()            { return passwordHash; }
    public void setPasswordHash(String v)      { this.passwordHash = v; }
    public String getRole()                    { return role; }
    public void setRole(String v)              { this.role = v; }
    public Instant getCreatedAt()              { return createdAt; }
}