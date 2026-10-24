package com.example.minishop.service;

import com.example.minishop.domain.OutboxRow;
import com.example.minishop.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final OutboxRepository outboxRepo;

    public OutboxService(OutboxRepository outboxRepo) {
        this.outboxRepo = outboxRepo;
    }

    @Transactional
    public void markPublished(long rowId) {
        OutboxRow row = outboxRepo.findById(rowId)
                .orElseThrow(() -> new IllegalStateException("Outbox row not found: " + rowId));
        row.markPublished();
        outboxRepo.save(row);
    }

    @Transactional
    public void recordFailure(long rowId) {
        OutboxRow row = outboxRepo.findById(rowId)
                .orElseThrow(() -> new IllegalStateException("Outbox row not found: " + rowId));
        row.recordFailure();
        outboxRepo.save(row);
    }
}