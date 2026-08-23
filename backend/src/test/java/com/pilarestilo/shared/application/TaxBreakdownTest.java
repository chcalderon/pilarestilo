package com.pilarestilo.shared.application;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxBreakdownTest {

    private static final BigDecimal IVA = new BigDecimal("19.00");

    @Test
    void splits_a_gross_amount_into_net_and_tax() {
        TaxBreakdown breakdown = TaxBreakdown.fromGross(clp("45990"), IVA);

        assertEquals(clp("38647"), breakdown.net());
        assertEquals(clp("7343"), breakdown.tax());
        assertEquals(clp("45990"), breakdown.total());
        assertEquals(IVA, breakdown.rate());
    }

    /**
     * The reason the tax is a subtraction and not a second multiplication. 38647 * 0.19 is 7342.93,
     * which rounds to 7343 here but would not for every amount, and a boleta whose net and IVA do
     * not add up to its total is a boleta the SII rejects.
     */
    @Test
    void net_and_tax_always_add_up_to_the_total() {
        for (int total = 0; total <= 5000; total++) {
            TaxBreakdown breakdown = TaxBreakdown.fromGross(clp(String.valueOf(total)), IVA);
            assertEquals(
                    breakdown.total().amount(),
                    breakdown.net().amount().add(breakdown.tax().amount()),
                    "net + tax must equal total for gross " + total);
        }
    }

    @Test
    void rounds_half_up() {
        // 3 / 2 is exactly 1.5, the only case where the rounding mode is visible.
        TaxBreakdown breakdown = TaxBreakdown.fromGross(clp("3"), new BigDecimal("100.00"));

        assertEquals(clp("2"), breakdown.net());
        assertEquals(clp("1"), breakdown.tax());
    }

    @Test
    void a_zero_rate_leaves_the_whole_amount_as_net() {
        TaxBreakdown breakdown = TaxBreakdown.fromGross(clp("45990"), BigDecimal.ZERO);

        assertEquals(clp("45990"), breakdown.net());
        assertEquals(clp("0"), breakdown.tax());
    }

    @Test
    void a_zero_total_breaks_down_to_zero() {
        TaxBreakdown breakdown = TaxBreakdown.fromGross(clp("0"), IVA);

        assertEquals(clp("0"), breakdown.net());
        assertEquals(clp("0"), breakdown.tax());
    }

    @Test
    void keeps_the_currency_of_the_gross_amount() {
        TaxBreakdown breakdown = TaxBreakdown.fromGross(Money.of(new BigDecimal("119"), "USD"), IVA);

        assertEquals("USD", breakdown.net().currency());
        assertEquals("USD", breakdown.tax().currency());
    }

    @Test
    void rejects_a_null_total() {
        assertThrows(DomainException.class, () -> TaxBreakdown.fromGross(null, IVA));
    }

    @Test
    void rejects_a_null_rate() {
        Money total = clp("1000");

        assertThrows(DomainException.class, () -> TaxBreakdown.fromGross(total, null));
    }

    @Test
    void rejects_a_negative_rate() {
        Money total = clp("1000");
        BigDecimal rate = new BigDecimal("-1");

        assertThrows(DomainException.class, () -> TaxBreakdown.fromGross(total, rate));
    }

    @Test
    void rejects_a_rate_above_one_hundred_percent() {
        Money total = clp("1000");
        BigDecimal rate = new BigDecimal("1000");

        assertThrows(DomainException.class, () -> TaxBreakdown.fromGross(total, rate));
    }

    private static Money clp(String amount) {
        return Money.of(new BigDecimal(amount));
    }
}
