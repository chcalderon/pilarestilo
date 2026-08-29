package com.pilarestilo.notificationservice.events;

import com.pilarestilo.notificationservice.domain.view.WelcomeDiscount;

import java.time.Instant;
import java.util.UUID;

/**
 * Thin copies of the monolith's domain events — only the fields this service reads, with the same
 * JSON names so Jackson binds the message body by field. The monolith's {@code __TypeId__} header
 * is ignored (see {@code KafkaConsumerConfig}); the {@code @KafkaListener} method parameter type is
 * what selects the record.
 *
 * <p>Each record's components are verified against the monolith's
 * {@code backend/.../domain/events/} records.
 */
public final class Events {

    private Events() {}

    /** {@code com.pilarestilo.order.domain.events.OrderCreated} */
    public record OrderCreated(UUID orderId, UUID customerId, Instant occurredAt) {}

    /** {@code com.pilarestilo.order.domain.events.OrderStatusChanged} — statuses are enum names. */
    public record OrderStatusChanged(UUID orderId, UUID customerId, String previousStatus,
                                     String newStatus, Instant occurredAt) {}

    /** {@code com.pilarestilo.payment.domain.events.PaymentConfirmed} */
    public record PaymentConfirmed(UUID paymentId, UUID orderId, Instant occurredAt) {}

    /** {@code com.pilarestilo.payment.domain.events.PaymentRegistered} */
    public record PaymentRegistered(UUID paymentId, UUID orderId, Instant occurredAt) {}

    /** {@code com.pilarestilo.payment.domain.events.PaymentSubmitted} */
    public record PaymentSubmitted(UUID paymentId, String proofReference, Instant occurredAt) {}

    /** {@code com.pilarestilo.payment.domain.events.PaymentRejected} */
    public record PaymentRejected(UUID paymentId, UUID orderId, UUID reviewerId, Instant occurredAt) {}

    /** {@code com.pilarestilo.billing.domain.events.SalesDocumentIssued} */
    public record SalesDocumentIssued(UUID documentId, UUID orderId, String folio, Instant occurredAt) {}

    /** {@code com.pilarestilo.returns.domain.events.ReturnRequested} */
    public record ReturnRequested(UUID returnId, UUID orderId, String kind, Instant occurredAt) {}

    /** {@code com.pilarestilo.returns.domain.events.ReturnApproved} */
    public record ReturnApproved(UUID returnId, UUID orderId, Instant occurredAt) {}

    /** {@code com.pilarestilo.returns.domain.events.RefundRegistered} */
    public record RefundRegistered(UUID returnId, UUID orderId, Instant occurredAt) {}

    /** {@code com.pilarestilo.user.domain.events.UserRegistered} */
    public record UserRegistered(UUID userId, Instant occurredAt, WelcomeDiscount welcomeDiscount) {}

    /** {@code com.pilarestilo.discount.domain.events.DiscountCodeAssigned} */
    public record DiscountCodeAssigned(UUID discountId, String code, UUID assignedUserId,
                                       Instant occurredAt) {}
}
