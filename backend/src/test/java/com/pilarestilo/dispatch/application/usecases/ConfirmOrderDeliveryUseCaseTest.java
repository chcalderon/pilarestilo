package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.dto.MoneyDto;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.shared.domain.DomainException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmOrderDeliveryUseCaseTest {

    @Mock
    DispatchRepository dispatchRepository;
    @Mock
    GetOrderUseCase getOrderUseCase;
    @Mock
    UpdateOrderStatusUseCase updateOrderStatusUseCase;

    @InjectMocks
    ConfirmOrderDeliveryUseCase useCase;

    @Test
    void customer_can_confirm_delivered_when_order_is_shipped() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID dispatcherId = UUID.randomUUID();
        Dispatch dispatch = Dispatch.create(orderId);
        dispatch.claim(dispatcherId);
        dispatch.dispatch("Chilexpress", "TRK-1", null, null);

        OrderDto shippedOrder = order(orderId, customerId, OrderStatus.SHIPPED);
        OrderDto deliveredOrder = order(orderId, customerId, OrderStatus.DELIVERED);

        when(getOrderUseCase.execute(orderId)).thenReturn(shippedOrder);
        when(dispatchRepository.findByOrderId(orderId)).thenReturn(Optional.of(dispatch));
        when(updateOrderStatusUseCase.execute(orderId, OrderStatus.DELIVERED)).thenReturn(deliveredOrder);

        OrderDto result = useCase.execute(orderId, customerId);

        assertEquals(OrderStatus.DELIVERED, result.status());
        verify(dispatchRepository).save(dispatch);
        verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.DELIVERED);
    }

    @Test
    void rejects_when_customer_does_not_own_order() {
        UUID orderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherCustomer = UUID.randomUUID();

        when(getOrderUseCase.execute(orderId)).thenReturn(order(orderId, ownerId, OrderStatus.SHIPPED));

        assertThrows(DomainException.class, () -> useCase.execute(orderId, otherCustomer));
    }

    private OrderDto order(UUID orderId, UUID customerId, OrderStatus status) {
        return new OrderDto(
                orderId,
                customerId,
                List.of(),
                new MoneyDto(BigDecimal.TEN, "CLP"),
                new MoneyDto(BigDecimal.ZERO, "CLP"),
                new MoneyDto(BigDecimal.TEN, "CLP"),
                PaymentMethod.TRANSFER,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                status,
                Instant.now(),
                Instant.now()
        );
    }
}
