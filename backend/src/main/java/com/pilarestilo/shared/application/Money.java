package com.pilarestilo.shared.application;

import com.pilarestilo.shared.domain.DomainException;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    public static final String DEFAULT_CURRENCY = "CLP";

    public Money {
        Objects.requireNonNull(amount, "Money amount cannot be null");
        Objects.requireNonNull(currency, "Money currency cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("Money amount cannot be negative");
        }
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO, DEFAULT_CURRENCY);
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("Subtraction would result in negative money amount");
        }
        return new Money(result, this.currency);
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new DomainException("Money factor cannot be negative");
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)), this.currency);
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new DomainException("Cannot operate on Money with different currencies: "
                    + this.currency + " vs " + other.currency);
        }
    }
}
