package com.pilarestilo.productai.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ProductAiDraftDto(
        UUID draftId,
        UUID productId,
        String status,
        Instant createdAt
) {
}
