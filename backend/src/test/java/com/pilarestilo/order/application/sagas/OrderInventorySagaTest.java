package com.pilarestilo.order.application.sagas;

import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.shared.application.Money;
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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderInventorySagaTest {

    @Mock
    UpdateOrderStatusUseCase updateOrderStatusUseCase;

    @Mock
    OrderRepository orderRepository;

    @Mock
    InventoryService inventoryService;

    @InjectMocks
    OrderInventorySaga saga;

    @Test
    void confirms_payment_from_created_moves_to_paid_via_pending_payment() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithItems(
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "A", Money.of(BigDecimal.valueOf(1000)), 1))
        );
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        saga.onPaymentConfirmed(new PaymentConfirmed(UUID.randomUUID(), orderId, Instant.now()));

        verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.PENDING_PAYMENT);
        verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.PAID);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void confirms_payment_already_cancelled_order_is_ignored() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithItems(
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "A", Money.of(BigDecimal.valueOf(1000)), 1))
        );
        order.cancel();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        saga.onPaymentConfirmed(new PaymentConfirmed(UUID.randomUUID(), orderId, Instant.now()));

        verify(updateOrderStatusUseCase, never()).execute(any(), any());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void rejects_payment_cancels_order_and_releases_all_items() {
        UUID orderId = UUID.randomUUID();
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();
        Order order = orderWithItems(
                List.of(
                        new OrderItem(UUID.randomUUID(), productA, "A", Money.of(BigDecimal.valueOf(1000)), 2),
                        new OrderItem(UUID.randomUUID(), productB, "B", Money.of(BigDecimal.valueOf(500)), 1)
                )
        );
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        saga.onPaymentRejected(new PaymentRejected(UUID.randomUUID(), orderId, UUID.randomUUID(), Instant.now()));

        verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.CANCELLED);
        verify(inventoryService).release(productA, 2);
        verify(inventoryService).release(productB, 1);
    }

    @Test
    void rejects_payment_already_cancelled_order_is_ignored_for_idempotency() {
        UUID orderId = UUID.randomUUID();
        Order order = orderWithItems(
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "A", Money.of(BigDecimal.valueOf(1000)), 1))
        );
        order.cancel();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        saga.onPaymentRejected(new PaymentRejected(UUID.randomUUID(), orderId, UUID.randomUUID(), Instant.now()));

        verify(updateOrderStatusUseCase, never()).execute(any(), any());
        verifyNoInteractions(inventoryService);
    }

    private Order orderWithItems(List<OrderItem> items) {
        return Order.create(
                UUID.randomUUID(),
                items,
                Money.zero(),
                PaymentMethod.BANK_TRANSFER,
                "LOCAL",
                "chilexpress",
                "Chilexpress",
                "POR_PAGAR",
                UUID.randomUUID(),
                "Av Apoquindo 123",
                null
        );
    }
}
