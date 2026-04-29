package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoConfirmDeliveredDispatchesUseCaseTest {

    @Test
    void auto_confirms_dispatches_older_than_15_days() {
        DispatchRepository dispatchRepository = mock(DispatchRepository.class);
        UpdateOrderStatusUseCase updateOrderStatusUseCase = mock(UpdateOrderStatusUseCase.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-01T10:00:00Z"), ZoneOffset.UTC);

        UUID orderId = UUID.randomUUID();
        Dispatch dispatch = Dispatch.create(orderId);
        dispatch.claim(UUID.randomUUID());
        dispatch.dispatch("Chilexpress", "TRK-2", null, null);

        when(dispatchRepository.findByStatusAndDispatchedAtBefore(eq(DispatchStatus.DISPATCHED), any()))
                .thenReturn(List.of(dispatch));

        AutoConfirmDeliveredDispatchesUseCase useCase = new AutoConfirmDeliveredDispatchesUseCase(
                dispatchRepository,
                updateOrderStatusUseCase,
                fixedClock
        );

        int updated = useCase.execute();

        assertEquals(1, updated);
        verify(dispatchRepository, times(1)).save(dispatch);
        verify(updateOrderStatusUseCase, times(1)).execute(orderId, OrderStatus.DELIVERED);
    }
}
