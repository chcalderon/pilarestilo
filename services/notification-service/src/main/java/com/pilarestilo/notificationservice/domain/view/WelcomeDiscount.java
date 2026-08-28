package com.pilarestilo.notificationservice.domain.view;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The welcome coupon carried on {@code UserRegistered}. Primitive-typed on purpose — no dependency
 * on the discount module's enum. Null (not an instance) means no coupon was issued.
 */
public record WelcomeDiscount(
        String code,
        String type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        LocalDate validUntil
) {}
