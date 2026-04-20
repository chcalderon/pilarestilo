package com.pilarestilo.customercredit.domain.ports;

import com.pilarestilo.customercredit.domain.model.CreditMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CreditMovementRepository {

    CreditMovement save(CreditMovement movement);

    Page<CreditMovement> findByCustomerId(UUID customerId, Pageable pageable);
}
