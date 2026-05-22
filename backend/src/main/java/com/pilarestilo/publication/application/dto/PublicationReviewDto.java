package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationReviewAction;

import java.time.Instant;
import java.util.UUID;

public record PublicationReviewDto(
        UUID id,
        PublicationReviewAction action,
        UUID actorUserId,
        String comment,
        Instant createdAt
) {
}
