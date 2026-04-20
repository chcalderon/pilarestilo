package com.pilarestilo.discount.infrastructure.persistence.entities;

import com.pilarestilo.discount.domain.enums.DiscountType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "discounts")
public class DiscountEntity {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal value;

    @Column(name = "min_order_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "min_order_currency", nullable = false, length = 10)
    private String minOrderCurrency;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "times_used", nullable = false)
    private int timesUsed;

    @Column(nullable = false)
    private boolean active;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public DiscountType getType() { return type; }
    public void setType(DiscountType type) { this.type = type; }

    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }

    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }

    public String getMinOrderCurrency() { return minOrderCurrency; }
    public void setMinOrderCurrency(String minOrderCurrency) { this.minOrderCurrency = minOrderCurrency; }

    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }

    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }

    public int getTimesUsed() { return timesUsed; }
    public void setTimesUsed(int timesUsed) { this.timesUsed = timesUsed; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
