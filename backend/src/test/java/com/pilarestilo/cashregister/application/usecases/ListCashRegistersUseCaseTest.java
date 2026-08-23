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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListCashRegistersUseCaseTest {

    @Mock
    CashRegisterRepository cashRegisterRepository;

    @InjectMocks
    ListCashRegistersUseCase useCase;

    @Test
    void maps_filters_and_returns_page() {
        UUID sellerId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 30);
        var pageable = PageRequest.of(0, 10);
        LocalDateTime expectedFrom = from.atStartOfDay();
        LocalDateTime expectedTo = to.plusDays(1).atStartOfDay().minusNanos(1);

        CashRegister register = CashRegister.reconstruct(
                UUID.randomUUID(),
                sellerId,
                LocalDateTime.of(2026, 4, 10, 9, 0),
                LocalDateTime.of(2026, 4, 10, 18, 0),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(250_000),
                BigDecimal.valueOf(250_000),
                BigDecimal.ZERO,
                CashRegisterStatus.CLOSED,
                "cierre ok",
                List.of()
        );

        when(cashRegisterRepository.findHistory(
                CashRegisterStatus.CLOSED,
                sellerId,
                expectedFrom,
                expectedTo,
                pageable
        )).thenReturn(new PageImpl<>(List.of(register), pageable, 1));

        var result = useCase.execute(pageable, CashRegisterStatus.CLOSED, sellerId, from, to);

        assertEquals(1, result.getTotalElements());
        assertEquals(register.getId(), result.getContent().get(0).id());
        verify(cashRegisterRepository).findHistory(
                CashRegisterStatus.CLOSED,
                sellerId,
                expectedFrom,
                expectedTo,
                pageable
        );
    }
}
