package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ListSellerCashRegisterHistoryUseCase {

    private final CashRegisterRepository cashRegisterRepository;

    public ListSellerCashRegisterHistoryUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public Page<CashRegisterDto> execute(UUID sellerId,
                                         CashRegisterStatus status,
                                         LocalDate from,
                                         LocalDate to,
                                         Pageable pageable) {
        LocalDateTime openedFrom = from != null ? from.atStartOfDay() : null;
        LocalDateTime openedTo = to != null ? to.plusDays(1).atStartOfDay().minusNanos(1) : null;
        return cashRegisterRepository
                .findHistoryForSeller(sellerId, status, openedFrom, openedTo, pageable)
                .map(CashRegisterDto::from);
    }
}
