package com.pilarestilo.cashregister.application.dto;

import com.pilarestilo.cashregister.domain.model.CashMovement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CashMovementDto(
        UUID id, String type, String category, BigDecimal amount, String description,
        UUID orderId, LocalDateTime recordedAt, UUID recordedBy
) {
    public static CashMovementDto from(CashMovement m) {
        return new CashMovementDto(
                m.getId(),
                m.getType().name(),
                m.getCategory() != null ? m.getCategory().name() : null,
                m.getAmount(),
                m.getDescription(),
                m.getOrderId(),
                m.getRecordedAt(),
                m.getRecordedBy());
    }
}
