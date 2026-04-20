package com.pilarestilo.order.domain.enums;

public enum OrderStatus {
    CREATED,
    PENDING_PAYMENT,
    PAYMENT_UNDER_REVIEW,
    PAID,
    PREPARING_ORDER,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
