package com.example.minishop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dlq_messages")
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_topic", nullable = false)
    private String originalTopic;

    @Column(name = "message_key")
    private String messageKey;

    @Column(nullable = false, length = 40)
    private String reason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "replayed_at")
    private Instant replayedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DlqMessage() {}

    public DlqMessage(String originalTopic, String messageKey, String reason, String payload) {
        this.originalTopic = originalTopic;
        this.messageKey = messageKey;
        this.reason = reason;
        this.payload = payload;
    }

    public void markReplayed() {
        this.replayedAt = Instant.now();
    }

    public Long getId()             { return id; }
    public String getOriginalTopic(){ return originalTopic; }
    public String getMessageKey()   { return messageKey; }
    public String getReason()       { return reason; }
    public String getPayload()      { return payload; }
    public Instant getReplayedAt()  { return replayedAt; }
    public Instant getCreatedAt()   { return createdAt; }
}