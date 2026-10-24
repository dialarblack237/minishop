package com.example.minishop.service;

import com.example.minishop.domain.ProcessedEvent;
import com.example.minishop.repository.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DedupeService {

    private final ProcessedEventRepository repo;

    public DedupeService(ProcessedEventRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void recordIfNew(UUID eventId, String eventType) {
        try {
            repo.saveAndFlush(new ProcessedEvent(eventId, eventType));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEvent(eventId);
        }
    }
}