package com.pilarestilo.dispatch.application.dto;

import com.pilarestilo.dispatch.domain.model.Dispatch;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DispatchDto(
        UUID id, UUID orderId, UUID dispatcherId, String status,
        String carrier, String trackingCode, LocalDate scheduledDate,
        LocalDateTime dispatchedAt, LocalDateTime deliveredAt,
        String notes, LocalDateTime createdAt
) {
    public static DispatchDto from(Dispatch d) {
        return new DispatchDto(d.getId(), d.getOrderId(), d.getDispatcherId(), d.getStatus().name(),
                d.getCarrier(), d.getTrackingCode(), d.getScheduledDate(),
                d.getDispatchedAt(), d.getDeliveredAt(), d.getNotes(), d.getCreatedAt());
    }
}
