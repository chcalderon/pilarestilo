package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OpenCashRegisterUseCase {
    private final CashRegisterRepository cashRegisterRepository;

    public OpenCashRegisterUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public CashRegisterDto execute(UUID sellerId, BigDecimal openingBalance) {
        if (cashRegisterRepository.findOpenBySellerId(sellerId).isPresent()) {
            throw new DomainException("Seller already has an open cash register");
        }
        CashRegister cr = CashRegister.open(sellerId, openingBalance);
        return CashRegisterDto.from(cashRegisterRepository.save(cr));
    }
}
