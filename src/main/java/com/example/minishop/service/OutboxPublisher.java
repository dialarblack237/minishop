package com.example.minishop.service;

import com.example.minishop.domain.OutboxRow;
import com.example.minishop.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 5;
    private static final long KAFKA_TIMEOUT_SECONDS = 5;

    private final OutboxRepository outboxRepo;
    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepo,
                           OutboxService outboxService,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepo = outboxRepo;
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    public void publishUnsent() {
        List<OutboxRow> rows = outboxRepo.findUnsent(PageRequest.of(0, BATCH_SIZE));
        if (rows.isEmpty()) return;

        for (OutboxRow row : rows) {
            if (row.exceededMaxAttempts(MAX_ATTEMPTS)) {
                continue;
            }
            try {
                kafkaTemplate.send(row.getTopic(), row.getEventId().toString(), row.getPayload())
                        .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                outboxService.markPublished(row.getId());
            } catch (Exception e) {
                outboxService.recordFailure(row.getId());
                log.warn("publisher: row {} attempt {} failed — will retry next run",
                        row.getId(), row.getAttempts() + 1, e);
                return;
            }
        }
    }
}