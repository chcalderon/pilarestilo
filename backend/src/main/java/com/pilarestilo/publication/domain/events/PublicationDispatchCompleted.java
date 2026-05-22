package com.pilarestilo.publication.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PublicationDispatchCompleted(UUID publicationId, int attemptNumber, String remotePostId, Instant occurredAt) implements DomainEvent {
    public PublicationDispatchCompleted(UUID publicationId, int attemptNumber, String remotePostId) {
        this(publicationId, attemptNumber, remotePostId, Instant.now());
    }
}
