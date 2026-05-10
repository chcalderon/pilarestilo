package com.pilarestilo.order.domain;

import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private static final String SHIPPING_ZONE = "LOCAL";
    private static final String SHIPPING_COURIER_ID = "chilexpress";
    private static final String SHIPPING_COURIER_NAME = "Chilexpress";
    private static final String SHIPPING_PAYMENT_MODE = "POR_PAGAR";

    private Order buildTestOrder() {
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Test Item",
                        Money.of(BigDecimal.valueOf(100000)), 2)
        );
        return createOrder(items, Money.zero());
    }

    @Test
    void new_order_has_status_CREATED() {
        assertEquals(OrderStatus.CREATED, buildTestOrder().getStatus());
    }

    @Test
    void can_transition_CREATED_to_PENDING_PAYMENT() {
        Order o = buildTestOrder();
        o.markAsPendingPayment();
        assertEquals(OrderStatus.PENDING_PAYMENT, o.getStatus());
    }

    @Test
    void cannot_transition_CREATED_to_PAID_directly() {
        assertThrows(DomainException.class, () -> buildTestOrder().markAsPaid());
    }

    @Test
    void can_cancel_from_CREATED() {
        Order o = buildTestOrder();
        o.cancel();
        assertEquals(OrderStatus.CANCELLED, o.getStatus());
    }

    @Test
    void cannot_cancel_DELIVERED_order() {
        Order o = buildTestOrder();
        o.markAsPendingPayment();
        o.markAsPaid();
        o.markAsPreparingOrder();
        o.markAsShipped();
        o.markAsDelivered();
        assertThrows(DomainException.class, o::cancel);
    }

    @Test
    void subtotal_computed_correctly() {
        Order o = buildTestOrder();
        assertEquals(Money.of(BigDecimal.valueOf(200000)), o.getSubtotal());
    }

    @Test
    void total_equals_subtotal_minus_discount() {
        List<OrderItem> items = List.of(
                new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Item",
                        Money.of(BigDecimal.valueOf(100000)), 1)
        );
        Money discount = Money.of(BigDecimal.valueOf(10000));
        Order o = createOrder(items, discount);
        assertEquals(Money.of(BigDecimal.valueOf(90000)), o.getTotalAmount());
    }

    @Test
    void full_happy_path_transitions() {
        Order o = buildTestOrder();
        o.markAsPendingPayment();
        o.markAsPaid();
        o.markAsPreparingOrder();
        o.markAsShipped();
        o.markAsDelivered();
        assertEquals(OrderStatus.DELIVERED, o.getStatus());
    }

    @Test
    void cannot_skip_to_shipped_directly() {
        Order o = buildTestOrder();
        assertThrows(DomainException.class, o::markAsShipped);
    }

    @Test
    void cannot_create_order_without_items() {
        assertThrows(DomainException.class,
                () -> createOrder(List.of(), Money.zero()));
    }

    private Order createOrder(List<OrderItem> items, Money discountAmount) {
        return Order.create(
                UUID.randomUUID(),
                items,
                discountAmount,
                PaymentMethod.BANK_TRANSFER,
                SHIPPING_ZONE,
                SHIPPING_COURIER_ID,
                SHIPPING_COURIER_NAME,
                SHIPPING_PAYMENT_MODE,
                UUID.randomUUID(),
                null,
                null
        );
    }
}
