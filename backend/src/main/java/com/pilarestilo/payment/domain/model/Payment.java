package com.pilarestilo.payment.domain.model;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

public class Payment {

    private UUID id;
    private UUID orderId;
    private PaymentMethod method;
    private PaymentStatus status;
    private String proofReference;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private Instant createdAt;

    private Payment() {}

    public static Payment create(UUID orderId, PaymentMethod method) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.orderId = orderId;
        p.method = method;
        p.status = PaymentStatus.PENDING;
        p.createdAt = Instant.now();
        return p;
    }

    /**
     * Reconstructs a Payment from persistence without triggering business-rule validation.
     * Only for use by repository adapters.
     */
    public static Payment reconstruct(UUID id, UUID orderId, PaymentMethod method,
                                       PaymentStatus status, String proofReference,
                                       UUID reviewedBy, Instant reviewedAt, Instant createdAt) {
        Payment p = new Payment();
        p.id = id;
        p.orderId = orderId;
        p.method = method;
        p.status = status;
        p.proofReference = proofReference;
        p.reviewedBy = reviewedBy;
        p.reviewedAt = reviewedAt;
        p.createdAt = createdAt;
        return p;
    }

    public void submitProof(String proofReference) {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.SUBMITTED) {
            throw new DomainException("Cannot submit proof in status " + status);
        }
        this.proofReference = proofReference;
        this.status = PaymentStatus.SUBMITTED;
    }

    public void markUnderReview() {
        if (status != PaymentStatus.SUBMITTED) {
            throw new DomainException("Must be SUBMITTED to mark under review");
        }
        this.status = PaymentStatus.UNDER_REVIEW;
    }

    public void approve(UUID reviewerId) {
        if (status != PaymentStatus.UNDER_REVIEW) {
            throw new DomainException("Must be UNDER_REVIEW to approve");
        }
        this.status = PaymentStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
    }

    public void reject(UUID reviewerId) {
        if (status != PaymentStatus.UNDER_REVIEW) {
            throw new DomainException("Must be UNDER_REVIEW to reject");
        }
        this.status = PaymentStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
    }

    public boolean confirmByGateway() {
        if (status == PaymentStatus.APPROVED) {
            return false;
        }
        if (status == PaymentStatus.REJECTED) {
            throw new DomainException("Cannot confirm payment already rejected");
        }
        this.status = PaymentStatus.APPROVED;
        this.reviewedBy = null;
        this.reviewedAt = Instant.now();
        return true;
    }

    public boolean rejectByGateway() {
        if (status == PaymentStatus.REJECTED) {
            return false;
        }
        if (status == PaymentStatus.APPROVED) {
            throw new DomainException("Cannot reject payment already approved");
        }
        this.status = PaymentStatus.REJECTED;
        this.reviewedBy = null;
        this.reviewedAt = Instant.now();
        return true;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getProofReference() { return proofReference; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
