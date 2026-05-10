package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.dto.MoneyDto;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkDispatchedUseCaseTest {

    @Mock
    DispatchRepository dispatchRepository;
    @Mock
    UpdateOrderStatusUseCase updateOrderStatusUseCase;
    @Mock
    GetOrderUseCase getOrderUseCase;

    @InjectMocks
    MarkDispatchedUseCase useCase;

    @Test
    void stores_structured_override_when_selected_carrier_differs_from_order_snapshot() {
        UUID orderId = UUID.randomUUID();
        UUID dispatchId = UUID.randomUUID();
        UUID dispatcherId = UUID.randomUUID();

        Dispatch dispatch = Dispatch.reconstruct(
                dispatchId,
                orderId,
                dispatcherId,
                DispatchStatus.IN_PROGRESS,
                null,
                null,
                null,
                null,
                null,
                null,
                "LOCAL",
                "starken",
                "Starken",
                "Depto 9",
                null,
                null,
                null,
                null,
                java.time.LocalDateTime.now().minusDays(1)
        );

        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(dispatchRepository.save(any(Dispatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DispatchDto result = useCase.execute(dispatchId, dispatcherId, "Chilexpress", "TRK-1", null, null);

        assertEquals("Starken", result.carrierOverrideConfigured());
        assertEquals("Chilexpress", result.carrierOverrideSelected());
        assertEquals(dispatcherId, result.carrierOverrideBy());
        assertNotNull(result.carrierOverrideAt());
        verify(getOrderUseCase, never()).execute(any(UUID.class));
        verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.SHIPPED);
    }

    @Test
    void does_not_store_override_when_selected_carrier_matches_snapshot() {
        UUID orderId = UUID.randomUUID();
        UUID dispatchId = UUID.randomUUID();
        UUID dispatcherId = UUID.randomUUID();

        Dispatch dispatch = Dispatch.create(orderId, "LOCAL", "chilexpress", "Chilexpress", null);
        dispatch.claim(dispatcherId);

        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(dispatch));
        when(dispatchRepository.save(any(Dispatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Dispatch claimedDispatch = Dispatch.reconstruct(
                dispatchId,
                dispatch.getOrderId(),
                dispatch.getDispatcherId(),
                dispatch.getStatus(),
                dispatch.getCarrier(),
                dispatch.getTrackingCode(),
                dispatch.getScheduledDate(),
                dispatch.getDispatchedAt(),
                dispatch.getDeliveredAt(),
                dispatch.getNotes(),
                dispatch.getOrderShippingZoneCode(),
                dispatch.getOrderShippingCourierId(),
                dispatch.getOrderShippingCourierName(),
                dispatch.getOrderShippingAddressReference(),
                dispatch.getCarrierOverrideConfigured(),
                dispatch.getCarrierOverrideSelected(),
                dispatch.getCarrierOverrideBy(),
                dispatch.getCarrierOverrideAt(),
                dispatch.getCreatedAt()
        );
        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(claimedDispatch));

        DispatchDto result = useCase.execute(dispatchId, dispatcherId, "chilexpress", "TRK-2", null, "ok");

        assertNull(result.carrierOverrideConfigured());
        assertNull(result.carrierOverrideSelected());
        assertNull(result.carrierOverrideBy());
        assertNull(result.carrierOverrideAt());
    }

    @Test
    void falls_back_to_order_when_dispatch_snapshot_is_missing() {
        UUID orderId = UUID.randomUUID();
        UUID dispatchId = UUID.randomUUID();
        UUID dispatcherId = UUID.randomUUID();

        Dispatch dispatch = Dispatch.create(orderId);
        dispatch.claim(dispatcherId);
        Dispatch claimedDispatch = Dispatch.reconstruct(
                dispatchId,
                dispatch.getOrderId(),
                dispatch.getDispatcherId(),
                dispatch.getStatus(),
                dispatch.getCarrier(),
                dispatch.getTrackingCode(),
                dispatch.getScheduledDate(),
                dispatch.getDispatchedAt(),
                dispatch.getDeliveredAt(),
                dispatch.getNotes(),
                dispatch.getOrderShippingZoneCode(),
                dispatch.getOrderShippingCourierId(),
                dispatch.getOrderShippingCourierName(),
                dispatch.getOrderShippingAddressReference(),
                dispatch.getCarrierOverrideConfigured(),
                dispatch.getCarrierOverrideSelected(),
                dispatch.getCarrierOverrideBy(),
                dispatch.getCarrierOverrideAt(),
                dispatch.getCreatedAt()
        );

        when(dispatchRepository.findById(dispatchId)).thenReturn(Optional.of(claimedDispatch));
        when(dispatchRepository.save(any(Dispatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(getOrderUseCase.execute(orderId)).thenReturn(order(orderId));

        DispatchDto result = useCase.execute(dispatchId, dispatcherId, "Correos", "TRK-3", null, null);

        assertEquals("Chilexpress", result.carrierOverrideConfigured());
        assertEquals("Correos", result.carrierOverrideSelected());
    }

    private OrderDto order(UUID orderId) {
        return new OrderDto(
                orderId,
                UUID.randomUUID(),
                List.of(),
                new MoneyDto(BigDecimal.TEN, "CLP"),
                new MoneyDto(BigDecimal.ZERO, "CLP"),
                new MoneyDto(BigDecimal.TEN, "CLP"),
                PaymentMethod.BANK_TRANSFER,
                "LOCAL",
                "chilexpress",
                "Chilexpress",
                "POR_PAGAR",
                null,
                null,
                OrderStatus.PAID,
                Instant.now(),
                Instant.now()
        );
    }
}
