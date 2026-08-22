package com.pilarestilo.user.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserRegistered(
        UUID userId,
        Instant occurredAt,
        WelcomeDiscount welcomeDiscount
) implements DomainEvent {

    public UserRegistered(UUID userId, Instant occurredAt) {
        this(userId, occurredAt, null);
    }

    /**
     * Primitive-typed on purpose: {@code user} stays free of a dependency on the {@code discount}
     * module's enum. Null means no coupon was issued (feature off, or marketing consent required
     * and not given) — the composer treats that the same as a plain welcome message.
     */
    public record WelcomeDiscount(
            String code,
            String type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            LocalDate validUntil
    ) {}
}
