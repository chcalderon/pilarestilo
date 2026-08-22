package com.pilarestilo.discount.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What the coupon just issued to a new account looks like, for the welcome email and in-app note. */
public record WelcomeDiscountDto(
        String code,
        String type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        LocalDate validUntil
) {}
