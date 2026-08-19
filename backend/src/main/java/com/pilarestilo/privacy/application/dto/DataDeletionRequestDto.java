package com.pilarestilo.privacy.application.dto;

import com.pilarestilo.privacy.domain.model.DataDeletionRequest;

import java.time.Instant;
import java.util.UUID;

public record DataDeletionRequestDto(
        UUID id,
        UUID userId,
        String status,
        String reason,
        Instant requestedAt,
        Instant resolvedAt,
        UUID resolvedBy,
        String resolution
) {
    public static DataDeletionRequestDto from(DataDeletionRequest request) {
        return new DataDeletionRequestDto(
                request.getId(),
                request.getUserId(),
                request.getStatus().name(),
                request.getReason(),
                request.getRequestedAt(),
                request.getResolvedAt(),
                request.getResolvedBy(),
                request.getResolution());
    }
}
