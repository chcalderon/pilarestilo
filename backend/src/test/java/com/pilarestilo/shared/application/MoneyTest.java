package com.pilarestilo.shared.application;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void add_two_money_values() {
        Money result = Money.of(BigDecimal.valueOf(100)).add(Money.of(BigDecimal.valueOf(200)));
        assertEquals(Money.of(BigDecimal.valueOf(300)), result);
    }

    @Test
    void subtract_money() {
        Money result = Money.of(BigDecimal.valueOf(300)).subtract(Money.of(BigDecimal.valueOf(100)));
        assertEquals(Money.of(BigDecimal.valueOf(200)), result);
    }

    @Test
    void subtract_to_zero() {
        Money result = Money.of(BigDecimal.valueOf(100)).subtract(Money.of(BigDecimal.valueOf(100)));
        assertEquals(Money.zero(), result);
    }

    @Test
    void throws_on_negative_amount() {
        BigDecimal negative = BigDecimal.valueOf(-1);

        assertThrows(DomainException.class, () -> Money.of(negative));
    }

    @Test
    void throws_on_subtract_resulting_in_negative() {
        Money fifty = Money.of(BigDecimal.valueOf(50));
        Money oneHundred = Money.of(BigDecimal.valueOf(100));

        assertThrows(DomainException.class, () -> fifty.subtract(oneHundred));
    }

    @Test
    void multiply_by_factor() {
        Money result = Money.of(BigDecimal.valueOf(100)).multiply(3);
        assertEquals(Money.of(BigDecimal.valueOf(300)), result);
    }

    @Test
    void throws_on_different_currencies() {
        Money clp = Money.of(BigDecimal.valueOf(100), "CLP");
        Money usd = Money.of(BigDecimal.valueOf(100), "USD");
        assertThrows(DomainException.class, () -> clp.add(usd));
    }
}
