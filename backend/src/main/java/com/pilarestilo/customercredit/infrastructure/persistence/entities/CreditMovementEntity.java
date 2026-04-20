package com.pilarestilo.customercredit.infrastructure.persistence.entities;

import com.pilarestilo.customercredit.domain.enums.CreditMovementType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_movements")
public class CreditMovementEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreditMovementType type;

    @Column(name = "amount_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountValue;

    @Column(name = "amount_currency", nullable = false, length = 10)
    private String amountCurrency;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public CreditMovementType getType() { return type; }
    public void setType(CreditMovementType type) { this.type = type; }

    public BigDecimal getAmountValue() { return amountValue; }
    public void setAmountValue(BigDecimal amountValue) { this.amountValue = amountValue; }

    public String getAmountCurrency() { return amountCurrency; }
    public void setAmountCurrency(String amountCurrency) { this.amountCurrency = amountCurrency; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
