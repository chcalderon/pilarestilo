package com.pilarestilo.dispatch.application.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DispatchHistoryRowDto(
        UUID id,
        UUID orderId,
        UUID dispatcherId,
        String dispatchedBy,
        String status,
        String carrier,
        String trackingCode,
        LocalDate scheduledDate,
        LocalDateTime dispatchedAt,
        LocalDateTime deliveredAt,
        String notes,
        LocalDateTime createdAt,
        Instant orderCreatedAt,
        String soldBy
) {
}
