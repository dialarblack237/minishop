package com.example.minishop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
public class IdemRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idem_key", nullable = false, unique = true)
    private String idemKey;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IdemRow() {}

    public IdemRow(String idemKey, String responseJson) {
        this.idemKey = idemKey;
        this.responseJson = responseJson;
    }

    public Long getId()              { return id; }
    public String getIdemKey()       { return idemKey; }
    public String getResponseJson()  { return responseJson; }
    public void setResponseJson(String v) { this.responseJson = v; }
    public Instant getCreatedAt()    { return createdAt; }
}