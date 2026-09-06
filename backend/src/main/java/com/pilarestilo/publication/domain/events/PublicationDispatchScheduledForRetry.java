package com.pilarestilo.publication.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PublicationDispatchScheduledForRetry(
        UUID publicationId, int retryCount, Instant nextAttemptAt, Instant occurredAt) implements DomainEvent {

    public PublicationDispatchScheduledForRetry(UUID publicationId, int retryCount, Instant nextAttemptAt) {
        this(publicationId, retryCount, nextAttemptAt, Instant.now());
    }
}
