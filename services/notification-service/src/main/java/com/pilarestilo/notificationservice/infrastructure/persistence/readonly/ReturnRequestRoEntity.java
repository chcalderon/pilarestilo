package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read-only view of {@code return_requests}. */
@Entity
@Immutable
@Table(name = "return_requests")
public class ReturnRequestRoEntity {

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "kind")
    private String kind;

    @Column(name = "reason")
    private String reason;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "refund_currency")
    private String refundCurrency;

    @Column(name = "refund_method")
    private String refundMethod;

    @Column(name = "refund_reference")
    private String refundReference;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getKind() { return kind; }
    public String getReason() { return reason; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public String getRefundCurrency() { return refundCurrency; }
    public String getRefundMethod() { return refundMethod; }
    public String getRefundReference() { return refundReference; }
    public Instant getRefundedAt() { return refundedAt; }
}
