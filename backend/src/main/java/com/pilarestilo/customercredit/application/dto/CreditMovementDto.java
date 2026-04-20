package com.pilarestilo.customercredit.application.dto;

import com.pilarestilo.customercredit.domain.enums.CreditMovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditMovementDto(
        UUID id,
        UUID customerId,
        CreditMovementType type,
        BigDecimal amount,
        String currency,
        String reason,
        Instant createdAt
) {}
