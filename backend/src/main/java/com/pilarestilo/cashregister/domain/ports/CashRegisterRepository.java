package com.pilarestilo.cashregister.domain.ports;

import com.pilarestilo.cashregister.domain.model.CashRegister;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

public interface CashRegisterRepository {
    CashRegister save(CashRegister cashRegister);
    Optional<CashRegister> findById(UUID id);
    Optional<CashRegister> findOpenBySellerId(UUID sellerId);
    Optional<CashRegister> findAnyOpenRegister();
    Page<CashRegister> findAll(Pageable pageable);
}
