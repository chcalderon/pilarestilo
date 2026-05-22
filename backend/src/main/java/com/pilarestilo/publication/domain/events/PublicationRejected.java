package com.pilarestilo.publication.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PublicationRejected(UUID publicationId, Instant occurredAt) implements DomainEvent {
    public PublicationRejected(UUID publicationId) {
        this(publicationId, Instant.now());
    }
}
