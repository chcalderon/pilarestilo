package com.pilarestilo.cashregister.domain.ports;

import com.pilarestilo.cashregister.domain.model.CashMovement;
import java.util.List;
import java.util.UUID;

public interface CashMovementRepository {
    CashMovement save(CashMovement movement);
    List<CashMovement> findByCashRegisterId(UUID cashRegisterId);
}
