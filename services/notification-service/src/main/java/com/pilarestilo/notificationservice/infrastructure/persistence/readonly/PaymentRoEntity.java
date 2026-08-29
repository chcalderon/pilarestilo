package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Read-only view of {@code payments}: the bank-transfer snapshot and the review outcome. */
@Entity
@Immutable
@Table(name = "payments")
public class PaymentRoEntity {

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "method")
    private String method;

    @Column(name = "status")
    private String status;

    @Column(name = "proof_reference")
    private String proofReference;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "transfer_account_holder_name")
    private String transferAccountHolderName;

    @Column(name = "transfer_bank_name")
    private String transferBankName;

    @Column(name = "transfer_account_type")
    private String transferAccountType;

    @Column(name = "transfer_account_number")
    private String transferAccountNumber;

    @Column(name = "transfer_account_email")
    private String transferAccountEmail;

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getMethod() { return method; }
    public String getStatus() { return status; }
    public String getProofReference() { return proofReference; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTransferAccountHolderName() { return transferAccountHolderName; }
    public String getTransferBankName() { return transferBankName; }
    public String getTransferAccountType() { return transferAccountType; }
    public String getTransferAccountNumber() { return transferAccountNumber; }
    public String getTransferAccountEmail() { return transferAccountEmail; }
}
