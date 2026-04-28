package com.pilarestilo.cashregister.domain.model;

import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.shared.domain.DomainException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CashRegister {
    private UUID id;
    private UUID sellerId;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal expectedBalance;
    private BigDecimal difference;
    private CashRegisterStatus status;
    private String notes;
    private List<CashMovement> movements = new ArrayList<>();

    private CashRegister() {}

    public static CashRegister open(UUID sellerId, BigDecimal openingBalance) {
        CashRegister cr = new CashRegister();
        cr.id = UUID.randomUUID();
        cr.sellerId = sellerId;
        cr.openedAt = LocalDateTime.now();
        cr.openingBalance = openingBalance;
        cr.status = CashRegisterStatus.OPEN;
        return cr;
    }

    public static CashRegister reconstruct(UUID id, UUID sellerId, LocalDateTime openedAt,
                                            LocalDateTime closedAt, BigDecimal openingBalance,
                                            BigDecimal closingBalance, BigDecimal expectedBalance,
                                            BigDecimal difference, CashRegisterStatus status,
                                            String notes, List<CashMovement> movements) {
        CashRegister cr = new CashRegister();
        cr.id = id; cr.sellerId = sellerId; cr.openedAt = openedAt; cr.closedAt = closedAt;
        cr.openingBalance = openingBalance; cr.closingBalance = closingBalance;
        cr.expectedBalance = expectedBalance; cr.difference = difference;
        cr.status = status; cr.notes = notes;
        cr.movements = new ArrayList<>(movements);
        return cr;
    }

    public void addMovement(CashMovementType type, BigDecimal amount,
                             String description, UUID orderId, UUID recordedBy) {
        if (status == CashRegisterStatus.CLOSED) {
            throw new DomainException("Cannot add movement to a closed cash register");
        }
        movements.add(CashMovement.create(id, type, amount, description, orderId, recordedBy));
    }

    public void close(BigDecimal declaredAmount, String notes) {
        if (status == CashRegisterStatus.CLOSED) {
            throw new DomainException("Cash register is already closed");
        }
        BigDecimal expected = computeExpectedBalance();
        this.closingBalance = declaredAmount;
        this.expectedBalance = expected;
        this.difference = declaredAmount.subtract(expected);
        this.closedAt = LocalDateTime.now();
        this.notes = notes;
        this.status = CashRegisterStatus.CLOSED;
    }

    public BigDecimal getExpectedBalance() {
        if (status == CashRegisterStatus.CLOSED) return expectedBalance;
        return computeExpectedBalance();
    }

    private BigDecimal computeExpectedBalance() {
        BigDecimal balance = openingBalance;
        for (CashMovement m : movements) {
            if (m.getType() == CashMovementType.SALE || m.getType() == CashMovementType.IN) {
                balance = balance.add(m.getAmount());
            } else {
                balance = balance.subtract(m.getAmount());
            }
        }
        return balance;
    }

    public UUID getId() { return id; }
    public UUID getSellerId() { return sellerId; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public BigDecimal getDifference() { return difference; }
    public CashRegisterStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public List<CashMovement> getMovements() { return Collections.unmodifiableList(movements); }
}
