package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.util.List;
import java.util.UUID;

public record PublishProductsBatchResult(
        List<PublicationItemResult> items
) {
    public record PublicationItemResult(
            UUID productId,
            PublicationPlatform platform,
            boolean success,
            UUID publicationId,
            String errorMessage,
            boolean scheduled
    ) {}
}
