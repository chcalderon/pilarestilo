package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void sendOrderConfirmation(UUID orderId, NotificationRecipient recipient) {
        log.info("[NOTIFICATION] ORDER_CONFIRMATION orderId={} recipient={}", orderId, recipient.preferredEmailThenPhone());
    }

    @Override
    public void sendPaymentReceived(UUID paymentId, NotificationRecipient recipient) {
        log.info("[NOTIFICATION] PAYMENT_RECEIVED paymentId={} recipient={}", paymentId, recipient.preferredEmailThenPhone());
    }

    @Override
    public void sendOrderPreparing(UUID orderId, NotificationRecipient recipient) {
        log.info("[NOTIFICATION] ORDER_PREPARING orderId={} recipient={}", orderId, recipient.preferredEmailThenPhone());
    }

    @Override
    public void sendOrderShipped(UUID orderId, NotificationRecipient recipient) {
        log.info("[NOTIFICATION] ORDER_SHIPPED orderId={} recipient={}", orderId, recipient.preferredEmailThenPhone());
    }

    @Override
    public void sendDiscountCodeAssigned(String code, NotificationRecipient recipient) {
        log.info("[NOTIFICATION] DISCOUNT_CODE_ASSIGNED code={} recipient={}", code, recipient.preferredEmailThenPhone());
    }
}
