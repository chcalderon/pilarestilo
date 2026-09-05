package com.pilarestilo.publication.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record PublishProductsBatchRequest(
        @NotEmpty List<UUID> productIds,
        @NotEmpty List<@NotBlank String> platforms,
        @NotBlank String captionTemplate,
        List<String> hashtags,
        String campaignLabel
) {}
