package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListSellerCashRegisterHistoryUseCaseTest {

    @Mock
    CashRegisterRepository cashRegisterRepository;

    @InjectMocks
    ListSellerCashRegisterHistoryUseCase useCase;

    @Test
    void uses_seller_scope_and_date_range() {
        UUID sellerId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 4, 20);
        LocalDate to = LocalDate.of(2026, 4, 28);
        var pageable = PageRequest.of(1, 10);
        LocalDateTime expectedFrom = from.atStartOfDay();
        LocalDateTime expectedTo = to.plusDays(1).atStartOfDay().minusNanos(1);

        CashRegister register = CashRegister.reconstruct(
                UUID.randomUUID(),
                sellerId,
                LocalDateTime.of(2026, 4, 27, 10, 0),
                null,
                BigDecimal.valueOf(50_000),
                null,
                null,
                null,
                CashRegisterStatus.OPEN,
                null,
                List.of()
        );

        when(cashRegisterRepository.findHistoryForSeller(
                eq(sellerId),
                eq(CashRegisterStatus.OPEN),
                eq(expectedFrom),
                eq(expectedTo),
                eq(pageable)
        )).thenReturn(new PageImpl<>(List.of(register), pageable, 11));

        var result = useCase.execute(sellerId, CashRegisterStatus.OPEN, from, to, pageable);

        assertEquals(11, result.getTotalElements());
        assertEquals(register.getSellerId(), result.getContent().get(0).sellerId());
        verify(cashRegisterRepository).findHistoryForSeller(
                eq(sellerId),
                eq(CashRegisterStatus.OPEN),
                eq(expectedFrom),
                eq(expectedTo),
                eq(pageable)
        );
    }
}
