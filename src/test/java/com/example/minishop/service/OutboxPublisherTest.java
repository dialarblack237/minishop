package com.example.minishop.service;

import com.example.minishop.domain.OutboxRow;
import com.example.minishop.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisher.publishUnsent")
class OutboxPublisherTest {

    @Mock OutboxRepository outboxRepo;
    @Mock OutboxService outboxService;
    @Mock KafkaTemplate<String, String> kafkaTemplate;
    @Mock PipelineMetrics metrics;
    @Mock RedissonClient redisson;
    @Mock RLock lock;

    OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxRepo, outboxService, kafkaTemplate, metrics, redisson);
    }

    // ── TEST 1 — happy path. ──────────────────────────────
    @Test
    @DisplayName("publishes one unsent row: send called once, topic matches, payload contains the event body")
    void publishesOneUnsentRow() throws Exception {
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        UUID eventId = UUID.randomUUID();
        OutboxRow row = OutboxRow.forEventWithTrace(
                eventId, "orders", "{\"orderId\":42,\"totalCents\":2400}", null);
        // Factory-built rows have no id (no save() ran). The publisher reads getId() and
        // passes it to markPublished(long) — simulate @GeneratedValue so the unbox is not null.
        assignId(row, 1L);

        when(outboxRepo.findUnsent(any(Pageable.class))).thenReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> completed =
                CompletableFuture.completedFuture(new SendResult<>(null, null));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completed);

        publisher.publishUnsent();

        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(1)).send(recordCaptor.capture());

        ProducerRecord<String, String> captured = recordCaptor.getValue();
        assertThat(captured.topic()).isEqualTo("orders");
        assertThat(captured.key()).isEqualTo(eventId.toString());
        assertThat(captured.value()).contains("\"orderId\":42").contains("\"totalCents\":2400");

        // markPublished(long) — primitive → anyLong().
        verify(outboxService, times(1)).markPublished(anyLong());
        verify(outboxService, never()).recordFailure(anyLong());
    }

    // ── TEST 2 — failure path. ────────────────────────────
    @Test
    @DisplayName("records failure and stops when Kafka send throws")
    void recordsFailure_whenKafkaSendFails() throws Exception {
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        OutboxRow row = OutboxRow.forEvent(UUID.randomUUID(), "orders", "{}");
        assignId(row, 2L);

        when(outboxRepo.findUnsent(any(Pageable.class))).thenReturn(List.of(row));

        CompletableFuture<SendResult<String, String>> failed =
                CompletableFuture.failedFuture(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        publisher.publishUnsent();

        verify(outboxService, times(1)).recordFailure(anyLong());
        verify(outboxService, never()).markPublished(anyLong());
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    // ── TEST 3 — empty outbox. ────────────────────────────
    @Test
    @DisplayName("does nothing when there are no unsent rows")
    void doesNothing_whenOutboxEmpty() throws Exception {
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        when(outboxRepo.findUnsent(any(Pageable.class))).thenReturn(List.of());

        publisher.publishUnsent();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(outboxService, never()).markPublished(anyLong());
        verify(outboxService, never()).recordFailure(anyLong());
        verify(lock).unlock();
    }

    /**
     * OutboxRow has no id setter and factories leave id null (no @GeneratedValue without save).
     * The publisher passes getId() into a primitive-long parameter — unboxing null would NPE.
     * Simulate what a real save() would do, via reflection on the private id field.
     */
    private static void assignId(OutboxRow row, long value) {
        try {
            Field idField = OutboxRow.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(row, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot assign OutboxRow id", e);
        }
    }
}