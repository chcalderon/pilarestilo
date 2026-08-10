package com.pilarestilo.notification.domain.ports;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;

import java.util.UUID;

public interface NotificationSender {

    void sendOrderConfirmation(UUID orderId, NotificationRecipient recipient);

    void sendPaymentReceived(UUID paymentId, NotificationRecipient recipient);

    void sendOrderPreparing(UUID orderId, NotificationRecipient recipient);

    void sendOrderShipped(UUID orderId, NotificationRecipient recipient);

    void sendDiscountCodeAssigned(String code, NotificationRecipient recipient);

    /**
     * Renders a composed message.
     *
     * <p>Abstract on purpose. This replaced {@code default void sendOrderCancelled(...) {}}, whose
     * empty body was a compile-time licence for an adapter to do nothing: only the LOG sender ever
     * implemented it, so under EMAIL_SMTP, SendGrid, WhatsApp or n8n a customer whose order was
     * auto-cancelled was never told. With no default, a new channel cannot be added without
     * deciding what every message does.
     */
    void send(NotificationMessage message, NotificationRecipient recipient);
}
