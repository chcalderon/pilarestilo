package com.pilarestilo.notification.domain.ports;

import com.pilarestilo.notification.domain.model.NotificationRecipient;

import java.util.UUID;

public interface NotificationSender {

    void sendOrderConfirmation(UUID orderId, NotificationRecipient recipient);

    void sendPaymentReceived(UUID paymentId, NotificationRecipient recipient);

    void sendOrderShipped(UUID orderId, NotificationRecipient recipient);

    void sendDiscountCodeAssigned(String code, NotificationRecipient recipient);
}
