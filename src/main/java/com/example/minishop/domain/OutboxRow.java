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

    @Column(name = "reason", length = 40)
    private String reason;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "attempts_context")
    private Integer attemptsContext;

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

    public static OutboxRow forward(String nextTopic, String originalPayload) {
        return forward(nextTopic, originalPayload, "unknown");
    }

    public static OutboxRow forward(String nextTopic, String originalPayload, String reason) {
        OutboxRow row = new OutboxRow();
        row.eventId = UUID.randomUUID();
        row.topic = nextTopic;
        row.payload = originalPayload;
        row.reason = reason;
        return row;
    }

    public static OutboxRow forwardWithAttempts(String nextTopic,
                                                String originalPayload,
                                                String reason,
                                                int attempts) {
        OutboxRow row = new OutboxRow();
        row.eventId = UUID.randomUUID();
        row.topic = nextTopic;
        row.payload = originalPayload;
        row.reason = reason;
        row.attemptsContext = attempts;
        return row;
    }
    public static OutboxRow forEventWithTrace(UUID eventId, String topic,
                                              String payload, String traceId) {
        OutboxRow row = new OutboxRow();
        row.eventId = eventId;
        row.topic = topic;
        row.payload = payload;
        row.traceId = traceId;
        return row;
    }

    public static OutboxRow forwardWithAttemptsAndTrace(String nextTopic,
                                                        String originalPayload,
                                                        String reason,
                                                        int attempts,
                                                        String traceId) {
        OutboxRow row = new OutboxRow();
        row.eventId = UUID.randomUUID();
        row.topic = nextTopic;
        row.payload = originalPayload;
        row.reason = reason;
        row.attemptsContext = attempts;
        row.traceId = traceId;
        return row;
    }
    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void recordFailure() {
        this.attempts++;
    }

    public boolean exceededMaxAttempts(int max) {
        return this.attempts >= max;
    }

    public Long getId()           { return id; }
    public UUID getEventId()      { return eventId; }
    public String getTopic()      { return topic; }
    public String getPayload()    { return payload; }
    public String getReason() { return reason; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts()      { return attempts; }
    public Integer getAttemptsContext() { return attemptsContext; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }
}