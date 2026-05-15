package com.pilarestilo.payment.infrastructure.persistence.entities;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "proof_reference", length = 500)
    private String proofReference;

    @Column(name = "transfer_account_holder_name", length = 160)
    private String transferAccountHolderName;

    @Column(name = "transfer_account_email", length = 255)
    private String transferAccountEmail;

    @Column(name = "transfer_account_number", length = 120)
    private String transferAccountNumber;

    @Column(name = "transfer_bank_name", length = 120)
    private String transferBankName;

    @Column(name = "transfer_account_type", length = 80)
    private String transferAccountType;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getProofReference() { return proofReference; }
    public void setProofReference(String proofReference) { this.proofReference = proofReference; }
    public String getTransferAccountHolderName() { return transferAccountHolderName; }
    public void setTransferAccountHolderName(String transferAccountHolderName) {
        this.transferAccountHolderName = transferAccountHolderName;
    }
    public String getTransferAccountEmail() { return transferAccountEmail; }
    public void setTransferAccountEmail(String transferAccountEmail) { this.transferAccountEmail = transferAccountEmail; }
    public String getTransferAccountNumber() { return transferAccountNumber; }
    public void setTransferAccountNumber(String transferAccountNumber) { this.transferAccountNumber = transferAccountNumber; }
    public String getTransferBankName() { return transferBankName; }
    public void setTransferBankName(String transferBankName) { this.transferBankName = transferBankName; }
    public String getTransferAccountType() { return transferAccountType; }
    public void setTransferAccountType(String transferAccountType) { this.transferAccountType = transferAccountType; }

    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
