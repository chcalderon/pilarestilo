package com.pilarestilo.cashregister.domain;

import com.pilarestilo.cashregister.domain.enums.CashMovementCategory;
import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CashMovementCategoryTest {

    @Test
    void eachCategoryHasExpectedDirection() {
        assertEquals(CashMovementType.IN,  CashMovementCategory.INITIAL_BALANCE.defaultDirection());
        assertEquals(CashMovementType.IN,  CashMovementCategory.CASH_SALE.defaultDirection());
        assertEquals(CashMovementType.OUT, CashMovementCategory.REFUND.defaultDirection());
        assertEquals(CashMovementType.OUT, CashMovementCategory.WITHDRAWAL.defaultDirection());
        assertEquals(CashMovementType.OUT, CashMovementCategory.EXPENSE.defaultDirection());
        assertNull(CashMovementCategory.ADJUSTMENT.defaultDirection(),
                "ADJUSTMENT has no fixed direction — must be provided explicitly");
    }
}
