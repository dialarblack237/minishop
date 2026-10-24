package com.example.minishop.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected OutboxRow() {}

    public static OutboxRow forEvent(UUID eventId, String topic, String payload) {
        OutboxRow row = new OutboxRow();
        row.eventId = eventId;
        row.topic = topic;
        row.payload = payload;
        return row;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public Long getId()           { return id; }
    public UUID getEventId()      { return eventId; }
    public String getTopic()      { return topic; }
    public String getPayload()    { return payload; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts()      { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
}