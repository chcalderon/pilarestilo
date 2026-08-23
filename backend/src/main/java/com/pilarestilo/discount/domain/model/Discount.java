package com.pilarestilo.discount.domain.model;

import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public class Discount {

    /** Validity is checked against the shop's own calendar day, not whatever zone the server runs in. */
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Santiago");

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
    private UUID assignedUserId;

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
        LocalDate today = LocalDate.now(STORE_ZONE);
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

    /**
     * Computes the discount without consuming it.
     *
     * <p>Renamed from {@code apply} and stripped of its {@code timesUsed++}: the counter now moves
     * in {@code DiscountRedemptionRepository}, where the increment is atomic with the ledger row
     * under a row lock. Fusing validation and mutation is what made an abandoned order burn a code
     * permanently — the increment happened at order creation and nothing reversed it.
     *
     * <p>The rename is deliberate. A silently-changed {@code apply} would have compiled at every
     * call site.
     */
    public Money computeDiscountFor(Money subtotal) {
        validate(subtotal);

        if (type == DiscountType.PERCENTAGE) {
            /*
             * Rounded to whole pesos. The Chilean peso has no subunit, so an unrounded
             * quotient invents a value that cannot be paid: 10% of 33.333 is 3.333,30, and
             * the order would be settled at 29.999,70 while the customer was shown 30.000.
             * HALF_UP matches the storefront's Math.round for the positive amounts this
             * ever sees, which is what keeps the two totals identical.
             */
            return Money.of(subtotal.amount()
                    .multiply(value)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP));
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

    public UUID getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(UUID assignedUserId) { this.assignedUserId = assignedUserId; }
}
