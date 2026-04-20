package com.pilarestilo.notification.domain.ports;

import java.util.UUID;

public interface NotificationSender {

    void sendOrderConfirmation(UUID orderId, String customerEmail);

    void sendPaymentReceived(UUID paymentId, String customerEmail);

    void sendOrderShipped(UUID orderId, String customerEmail);
}
