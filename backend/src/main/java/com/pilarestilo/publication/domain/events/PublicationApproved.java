package com.pilarestilo.publication.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PublicationApproved(UUID publicationId, Instant occurredAt) implements DomainEvent {
    public PublicationApproved(UUID publicationId) {
        this(publicationId, Instant.now());
    }
}
