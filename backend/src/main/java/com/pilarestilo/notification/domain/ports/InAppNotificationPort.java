package com.pilarestilo.notification.domain.ports;

import java.util.UUID;

public interface InAppNotificationPort {
    void notifyDiscountCodeAssigned(UUID userId, String code);
    void notifyOrderConfirmed(UUID userId, UUID orderId);
    void notifyPaymentReceived(UUID userId, UUID paymentId);
    void notifyOrderShipped(UUID userId, UUID orderId);
}
