package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentNotificationListener {

    private final NotificationSender notificationSender;

    public PaymentNotificationListener(NotificationSender notificationSender) {
        this.notificationSender = notificationSender;
    }

    @EventListener
    public void onPaymentConfirmed(PaymentConfirmed event) {
        notificationSender.sendPaymentReceived(event.paymentId(), "unknown");
    }
}
