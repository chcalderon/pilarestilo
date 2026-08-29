package com.pilarestilo.notificationservice.infrastructure.listeners.kafka;

import com.pilarestilo.notificationservice.application.BillingNotificationDispatcher;
import com.pilarestilo.notificationservice.application.DiscountNotificationDispatcher;
import com.pilarestilo.notificationservice.application.OrderNotificationDispatcher;
import com.pilarestilo.notificationservice.application.PaymentNotificationDispatcher;
import com.pilarestilo.notificationservice.application.PaymentRegisteredNotificationDispatcher;
import com.pilarestilo.notificationservice.application.ReturnNotificationDispatcher;
import com.pilarestilo.notificationservice.application.UserNotificationDispatcher;
import com.pilarestilo.notificationservice.events.Events;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The Kafka listeners hold nothing of their own — each method is one delegation. The wiring
 * (topic names, container factory, group) is exercised end to end by the compose-stack
 * verification (plan Task 15), against a real broker.
 */
class KafkaListenerDelegationTest {

    private final Instant now = Instant.now();
    private final UUID id = UUID.randomUUID();

    @Test
    void order_listener_delegates() {
        OrderNotificationDispatcher dispatcher = mock(OrderNotificationDispatcher.class);
        KafkaOrderNotificationListener listener = new KafkaOrderNotificationListener(dispatcher);

        var created = new Events.OrderCreated(id, id, now);
        var changed = new Events.OrderStatusChanged(id, id, "PREPARING_ORDER", "SHIPPED", now);
        listener.onOrderCreated(created);
        listener.onOrderStatusChanged(changed);

        verify(dispatcher).onOrderCreated(created);
        verify(dispatcher).onOrderStatusChanged(changed);
    }

    @Test
    void payment_listener_delegates() {
        PaymentNotificationDispatcher dispatcher = mock(PaymentNotificationDispatcher.class);
        KafkaPaymentNotificationListener listener = new KafkaPaymentNotificationListener(dispatcher);

        var confirmed = new Events.PaymentConfirmed(id, id, now);
        var submitted = new Events.PaymentSubmitted(id, "x.pdf", now);
        var rejected = new Events.PaymentRejected(id, id, id, now);
        listener.onPaymentConfirmed(confirmed);
        listener.onPaymentSubmitted(submitted);
        listener.onPaymentRejected(rejected);

        verify(dispatcher).onPaymentConfirmed(confirmed);
        verify(dispatcher).onPaymentSubmitted(submitted);
        verify(dispatcher).onPaymentRejected(rejected);
    }

    @Test
    void payment_registered_listener_delegates() {
        PaymentRegisteredNotificationDispatcher dispatcher = mock(PaymentRegisteredNotificationDispatcher.class);
        var event = new Events.PaymentRegistered(id, id, now);
        new KafkaPaymentRegisteredNotificationListener(dispatcher).onPaymentRegistered(event);
        verify(dispatcher).onPaymentRegistered(event);
    }

    @Test
    void billing_listener_delegates() {
        BillingNotificationDispatcher dispatcher = mock(BillingNotificationDispatcher.class);
        var event = new Events.SalesDocumentIssued(id, id, "12345", now);
        new KafkaBillingNotificationListener(dispatcher).onSalesDocumentIssued(event);
        verify(dispatcher).onSalesDocumentIssued(event);
    }

    @Test
    void return_listener_delegates() {
        ReturnNotificationDispatcher dispatcher = mock(ReturnNotificationDispatcher.class);
        KafkaReturnNotificationListener listener = new KafkaReturnNotificationListener(dispatcher);

        var requested = new Events.ReturnRequested(id, id, "RETRACTO", now);
        var approved = new Events.ReturnApproved(id, id, now);
        var refunded = new Events.RefundRegistered(id, id, now);
        listener.onReturnRequested(requested);
        listener.onReturnApproved(approved);
        listener.onRefundRegistered(refunded);

        verify(dispatcher).onReturnRequested(requested);
        verify(dispatcher).onReturnApproved(approved);
        verify(dispatcher).onRefundRegistered(refunded);
    }

    @Test
    void user_listener_delegates() {
        UserNotificationDispatcher dispatcher = mock(UserNotificationDispatcher.class);
        var event = new Events.UserRegistered(id, now, null);
        new KafkaUserNotificationListener(dispatcher).onUserRegistered(event);
        verify(dispatcher).onUserRegistered(event);
    }

    @Test
    void discount_listener_delegates() {
        DiscountNotificationDispatcher dispatcher = mock(DiscountNotificationDispatcher.class);
        var event = new Events.DiscountCodeAssigned(id, "CODE", id, now);
        new KafkaDiscountNotificationListener(dispatcher).onDiscountCodeAssigned(event);
        verify(dispatcher).onDiscountCodeAssigned(event);
    }
}
