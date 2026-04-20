package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.ports.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void sendOrderConfirmation(UUID orderId, String customerEmail) {
        log.info("[NOTIFICATION] ORDER_CONFIRMATION orderId={} email={}", orderId, customerEmail);
    }

    @Override
    public void sendPaymentReceived(UUID paymentId, String customerEmail) {
        log.info("[NOTIFICATION] PAYMENT_RECEIVED paymentId={} email={}", paymentId, customerEmail);
    }

    @Override
    public void sendOrderShipped(UUID orderId, String customerEmail) {
        log.info("[NOTIFICATION] ORDER_SHIPPED orderId={} email={}", orderId, customerEmail);
    }
}
