package com.example.minishop.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxRow")
class OutboxRowTest {

    @Test
    @DisplayName("forEvent sets eventId, topic, payload and leaves attempts at 0")
    void forEvent_setsFields() {
        UUID id = UUID.randomUUID();
        OutboxRow row = OutboxRow.forEvent(id, "orders", "{\"orderId\":42}");

        assertThat(row.getEventId()).isEqualTo(id);
        assertThat(row.getTopic()).isEqualTo("orders");
        assertThat(row.getPayload()).isEqualTo("{\"orderId\":42}");
        assertThat(row.getAttempts()).isEqualTo(0);
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getReason()).isNull();
        assertThat(row.getTraceId()).isNull();
        assertThat(row.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("forEventWithTrace also sets traceId")
    void forEventWithTrace_setsTraceId() {
        UUID id = UUID.randomUUID();
        OutboxRow row = OutboxRow.forEventWithTrace(id, "orders", "{}", "trace-1");

        assertThat(row.getTraceId()).isEqualTo("trace-1");
        assertThat(row.getAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("forward generates a new eventId, copies payload, defaults reason to 'unknown'")
    void forward_defaultsReason() {
        String payload = "{\"foo\":1}";
        OutboxRow row = OutboxRow.forward("orders.DLQ", payload);

        assertThat(row.getTopic()).isEqualTo("orders.DLQ");
        assertThat(row.getPayload()).isEqualTo(payload);
        assertThat(row.getReason()).isEqualTo("unknown");
        assertThat(row.getEventId()).isNotNull();
        assertThat(row.getAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("forward with explicit reason sets it")
    void forward_withReason() {
        OutboxRow row = OutboxRow.forward("orders.DLQ", "{}", "exhausted-retries");

        assertThat(row.getReason()).isEqualTo("exhausted-retries");
    }

    @Test
    @DisplayName("forwardWithAttempts sets attemptsContext")
    void forwardWithAttempts_setsAttemptsContext() {
        OutboxRow row = OutboxRow.forwardWithAttempts("orders.retry.5s", "{}", "unknown", 3);

        assertThat(row.getAttemptsContext()).isEqualTo(3);
    }

    @Test
    @DisplayName("forwardWithAttemptsAndTrace sets attemptsContext and traceId")
    void forwardWithAttemptsAndTrace_setsBoth() {
        OutboxRow row = OutboxRow.forwardWithAttemptsAndTrace(
                "orders.retry.5s", "{}", "unknown", 2, "trace-2");

        assertThat(row.getAttemptsContext()).isEqualTo(2);
        assertThat(row.getTraceId()).isEqualTo("trace-2");
    }

    @Test
    @DisplayName("markPublished sets publishedAt to a non-null Instant")
    void markPublished_setsTimestamp() {
        OutboxRow row = OutboxRow.forEvent(UUID.randomUUID(), "orders", "{}");

        row.markPublished();

        assertThat(row.getPublishedAt()).isNotNull();
        assertThat(row.getPublishedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("recordFailure increments attempts by 1")
    void recordFailure_incrementsAttempts() {
        OutboxRow row = OutboxRow.forEvent(UUID.randomUUID(), "orders", "{}");

        row.recordFailure();
        row.recordFailure();

        assertThat(row.getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("exceededMaxAttempts returns true when attempts equals the max")
    void exceededMaxAttempts_atThreshold() {
        OutboxRow row = OutboxRow.forEvent(UUID.randomUUID(), "orders", "{}");
        row.recordFailure();
        row.recordFailure();

        assertThat(row.exceededMaxAttempts(2)).isTrue();
        assertThat(row.exceededMaxAttempts(3)).isFalse();
    }

    @Test
    @DisplayName("createdAt is set at construction time and never changes on subsequent reads")
    void createdAt_isSetAtConstruction() throws InterruptedException {
        OutboxRow row = OutboxRow.forEvent(UUID.randomUUID(), "orders", "{}");
        Instant first = row.getCreatedAt();
        Thread.sleep(5L);
        assertThat(row.getCreatedAt()).isEqualTo(first);
    }
}