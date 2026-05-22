package com.pilarestilo.publication.domain.events;

import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PublicationDraftCreated(
        UUID publicationId,
        PublicationPlatform platform,
        PublicationChannelType channelType,
        PublicationSourceType sourceType,
        Instant occurredAt
) implements DomainEvent {
    public PublicationDraftCreated(UUID publicationId,
                                   PublicationPlatform platform,
                                   PublicationChannelType channelType,
                                   PublicationSourceType sourceType) {
        this(publicationId, platform, channelType, sourceType, Instant.now());
    }
}
