package com.pilarestilo.publication.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PublicationSubmittedForReview(UUID publicationId, Instant occurredAt) implements DomainEvent {
    public PublicationSubmittedForReview(UUID publicationId) {
        this(publicationId, Instant.now());
    }
}
