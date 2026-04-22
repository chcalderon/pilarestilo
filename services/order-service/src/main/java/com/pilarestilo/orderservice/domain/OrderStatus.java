package com.pilarestilo.orderservice.domain;

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
