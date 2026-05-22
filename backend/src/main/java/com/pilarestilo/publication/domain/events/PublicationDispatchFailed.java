package com.pilarestilo.publication.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PublicationDispatchFailed(UUID publicationId, int attemptNumber, String errorCode, Instant occurredAt) implements DomainEvent {
    public PublicationDispatchFailed(UUID publicationId, int attemptNumber, String errorCode) {
        this(publicationId, attemptNumber, errorCode, Instant.now());
    }
}
