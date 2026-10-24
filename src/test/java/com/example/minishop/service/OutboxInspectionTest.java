package com.example.minishop.service;

import com.example.minishop.domain.OutboxRow;
import com.example.minishop.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class OutboxInspectionTest {

    @Autowired
    private OutboxRepository outboxRepo;

    @Test
    void print_outbox_state() {
        long total = outboxRepo.count();
        long unsent = outboxRepo.findUnsent(org.springframework.data.domain.PageRequest.of(0, 100)).size();
        System.out.println("=== OUTBOX INSPECTION ===");
        System.out.println("Total outbox rows: " + total);
        System.out.println("Unsent rows: " + unsent);
        for (OutboxRow row : outboxRepo.findAll()) {
            System.out.println("  event=" + row.getEventId()
                    + " attempts=" + row.getAttempts()
                    + " publishedAt=" + row.getPublishedAt());
        }
    }
}