package com.pilarestilo.cashregister.domain.enums;

public enum CashMovementCategory {
    INITIAL_BALANCE,
    CASH_SALE,
    WITHDRAWAL,
    EXPENSE,
    ADJUSTMENT,
    REFUND;

    public CashMovementType defaultDirection() {
        return switch (this) {
            case INITIAL_BALANCE, CASH_SALE -> CashMovementType.IN;
            case WITHDRAWAL, EXPENSE, REFUND -> CashMovementType.OUT;
            case ADJUSTMENT -> null; // direction must be explicit
        };
    }
}
