package com.pilarestilo.paymentservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "method", nullable = false, length = 30)
    private String method;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "proof_reference", length = 500)
    private String proofReference;

    @Column(name = "transfer_account_holder_name", length = 160)
    private String transferAccountHolderName;

    @Column(name = "transfer_account_email", length = 255)
    private String transferAccountEmail;

    @Column(name = "transfer_account_number", length = 120)
    private String transferAccountNumber;

    @Column(name = "transfer_account_type", length = 80)
    private String transferAccountType;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProofReference() {
        return proofReference;
    }

    public void setProofReference(String proofReference) {
        this.proofReference = proofReference;
    }

    public String getTransferAccountHolderName() {
        return transferAccountHolderName;
    }

    public void setTransferAccountHolderName(String transferAccountHolderName) {
        this.transferAccountHolderName = transferAccountHolderName;
    }

    public String getTransferAccountEmail() {
        return transferAccountEmail;
    }

    public void setTransferAccountEmail(String transferAccountEmail) {
        this.transferAccountEmail = transferAccountEmail;
    }

    public String getTransferAccountNumber() {
        return transferAccountNumber;
    }

    public void setTransferAccountNumber(String transferAccountNumber) {
        this.transferAccountNumber = transferAccountNumber;
    }

    public String getTransferAccountType() {
        return transferAccountType;
    }

    public void setTransferAccountType(String transferAccountType) {
        this.transferAccountType = transferAccountType;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(UUID reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
