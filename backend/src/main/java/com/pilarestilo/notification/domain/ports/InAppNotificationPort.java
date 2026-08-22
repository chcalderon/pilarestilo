package com.pilarestilo.notification.domain.ports;

import java.util.UUID;

public interface InAppNotificationPort {
    void notifyDiscountCodeAssigned(UUID userId, String code);
    void notifyOrderConfirmed(UUID userId, UUID orderId);
    void notifyPaymentReceived(UUID userId, UUID paymentId);
    void notifyOrderPreparing(UUID userId, UUID orderId);
    void notifyOrderShipped(UUID userId, UUID orderId);

    /** Reaches the customer whether they confirmed the delivery or the job did it for them. */
    void notifyOrderDelivered(UUID userId, UUID orderId);

    /** {@code couponCode} is null when no welcome coupon was issued. */
    void notifyWelcome(UUID userId, String couponCode);
}
