package com.pilarestilo.returns.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "return_requests")
public class ReturnRequestEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /*
     * kind, status and item_disposition are plain Strings rather than @Enumerated: each column
     * carries a CHECK constraint, so the database is already the authority on the allowed values.
     */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "item_disposition", length = 30)
    private String itemDisposition;

    @Column(name = "disposition_at")
    private Instant dispositionAt;

    @Column(name = "disposition_note", length = 500)
    private String dispositionNote;

    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_currency", length = 10)
    private String refundCurrency;

    @Column(name = "refund_method", length = 30)
    private String refundMethod;

    @Column(name = "refund_reference", length = 200)
    private String refundReference;

    @Column(name = "refund_file_url", length = 500)
    private String refundFileUrl;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_account_holder", length = 160)
    private String refundAccountHolder;

    @Column(name = "refund_account_rut", length = 20)
    private String refundAccountRut;

    @Column(name = "refund_bank_name", length = 120)
    private String refundBankName;

    @Column(name = "refund_account_type", length = 80)
    private String refundAccountType;

    /** AES/GCM, same scheme as the shop secrets. Erased once the refund settles. */
    @Column(name = "refund_account_encrypted", columnDefinition = "TEXT")
    private String refundAccountEncrypted;

    @Column(name = "refund_account_last4", length = 4)
    private String refundAccountLast4;

    @Column(name = "credit_note_id")
    private UUID creditNoteId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(Instant deadlineAt) { this.deadlineAt = deadlineAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public String getItemDisposition() { return itemDisposition; }
    public void setItemDisposition(String itemDisposition) { this.itemDisposition = itemDisposition; }
    public Instant getDispositionAt() { return dispositionAt; }
    public void setDispositionAt(Instant dispositionAt) { this.dispositionAt = dispositionAt; }
    public String getDispositionNote() { return dispositionNote; }
    public void setDispositionNote(String dispositionNote) { this.dispositionNote = dispositionNote; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getRefundCurrency() { return refundCurrency; }
    public void setRefundCurrency(String refundCurrency) { this.refundCurrency = refundCurrency; }
    public String getRefundMethod() { return refundMethod; }
    public void setRefundMethod(String refundMethod) { this.refundMethod = refundMethod; }
    public String getRefundReference() { return refundReference; }
    public void setRefundReference(String refundReference) { this.refundReference = refundReference; }
    public String getRefundFileUrl() { return refundFileUrl; }
    public void setRefundFileUrl(String refundFileUrl) { this.refundFileUrl = refundFileUrl; }
    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }
    public String getRefundAccountHolder() { return refundAccountHolder; }
    public void setRefundAccountHolder(String v) { this.refundAccountHolder = v; }
    public String getRefundAccountRut() { return refundAccountRut; }
    public void setRefundAccountRut(String v) { this.refundAccountRut = v; }
    public String getRefundBankName() { return refundBankName; }
    public void setRefundBankName(String v) { this.refundBankName = v; }
    public String getRefundAccountType() { return refundAccountType; }
    public void setRefundAccountType(String v) { this.refundAccountType = v; }
    public String getRefundAccountEncrypted() { return refundAccountEncrypted; }
    public void setRefundAccountEncrypted(String v) { this.refundAccountEncrypted = v; }
    public String getRefundAccountLast4() { return refundAccountLast4; }
    public void setRefundAccountLast4(String v) { this.refundAccountLast4 = v; }
    public UUID getCreditNoteId() { return creditNoteId; }
    public void setCreditNoteId(UUID creditNoteId) { this.creditNoteId = creditNoteId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
