package com.pilarestilo.publication.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PublicationDispatchRequested(UUID publicationId, int attemptNumber, Instant occurredAt) implements DomainEvent {
    public PublicationDispatchRequested(UUID publicationId, int attemptNumber) {
        this(publicationId, attemptNumber, Instant.now());
    }
}
