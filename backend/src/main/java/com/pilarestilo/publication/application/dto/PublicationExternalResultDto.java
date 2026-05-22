package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;

import java.time.Instant;
import java.util.UUID;

public record PublicationExternalResultDto(
        UUID publicationId,
        int attemptNumber,
        PublicationAttemptStatus status,
        String remotePostId,
        Instant publishedAt
) {
}
