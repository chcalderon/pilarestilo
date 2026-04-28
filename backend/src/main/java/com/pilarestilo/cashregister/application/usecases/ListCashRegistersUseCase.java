package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ListCashRegistersUseCase {
    private final CashRegisterRepository cashRegisterRepository;

    public ListCashRegistersUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public Page<CashRegisterDto> execute(Pageable pageable) {
        return cashRegisterRepository.findAll(pageable).map(CashRegisterDto::from);
    }
}
