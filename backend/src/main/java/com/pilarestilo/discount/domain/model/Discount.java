package com.pilarestilo.discount.domain.model;

import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class Discount {

    private UUID id;
    private String code;
    private DiscountType type;
    private BigDecimal value;
    private Money minOrderAmount;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private int maxUses;
    private int timesUsed;
    private boolean active;

    private Discount() {}

    public static Discount create(String code, DiscountType type, BigDecimal value,
                                   Money minOrderAmount, LocalDate validFrom,
                                   LocalDate validUntil, int maxUses) {
        if (code == null || code.isBlank()) {
            throw new DomainException("Discount code cannot be blank");
        }
        if (type == null) {
            throw new DomainException("Discount type cannot be null");
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Discount value must be positive");
        }
        if (type == DiscountType.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new DomainException("Percentage discount cannot exceed 100");
        }
        if (validFrom == null || validUntil == null) {
            throw new DomainException("Validity dates cannot be null");
        }
        if (validFrom.isAfter(validUntil)) {
            throw new DomainException("validFrom cannot be after validUntil");
        }
        if (maxUses <= 0) {
            throw new DomainException("Max uses must be positive");
        }

        Discount discount = new Discount();
        discount.id = UUID.randomUUID();
        discount.code = code.trim().toUpperCase();
        discount.type = type;
        discount.value = value;
        discount.minOrderAmount = minOrderAmount != null ? minOrderAmount : Money.zero();
        discount.validFrom = validFrom;
        discount.validUntil = validUntil;
        discount.maxUses = maxUses;
        discount.timesUsed = 0;
        discount.active = true;
        return discount;
    }

    public void validate(Money subtotal) {
        if (!active) {
            throw new DomainException("Discount not active");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(validFrom) || today.isAfter(validUntil)) {
            throw new DomainException("Discount expired or not yet valid");
        }
        if (timesUsed >= maxUses) {
            throw new DomainException("Discount usage limit reached");
        }
        if (subtotal.amount().compareTo(minOrderAmount.amount()) < 0) {
            throw new DomainException("Order subtotal below minimum required for this discount");
        }
    }

    public Money apply(Money subtotal) {
        validate(subtotal);
        timesUsed++;

        if (type == DiscountType.PERCENTAGE) {
            return Money.of(subtotal.amount()
                    .multiply(value)
                    .divide(BigDecimal.valueOf(100)));
        } else {
            BigDecimal discountAmt = value.min(subtotal.amount());
            return Money.of(discountAmt);
        }
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public DiscountType getType() { return type; }
    public BigDecimal getValue() { return value; }
    public Money getMinOrderAmount() { return minOrderAmount; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public int getMaxUses() { return maxUses; }
    public int getTimesUsed() { return timesUsed; }
    public boolean isActive() { return active; }

    public void setId(UUID id) { this.id = id; }
    public void setTimesUsed(int timesUsed) { this.timesUsed = timesUsed; }
    public void setActive(boolean active) { this.active = active; }
}
