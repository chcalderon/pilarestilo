package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.application.PaymentNotificationDispatcher;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * In-process transport. Dead whenever Kafka is enabled — see {@link
 * PaymentNotificationDispatcher}, which holds the behaviour both transports share.
 */
@Component
public class PaymentNotificationListener {

    private final PaymentNotificationDispatcher dispatcher;

    public PaymentNotificationListener(PaymentNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void onPaymentConfirmed(PaymentConfirmed event) {
        dispatcher.onPaymentConfirmed(event);
    }

    @EventListener
    public void onPaymentRejected(PaymentRejected event) {
        dispatcher.onPaymentRejected(event);
    }
}
