package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.application.PaymentRegisteredNotificationDispatcher;
import com.pilarestilo.payment.domain.events.PaymentRegistered;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * In-process transport for {@code PaymentRegistered}. Dead whenever Kafka is enabled — the
 * behaviour lives in {@link PaymentRegisteredNotificationDispatcher}, which both transports share.
 */
@Component
public class PaymentRegisteredNotificationListener {

    private final PaymentRegisteredNotificationDispatcher dispatcher;

    public PaymentRegisteredNotificationListener(PaymentRegisteredNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void onPaymentRegistered(PaymentRegistered event) {
        dispatcher.onPaymentRegistered(event);
    }
}
