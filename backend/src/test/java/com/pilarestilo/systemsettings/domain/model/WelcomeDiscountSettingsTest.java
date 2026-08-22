package com.pilarestilo.systemsettings.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WelcomeDiscountSettingsTest {

    @Test
    void disabled_is_off_with_safe_defaults() {
        WelcomeDiscountSettings settings = WelcomeDiscountSettings.disabled();

        assertFalse(settings.enabled());
        assertTrue(settings.requiresMarketingConsent());
    }

    @Test
    void of_normalizes_a_valid_percentage_configuration() {
        WelcomeDiscountSettings settings = WelcomeDiscountSettings.of(
                true, "PERCENTAGE", BigDecimal.TEN, BigDecimal.ZERO, true);

        assertTrue(settings.enabled());
        assertEquals("PERCENTAGE", settings.type());
        assertEquals(0, BigDecimal.TEN.compareTo(settings.value()));
    }

    @Test
    void of_rejects_a_percentage_over_100() {
        assertThrows(DomainException.class, () -> WelcomeDiscountSettings.of(
                true, "PERCENTAGE", BigDecimal.valueOf(150), BigDecimal.ZERO, true));
    }

    @Test
    void of_rejects_a_zero_or_negative_value_when_enabled() {
        assertThrows(DomainException.class, () -> WelcomeDiscountSettings.of(
                true, "PERCENTAGE", BigDecimal.ZERO, BigDecimal.ZERO, true));
    }

    @Test
    void of_rejects_a_negative_minimum_order_amount() {
        assertThrows(DomainException.class, () -> WelcomeDiscountSettings.of(
                true, "PERCENTAGE", BigDecimal.TEN, BigDecimal.valueOf(-1), true));
    }

    @Test
    void of_allows_a_disabled_configuration_with_no_value() {
        WelcomeDiscountSettings settings = WelcomeDiscountSettings.of(
                false, null, null, null, true);

        assertFalse(settings.enabled());
    }
}
