package com.pilarestilo.productai.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ProductAiPublishResultDto(
        UUID productId,
        String status,
        Instant publishedAt
) {
}
