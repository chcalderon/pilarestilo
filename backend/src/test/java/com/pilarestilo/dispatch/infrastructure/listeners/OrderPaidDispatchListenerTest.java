package com.pilarestilo.dispatch.infrastructure.listeners;

import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.dto.MoneyDto;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaidDispatchListenerTest {

    @Mock
    DispatchRepository dispatchRepository;
    @Mock
    GetOrderUseCase getOrderUseCase;

    @InjectMocks
    OrderPaidDispatchListener listener;

    @Test
    void creates_dispatch_with_shipping_snapshot_when_order_becomes_paid() {
        UUID orderId = UUID.randomUUID();
        when(dispatchRepository.existsByOrderId(orderId)).thenReturn(false);
        when(getOrderUseCase.execute(orderId)).thenReturn(order(orderId));

        listener.onOrderStatusChanged(new OrderStatusChanged(
                orderId,
                UUID.randomUUID(),
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAID,
                Instant.now()
        ));

        ArgumentCaptor<Dispatch> captor = ArgumentCaptor.forClass(Dispatch.class);
        verify(dispatchRepository).save(captor.capture());
        Dispatch created = captor.getValue();
        assertEquals(orderId, created.getOrderId());
        assertEquals("LOCAL", created.getOrderShippingZoneCode());
        assertEquals("chilexpress", created.getOrderShippingCourierId());
        assertEquals("Chilexpress", created.getOrderShippingCourierName());
        assertEquals("Casa 12", created.getOrderShippingAddressReference());
    }

    @Test
    void ignores_events_when_dispatch_already_exists_or_status_is_not_paid() {
        UUID orderId = UUID.randomUUID();
        when(dispatchRepository.existsByOrderId(orderId)).thenReturn(true);

        listener.onOrderStatusChanged(new OrderStatusChanged(
                orderId,
                UUID.randomUUID(),
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAID,
                Instant.now()
        ));

        listener.onOrderStatusChanged(new OrderStatusChanged(
                orderId,
                UUID.randomUUID(),
                OrderStatus.PAID,
                OrderStatus.SHIPPED,
                Instant.now()
        ));

        verify(dispatchRepository, never()).save(any());
    }

    private OrderDto order(UUID orderId) {
        return new OrderDto(
                orderId,
                UUID.randomUUID(),
                List.of(),
                new MoneyDto(BigDecimal.TEN, "CLP"),
                new MoneyDto(BigDecimal.ZERO, "CLP"),
                new MoneyDto(BigDecimal.TEN, "CLP"),
                PaymentMethod.TRANSFER,
                "LOCAL",
                "chilexpress",
                "Chilexpress",
                "POR_PAGAR",
                null,
                "Casa 12",
                null,
                null,
                OrderStatus.PAID,
                Instant.now(),
                Instant.now()
        );
    }
}
