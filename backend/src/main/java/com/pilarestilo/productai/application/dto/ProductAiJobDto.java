package com.pilarestilo.productai.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductAiJobDto(
        UUID jobId,
        UUID draftId,
        String status,
        int progress,
        int attempt,
        int maxAttempts,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        List<ProductAiJobItemDto> items
) {
}
