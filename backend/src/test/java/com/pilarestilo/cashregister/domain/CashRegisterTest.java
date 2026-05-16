package com.pilarestilo.cashregister.domain;

import com.pilarestilo.cashregister.domain.enums.CashMovementCategory;
import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CashRegisterTest {

    private CashRegister buildOpen() {
        return CashRegister.open(UUID.randomUUID(), new BigDecimal("50000"));
    }

    @Test
    void new_register_is_open_with_opening_balance() {
        CashRegister cr = buildOpen();
        assertEquals(CashRegisterStatus.OPEN, cr.getStatus());
        assertEquals(new BigDecimal("50000"), cr.getOpeningBalance());
    }

    @Test
    void expected_balance_equals_opening_plus_sales_minus_refunds() {
        CashRegister cr = buildOpen();
        cr.addMovement(CashMovementType.IN,  CashMovementCategory.CASH_SALE,
                new BigDecimal("10000"), "Venta #1", null, UUID.randomUUID());
        cr.addMovement(CashMovementType.IN,  CashMovementCategory.ADJUSTMENT,
                new BigDecimal("5000"),  "Ingreso extra", null, UUID.randomUUID());
        cr.addMovement(CashMovementType.OUT, CashMovementCategory.WITHDRAWAL,
                new BigDecimal("2000"),  "Retiro", null, UUID.randomUUID());
        assertEquals(new BigDecimal("63000"), cr.getExpectedBalance());
    }

    @Test
    void can_close_with_declared_amount() {
        CashRegister cr = buildOpen();
        cr.addMovement(CashMovementType.IN, CashMovementCategory.CASH_SALE,
                new BigDecimal("10000"), "Venta", null, UUID.randomUUID());
        cr.close(new BigDecimal("59000"), null);
        assertEquals(CashRegisterStatus.CLOSED, cr.getStatus());
        assertEquals(new BigDecimal("59000"), cr.getClosingBalance());
        assertEquals(new BigDecimal("-1000"), cr.getDifference());
    }

    @Test
    void cannot_add_movement_to_closed_register() {
        CashRegister cr = buildOpen();
        cr.close(new BigDecimal("50000"), null);
        assertThrows(DomainException.class,
                () -> cr.addMovement(CashMovementType.IN, CashMovementCategory.ADJUSTMENT,
                        new BigDecimal("1000"), "X", null, UUID.randomUUID()));
    }

    @Test
    void cannot_close_already_closed_register() {
        CashRegister cr = buildOpen();
        cr.close(new BigDecimal("50000"), null);
        assertThrows(DomainException.class, () -> cr.close(new BigDecimal("50000"), null));
    }

    @Test
    void closeCashRegister_calculatesDifferenceCorrectly() {
        // openBalance=10000, add IN(ADJUSTMENT) 5000, close declaring 14000
        // expectedBalance = 10000 + 5000 = 15000
        // difference = 14000 - 15000 = -1000
        CashRegister cr = CashRegister.open(UUID.randomUUID(), new BigDecimal("10000"));
        cr.addMovement(CashMovementType.IN, CashMovementCategory.ADJUSTMENT,
                new BigDecimal("5000"), "Ingreso extra", null, UUID.randomUUID());
        cr.close(new BigDecimal("14000"), null);
        assertEquals(new BigDecimal("15000"), cr.getExpectedBalance());
        assertEquals(new BigDecimal("-1000"), cr.getDifference());
    }

    @Test
    void closeCashRegister_rejectsMissingDeclaredBalance() {
        CashRegister cr = buildOpen();
        assertThrows(DomainException.class, () -> cr.close(null, null));
    }
}
