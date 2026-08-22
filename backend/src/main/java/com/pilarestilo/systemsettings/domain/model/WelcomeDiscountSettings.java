package com.pilarestilo.systemsettings.domain.model;

import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.shared.domain.DomainException;

import java.math.BigDecimal;

/**
 * The shop's own rule for the coupon a new account gets, if any.
 *
 * <p>Vigencia is deliberately not a field here: 30 days is fixed in
 * {@code IssueWelcomeDiscountUseCase}, not a knob the owner asked for. Everything else — whether
 * it runs at all, the discount shape, and whether it requires marketing consent — is.
 */
public record WelcomeDiscountSettings(
        boolean enabled,
        String type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        boolean requiresMarketingConsent
) {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /** What a shop that has never opened this section gets: off, safe defaults underneath. */
    public static WelcomeDiscountSettings disabled() {
        return new WelcomeDiscountSettings(false, DiscountType.PERCENTAGE.name(), BigDecimal.TEN,
                BigDecimal.ZERO, true);
    }

    public static WelcomeDiscountSettings of(
            Boolean enabled,
            String type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            Boolean requiresMarketingConsent
    ) {
        boolean isEnabled = Boolean.TRUE.equals(enabled);
        String normalizedType = type == null || type.isBlank()
                ? DiscountType.PERCENTAGE.name()
                : parseType(type).name();
        BigDecimal normalizedValue = value == null ? BigDecimal.TEN : value;
        BigDecimal normalizedMinOrder = minOrderAmount == null ? BigDecimal.ZERO : minOrderAmount;

        if (isEnabled) {
            if (normalizedValue.compareTo(BigDecimal.ZERO) <= 0) {
                throw new DomainException("Welcome discount value must be positive");
            }
            if (DiscountType.PERCENTAGE.name().equals(normalizedType)
                    && normalizedValue.compareTo(ONE_HUNDRED) > 0) {
                throw new DomainException("Welcome discount percentage cannot exceed 100");
            }
            if (normalizedMinOrder.compareTo(BigDecimal.ZERO) < 0) {
                throw new DomainException("Welcome discount minimum order amount cannot be negative");
            }
        }

        return new WelcomeDiscountSettings(isEnabled, normalizedType, normalizedValue,
                normalizedMinOrder, requiresMarketingConsent == null || requiresMarketingConsent);
    }

    private static DiscountType parseType(String type) {
        try {
            return DiscountType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException _) {
            throw new DomainException("Unsupported welcome discount type: " + type);
        }
    }
}
