package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchHistoryRowDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import com.pilarestilo.payment.application.usecases.GetPaymentByOrderUseCase;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListDispatchHistoryUseCaseTest {

    private DispatchRepository dispatchRepository;
    private GetOrderUseCase getOrderUseCase;
    private GetPaymentByOrderUseCase getPaymentByOrderUseCase;
    private ListDispatchHistoryUseCase useCase;

    private final Pageable pageable = PageRequest.of(0, 20);
    private final UUID orderId = UUID.randomUUID();
    private Dispatch dispatch;

    @BeforeEach
    void setUp() {
        dispatchRepository = mock(DispatchRepository.class);
        getOrderUseCase = mock(GetOrderUseCase.class);
        getPaymentByOrderUseCase = mock(GetPaymentByOrderUseCase.class);
        useCase = new ListDispatchHistoryUseCase(dispatchRepository, getOrderUseCase,
                getPaymentByOrderUseCase, mock(UserRepository.class));

        dispatch = Dispatch.create(orderId);
        when(dispatchRepository.findHistory(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dispatch)));
        when(getPaymentByOrderUseCase.execute(any())).thenThrow(new NoSuchElementException("none"));
    }

    @Test
    @DisplayName("a row carries the reference the customer quotes, not just the order's UUID")
    void carriesThePublicReference() {
        OrderDto order = mock(OrderDto.class);
        when(order.publicReference()).thenReturn("PE-53DA66C120");
        when(getOrderUseCase.execute(orderId)).thenReturn(order);

        DispatchHistoryRowDto row = useCase.execute(pageable, null, null).getContent().getFirst();

        assertThat(row.orderReference()).isEqualTo("PE-53DA66C120");
    }

    @Test
    @DisplayName("a row whose order can no longer be read still appears, without a reference")
    void survivesAnUnreadableOrder() {
        when(getOrderUseCase.execute(orderId)).thenThrow(new NoSuchElementException("gone"));

        DispatchHistoryRowDto row = useCase.execute(pageable, null, null).getContent().getFirst();

        assertThat(row.orderReference()).isNull();
        assertThat(row.orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("with no dates given it reports the current month")
    void defaultsToThisMonth() {
        when(getOrderUseCase.execute(orderId)).thenThrow(new NoSuchElementException("gone"));

        useCase.execute(pageable, null, null);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(dispatchRepository).findHistory(from.capture(), to.capture(), any());

        assertThat(from.getValue()).isEqualTo(YearMonth.now().atDay(1));
        assertThat(to.getValue()).isEqualTo(YearMonth.now().atEndOfMonth());
    }

    @Test
    @DisplayName("given only a start date it reports the month that follows it")
    void derivesTheEndFromTheStart() {
        when(getOrderUseCase.execute(orderId)).thenThrow(new NoSuchElementException("gone"));
        LocalDate from = LocalDate.of(2026, 3, 10);

        useCase.execute(pageable, from, null);

        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(dispatchRepository).findHistory(any(), to.capture(), any());
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2026, 4, 9));
    }

    @Test
    @DisplayName("an order sold at the counter is attributed to whoever approved the payment")
    void fallsBackToWebWhenNoPaymentReviewer() {
        when(getOrderUseCase.execute(orderId)).thenThrow(new NoSuchElementException("gone"));

        DispatchHistoryRowDto row = useCase.execute(pageable, null, null).getContent().getFirst();

        assertThat(row.soldBy()).isEqualTo("Web");
    }

    @Test
    @DisplayName("an empty month is an empty page, not a failure")
    void emptyMonth() {
        when(dispatchRepository.findHistory(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(useCase.execute(pageable, null, null).getContent()).isEmpty();
    }

    @Test
    @DisplayName("explicit dates are used as given")
    void honoursExplicitDates() {
        when(getOrderUseCase.execute(orderId)).thenThrow(new NoSuchElementException("gone"));
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        useCase.execute(pageable, from, to);

        verify(dispatchRepository).findHistory(from, to, pageable);
        assertThat(Optional.of(from)).isPresent();
    }
}
