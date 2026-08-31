package com.pilarestilo.order.application.usecases;

import com.pilarestilo.order.application.dto.MoneyDto;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.dto.OrderItemDto;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.domain.ports.AnalyticsTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackOrderAnalyticsUseCaseTest {

    @Mock
    GetOrderUseCase getOrderUseCase;
    @Mock
    AnalyticsTracker analyticsTracker;

    @InjectMocks
    TrackOrderAnalyticsUseCase useCase;

    @Test
    void order_created_is_tracked_with_the_money_numbers_keyed_to_the_customer() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(getOrderUseCase.execute(orderId)).thenReturn(order(orderId, OrderStatus.PENDING_PAYMENT));

        useCase.onOrderCreated(new OrderCreated(orderId, customerId, Instant.now()));

        ArgumentCaptor<Map<String, Object>> props = captor();
        verify(analyticsTracker).track(eq("order_created"), eq(customerId.toString()), props.capture());
        assertThat(props.getValue())
                .containsEntry("order_id", orderId.toString())
                .containsEntry("public_reference", "PE-ABC123")
                .containsEntry("total", new BigDecimal("29990"))
                .containsEntry("currency", "CLP")
                .containsEntry("item_count", 3)
                .containsEntry("line_count", 2)
                .containsEntry("payment_method", "TRANSFER")
                .containsEntry("sales_channel", "ECOMMERCE");
    }

    @Test
    void order_paid_is_tracked_only_when_the_new_status_is_paid() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(getOrderUseCase.execute(orderId)).thenReturn(order(orderId, OrderStatus.PAID));

        useCase.onOrderStatusChanged(new OrderStatusChanged(
                orderId, customerId, OrderStatus.PAYMENT_UNDER_REVIEW, OrderStatus.PAID, Instant.now()));

        ArgumentCaptor<Map<String, Object>> props = captor();
        verify(analyticsTracker).track(eq("order_paid"), eq(customerId.toString()), props.capture());
        assertThat(props.getValue()).containsEntry("previous_status", "PAYMENT_UNDER_REVIEW");
    }

    @Test
    void a_status_change_that_is_not_to_paid_tracks_nothing() {
        useCase.onOrderStatusChanged(new OrderStatusChanged(
                UUID.randomUUID(), UUID.randomUUID(),
                OrderStatus.PAID, OrderStatus.SHIPPED, Instant.now()));

        verifyNoInteractions(analyticsTracker, getOrderUseCase);
    }

    @Test
    void an_event_with_no_customer_is_dropped() {
        useCase.onOrderCreated(new OrderCreated(UUID.randomUUID(), null, Instant.now()));

        verifyNoInteractions(analyticsTracker, getOrderUseCase);
    }

    @Test
    void the_event_still_goes_out_thin_when_the_order_cannot_be_read() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(getOrderUseCase.execute(orderId)).thenThrow(new DomainException("Order not found"));

        useCase.onOrderCreated(new OrderCreated(orderId, customerId, Instant.now()));

        ArgumentCaptor<Map<String, Object>> props = captor();
        verify(analyticsTracker).track(eq("order_created"), eq(customerId.toString()), props.capture());
        assertThat(props.getValue()).containsOnlyKeys("order_id");
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> captor() {
        return ArgumentCaptor.forClass(Map.class);
    }

    private OrderDto order(UUID orderId, OrderStatus status) {
        return new OrderDto(
                orderId,
                "PE-ABC123",
                UUID.randomUUID(),
                List.of(
                        new OrderItemDto(UUID.randomUUID(), UUID.randomUUID(), "Vestido",
                                new MoneyDto(new BigDecimal("14995"), "CLP"), 2, "Negro", "M"),
                        new OrderItemDto(UUID.randomUUID(), UUID.randomUUID(), "Aros",
                                new MoneyDto(new BigDecimal("15000"), "CLP"), 1, null, null)
                ),
                new MoneyDto(new BigDecimal("29990"), "CLP"),
                new MoneyDto(BigDecimal.ZERO, "CLP"),
                new MoneyDto(new BigDecimal("29990"), "CLP"),
                new MoneyDto(new BigDecimal("25202"), "CLP"),
                new MoneyDto(new BigDecimal("4788"), "CLP"),
                new BigDecimal("19.00"),
                PaymentMethod.TRANSFER,
                "RM",
                "starken",
                "Starken",
                "PREPAID",
                null,
                "Depto 42",
                null,
                SalesChannel.ECOMMERCE,
                status,
                Instant.now(),
                Instant.now()
        );
    }
}
