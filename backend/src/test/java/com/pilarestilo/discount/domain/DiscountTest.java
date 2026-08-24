package com.pilarestilo.discount.domain;

import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class DiscountTest {

    /**
     * Discount.validate() checks "today" against America/Santiago, not the JVM's default zone.
     * Fixtures built from the system default drift a calendar day from that near UTC midnight --
     * exactly the multi-hour window this repo's CI runs in -- so every relative date here has to
     * be anchored to the same zone the domain uses.
     */
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Santiago");

    private Discount buildPercentageDiscount(int maxUses) {
        return Discount.create(
                "SAVE10",
                DiscountType.PERCENTAGE,
                BigDecimal.valueOf(10),
                Money.zero(),
                LocalDate.now(STORE_ZONE).minusDays(1),
                LocalDate.now(STORE_ZONE).plusDays(30),
                maxUses
        );
    }

    private Discount buildFixedDiscount() {
        return Discount.create(
                "FIXED5000",
                DiscountType.FIXED,
                BigDecimal.valueOf(5000),
                Money.zero(),
                LocalDate.now(STORE_ZONE).minusDays(1),
                LocalDate.now(STORE_ZONE).plusDays(30),
                10
        );
    }

    @Test
    void percentage_discount_computed_correctly() {
        Discount d = buildPercentageDiscount(5);
        Money subtotal = Money.of(BigDecimal.valueOf(100000));
        Money discountAmount = d.computeDiscountFor(subtotal);
        assertEquals(BigDecimal.valueOf(10000).stripTrailingZeros(),
                discountAmount.amount().stripTrailingZeros());
    }

    /**
     * The storefront computes the same figure with {@code Math.round} to show the customer a
     * total before the order exists. An unrounded quotient here made the two disagree: the
     * customer saw 30.000 and the order was settled at 29.999,70. CLP has no subunit, so the
     * fractional value was never payable in the first place.
     */
    @Test
    void percentage_discount_rounds_to_whole_pesos() {
        Discount d = buildPercentageDiscount(5);
        Money discountAmount = d.computeDiscountFor(Money.of(BigDecimal.valueOf(33333)));

        assertEquals(0, discountAmount.amount().scale(),
                "a fractional peso cannot be charged");
        assertEquals(0, BigDecimal.valueOf(3333).compareTo(discountAmount.amount()));
    }

    @Test
    void percentage_discount_rounds_half_up_like_the_storefront() {
        Discount d = buildPercentageDiscount(5);
        // 10% of 33.335 is 3.333,50 — both sides must land on 3.334, never 3.333.
        Money discountAmount = d.computeDiscountFor(Money.of(BigDecimal.valueOf(33335)));

        assertEquals(0, BigDecimal.valueOf(3334).compareTo(discountAmount.amount()));
    }

    @Test
    void fixed_discount_returned_correctly() {
        Discount d = buildFixedDiscount();
        Money subtotal = Money.of(BigDecimal.valueOf(50000));
        Money discountAmount = d.computeDiscountFor(subtotal);
        assertEquals(BigDecimal.valueOf(5000).stripTrailingZeros(),
                discountAmount.amount().stripTrailingZeros());
    }

    @Test
    void fixed_discount_capped_at_subtotal() {
        Discount d = buildFixedDiscount();
        Money subtotal = Money.of(BigDecimal.valueOf(3000));
        Money discountAmount = d.computeDiscountFor(subtotal);
        assertEquals(BigDecimal.valueOf(3000).stripTrailingZeros(),
                discountAmount.amount().stripTrailingZeros());
    }

    @Test
    void throws_when_expired() {
        Discount d = Discount.create(
                "EXPIRED",
                DiscountType.PERCENTAGE,
                BigDecimal.valueOf(10),
                Money.zero(),
                LocalDate.now(STORE_ZONE).minusDays(30),
                LocalDate.now(STORE_ZONE).minusDays(1),
                5
        );
        Money subtotal = Money.of(BigDecimal.valueOf(10000));
        assertThrows(DomainException.class, () -> d.validate(subtotal));
    }

    @Test
    void throws_when_usage_limit_reached() {
        Discount d = buildPercentageDiscount(1);
        Money subtotal = Money.of(BigDecimal.valueOf(10000));
        d.setTimesUsed(1); // the slot is claimed in DiscountRedemptionRepository, not by the aggregate
        assertThrows(DomainException.class, () -> d.computeDiscountFor(subtotal));
    }

    @Test
    void throws_when_below_minimum_order() {
        Discount d = Discount.create(
                "MIN50K",
                DiscountType.PERCENTAGE,
                BigDecimal.valueOf(10),
                Money.of(BigDecimal.valueOf(50000)),
                LocalDate.now(STORE_ZONE).minusDays(1),
                LocalDate.now(STORE_ZONE).plusDays(30),
                10
        );
        Money subtotal = Money.of(BigDecimal.valueOf(30000));
        assertThrows(DomainException.class, () -> d.validate(subtotal));
    }

    @Test
    void throws_when_inactive() {
        Discount d = buildPercentageDiscount(5);
        d.deactivate();
        Money subtotal = Money.of(BigDecimal.valueOf(10000));

        assertThrows(DomainException.class, () -> d.validate(subtotal));
    }
}
