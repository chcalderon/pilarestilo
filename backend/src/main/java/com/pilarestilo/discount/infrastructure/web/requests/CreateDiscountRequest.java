package com.pilarestilo.discount.infrastructure.web.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateDiscountRequest(
        @NotBlank(message = "Code is required")
        String code,

        @NotBlank(message = "Type is required")
        String type,

        @NotNull(message = "Value is required")
        @DecimalMin(value = "0.01", message = "Value must be positive")
        BigDecimal value,

        BigDecimal minOrderAmount,

        @NotNull(message = "validFrom is required")
        LocalDate validFrom,

        @NotNull(message = "validUntil is required")
        LocalDate validUntil,

        @Min(value = 1, message = "maxUses must be at least 1")
        int maxUses
) {}
