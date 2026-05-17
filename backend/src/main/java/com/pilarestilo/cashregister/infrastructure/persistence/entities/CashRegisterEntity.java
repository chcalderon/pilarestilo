package com.pilarestilo.cashregister.infrastructure.persistence.entities;

import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_registers")
public class CashRegisterEntity {
    @Id private UUID id;
    @Version private Long version;
    @Column(name = "seller_id", nullable = false) private UUID sellerId;
    @Column(name = "opened_at", nullable = false) private LocalDateTime openedAt;
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(name = "opening_balance", nullable = false) private BigDecimal openingBalance;
    @Column(name = "closing_balance") private BigDecimal closingBalance;
    @Column(name = "expected_balance") private BigDecimal expectedBalance;
    @Column private BigDecimal difference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private CashRegisterStatus status;
    @Column private String notes;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public UUID getSellerId() { return sellerId; }
    public void setSellerId(UUID sellerId) { this.sellerId = sellerId; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public void setClosingBalance(BigDecimal closingBalance) { this.closingBalance = closingBalance; }
    public BigDecimal getExpectedBalance() { return expectedBalance; }
    public void setExpectedBalance(BigDecimal expectedBalance) { this.expectedBalance = expectedBalance; }
    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }
    public CashRegisterStatus getStatus() { return status; }
    public void setStatus(CashRegisterStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
