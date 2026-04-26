package com.pilarestilo.discount.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DiscountDto(
        UUID id,
        String code,
        String type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        String minOrderCurrency,
        LocalDate validFrom,
        LocalDate validUntil,
        int maxUses,
        int timesUsed,
        boolean active,
        UUID assignedUserId,
        String assignedUserName,
        String assignedUserEmail
) {}
