package com.pilarestilo.orderservice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The same vectors as {@code TaxBreakdownTest} in the monolith. Both codebases write the tax columns
 * of the same {@code orders} row, so these two files disagreeing means production rows disagree.
 */
class TaxBreakdownTest {

    private static final BigDecimal IVA = new BigDecimal("19.00");

    @Test
    void splits_a_gross_amount_into_net_and_tax() {
        TaxBreakdown breakdown = TaxBreakdown.fromGross(new BigDecimal("45990.00"), IVA);

        assertEquals(0, breakdown.net().compareTo(new BigDecimal("38647")));
        assertEquals(0, breakdown.tax().compareTo(new BigDecimal("7343")));
        assertEquals(0, breakdown.total().compareTo(new BigDecimal("45990")));
    }

    @Test
    void net_and_tax_always_add_up_to_the_total() {
        for (int total = 0; total <= 5000; total++) {
            TaxBreakdown breakdown = TaxBreakdown.fromGross(BigDecimal.valueOf(total), IVA);
            assertEquals(
                    0,
                    breakdown.total().compareTo(breakdown.net().add(breakdown.tax())),
                    "net + tax must equal total for gross " + total);
        }
    }

    @Test
    void rounds_half_up() {
        TaxBreakdown breakdown = TaxBreakdown.fromGross(new BigDecimal("3"), new BigDecimal("100.00"));

        assertEquals(0, breakdown.net().compareTo(new BigDecimal("2")));
        assertEquals(0, breakdown.tax().compareTo(BigDecimal.ONE));
    }

    @Test
    void a_missing_rate_falls_back_to_the_default() {
        TaxBreakdown breakdown = TaxBreakdown.fromGross(new BigDecimal("45990"), null);

        assertEquals(TaxBreakdown.DEFAULT_RATE, breakdown.rate());
        assertEquals(0, breakdown.net().compareTo(new BigDecimal("38647")));
    }

    @Test
    void a_zero_rate_leaves_the_whole_amount_as_net() {
        TaxBreakdown breakdown = TaxBreakdown.fromGross(new BigDecimal("45990"), BigDecimal.ZERO);

        assertEquals(0, breakdown.net().compareTo(new BigDecimal("45990")));
        assertEquals(0, breakdown.tax().compareTo(BigDecimal.ZERO));
    }

    @Test
    void rejects_a_rate_outside_zero_to_one_hundred() {
        assertThrows(IllegalArgumentException.class,
                () -> TaxBreakdown.fromGross(new BigDecimal("1000"), new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> TaxBreakdown.fromGross(new BigDecimal("1000"), new BigDecimal("101")));
    }
}
