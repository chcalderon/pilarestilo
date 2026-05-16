package com.pilarestilo.payment.domain.model;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

public class Payment {

    public static final UUID SYSTEM_REVIEWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private UUID id;
    private UUID orderId;
    private PaymentMethod method;
    private PaymentStatus status;
    private String proofReference;
    private String transferAccountHolderName;
    private String transferAccountEmail;
    private String transferAccountNumber;
    private String transferBankName;
    private String transferAccountType;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private Instant createdAt;
    private String rejectionReason;

    private Payment() {}

    public static Payment create(UUID orderId, PaymentMethod method) {
        return create(orderId, method, null, null, null, null, null);
    }

    public static Payment create(UUID orderId, PaymentMethod method,
                                 String transferAccountHolderName,
                                 String transferAccountEmail,
                                 String transferAccountNumber,
                                 String transferBankName,
                                 String transferAccountType) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.orderId = orderId;
        p.method = method;
        p.transferAccountHolderName = normalizeNullable(transferAccountHolderName);
        p.transferAccountEmail = normalizeNullable(transferAccountEmail);
        p.transferAccountNumber = normalizeNullable(transferAccountNumber);
        p.transferBankName = normalizeNullable(transferBankName);
        p.transferAccountType = normalizeNullable(transferAccountType);
        p.validateTransferSnapshot();
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
                                       String transferAccountHolderName,
                                       String transferAccountEmail,
                                       String transferAccountNumber,
                                       String transferBankName,
                                       String transferAccountType,
                                       UUID reviewedBy, Instant reviewedAt, Instant createdAt,
                                       String rejectionReason) {
        Payment p = new Payment();
        p.id = id;
        p.orderId = orderId;
        p.method = method;
        p.status = status;
        p.proofReference = proofReference;
        p.transferAccountHolderName = transferAccountHolderName;
        p.transferAccountEmail = transferAccountEmail;
        p.transferAccountNumber = transferAccountNumber;
        p.transferBankName = transferBankName;
        p.transferAccountType = transferAccountType;
        p.reviewedBy = reviewedBy;
        p.reviewedAt = reviewedAt;
        p.createdAt = createdAt;
        p.rejectionReason = rejectionReason;
        return p;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateTransferSnapshot() {
        if (method != PaymentMethod.TRANSFER) {
            return;
        }
        if (transferAccountHolderName == null || transferAccountHolderName.isBlank()) {
            throw new DomainException("Transfer account holder snapshot is required for TRANSFER payments");
        }
        if (transferAccountEmail == null || transferAccountEmail.isBlank()) {
            throw new DomainException("Transfer account email snapshot is required for TRANSFER payments");
        }
        if (transferAccountNumber == null || transferAccountNumber.isBlank()) {
            throw new DomainException("Transfer account number snapshot is required for TRANSFER payments");
        }
        if (transferBankName == null || transferBankName.isBlank()) {
            throw new DomainException("Transfer bank name snapshot is required for TRANSFER payments");
        }
        if (transferAccountType == null || transferAccountType.isBlank()) {
            throw new DomainException("Transfer account type snapshot is required for TRANSFER payments");
        }
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

    public void systemCancel(String reason) {
        if (status != PaymentStatus.PENDING) {
            throw new DomainException("Only PENDING payments can be system-cancelled, got " + status);
        }
        if (method != PaymentMethod.TRANSFER) {
            throw new DomainException("System cancellation only supports TRANSFER");
        }
        this.status = PaymentStatus.REJECTED;
        this.reviewedBy = SYSTEM_REVIEWER_ID;
        this.reviewedAt = Instant.now();
        this.rejectionReason = reason;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public String getProofReference() { return proofReference; }
    public String getTransferAccountHolderName() { return transferAccountHolderName; }
    public String getTransferAccountEmail() { return transferAccountEmail; }
    public String getTransferAccountNumber() { return transferAccountNumber; }
    public String getTransferBankName() { return transferBankName; }
    public String getTransferAccountType() { return transferAccountType; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public String getRejectionReason() { return rejectionReason; }
}
