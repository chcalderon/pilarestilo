package com.pilarestilo.cashregister.infrastructure.persistence.entities;

import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_movements")
public class CashMovementEntity {
    @Id private UUID id;
    @Column(name = "cash_register_id", nullable = false) private UUID cashRegisterId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private CashMovementType type;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private String description;
    @Column(name = "order_id") private UUID orderId;
    @Column(name = "recorded_at", nullable = false) private LocalDateTime recordedAt;
    @Column(name = "recorded_by", nullable = false) private UUID recordedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCashRegisterId() { return cashRegisterId; }
    public void setCashRegisterId(UUID cashRegisterId) { this.cashRegisterId = cashRegisterId; }
    public CashMovementType getType() { return type; }
    public void setType(CashMovementType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
    public UUID getRecordedBy() { return recordedBy; }
    public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }
}
