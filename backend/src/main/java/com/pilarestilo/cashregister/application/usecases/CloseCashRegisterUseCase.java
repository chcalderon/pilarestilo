package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CloseCashRegisterUseCase {
    private final CashRegisterRepository cashRegisterRepository;

    public CloseCashRegisterUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public CashRegisterDto execute(UUID sellerId, BigDecimal closingBalance, String notes) {
        CashRegister cr = cashRegisterRepository.findOpenBySellerId(sellerId)
                .orElseThrow(() -> new DomainException("No open cash register found"));
        cr.close(closingBalance, notes);
        return CashRegisterDto.from(cashRegisterRepository.save(cr));
    }
}
