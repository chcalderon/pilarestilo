package com.pilarestilo.productai.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ProductAiJobSummaryDto(
        UUID jobId,
        UUID draftId,
        String status,
        int progress,
        int attempt,
        int maxAttempts,
        Instant updatedAt
) {
}
