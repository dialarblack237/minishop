package com.example.minishop.service;

import java.util.UUID;

public class DuplicateEvent extends RuntimeException {

    private final UUID eventId;

    public DuplicateEvent(UUID eventId) {
        super("Duplicate event: " + eventId);
        this.eventId = eventId;
    }

    public UUID getEventId() {
        return eventId;
    }
}