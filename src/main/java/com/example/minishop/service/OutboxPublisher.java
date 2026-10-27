package com.example.minishop.service;

import com.example.minishop.domain.OutboxRow;
import com.example.minishop.repository.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 5;
    private static final long KAFKA_TIMEOUT_SECONDS = 5;
    private static final String LOCK_NAME = "lock:outbox-publisher";

    private final OutboxRepository outboxRepo;
    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PipelineMetrics metrics;
    private final RedissonClient redisson;

    public OutboxPublisher(OutboxRepository outboxRepo,
                           OutboxService outboxService,
                           KafkaTemplate<String, String> kafkaTemplate,
                           PipelineMetrics metrics,
                           RedissonClient redisson) {
        this.outboxRepo = outboxRepo;
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;
        this.redisson = redisson;
    }

    @Scheduled(fixedDelay = 2000)
    public void publishUnsent() {
        RLock lock = redisson.getLock(LOCK_NAME);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(2, 30, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("outbox publisher skipped — previous run still holds lock");
                return;
            }
            doPublish();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("outbox publisher interrupted while acquiring lock");
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }

    private void doPublish() {
        List<OutboxRow> rows = outboxRepo.findUnsent(PageRequest.of(0, BATCH_SIZE));
        if (rows.isEmpty()) return;

        for (OutboxRow row : rows) {
            if (row.exceededMaxAttempts(MAX_ATTEMPTS)) {
                continue;
            }
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        row.getTopic(), row.getEventId().toString(), row.getPayload());

                if (row.getAttemptsContext() != null) {
                    record.headers().add("attempts",
                            String.valueOf(row.getAttemptsContext()).getBytes(StandardCharsets.UTF_8));
                }
                if (row.getReason() != null) {
                    record.headers().add("reason",
                            row.getReason().getBytes(StandardCharsets.UTF_8));
                }
                if (row.getTraceId() != null) {
                    record.headers().add("traceId",
                            row.getTraceId().getBytes(StandardCharsets.UTF_8));
                }

                kafkaTemplate.send(record).get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                outboxService.markPublished(row.getId());
                metrics.recordProduced();
            } catch (Exception e) {
                outboxService.recordFailure(row.getId());
                log.warn("publisher: row {} attempt {} failed — will retry next run",
                        row.getId(), row.getAttempts() + 1, e);
                return;
            }
        }
    }
}