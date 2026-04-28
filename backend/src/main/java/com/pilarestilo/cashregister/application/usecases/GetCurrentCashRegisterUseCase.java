package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class GetCurrentCashRegisterUseCase {
    private final CashRegisterRepository cashRegisterRepository;

    public GetCurrentCashRegisterUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public CashRegisterDto execute(UUID sellerId) {
        return cashRegisterRepository.findOpenBySellerId(sellerId)
                .map(CashRegisterDto::from)
                .orElseThrow(() -> new DomainException("No open cash register"));
    }
}
