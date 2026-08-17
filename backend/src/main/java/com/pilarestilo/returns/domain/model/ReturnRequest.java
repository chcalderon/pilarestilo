package com.pilarestilo.returns.domain.model;

import com.pilarestilo.returns.domain.enums.ItemDisposition;
import com.pilarestilo.returns.domain.enums.RefundMethod;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.domain.enums.ReturnStatus;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A sale being undone after it was delivered.
 *
 * <p>The aggregate carries <strong>two independent tracks</strong>, and keeping them apart is the
 * point of its design:
 *
 * <ul>
 *   <li>{@link ReturnStatus} is where the money stands. The Ley 19.496 gives the shop forty-five
 *       days to return it, so {@link #deadlineAt} is stored rather than recomputed — a deadline the
 *       screen can show beats one somebody has to remember.</li>
 *   <li>{@link ItemDisposition} is where the garment stands. Every returned garment is cleaned,
 *       pressed, sanitised and repaired before it can be sold again, so receiving it puts nothing
 *       back on the shelf.</li>
 * </ul>
 *
 * <p>Chaining them would let a delay in the workshop breach a legal deadline, which is why a refund
 * can be registered while the garment is still being reconditioned.
 */
public class ReturnRequest {

    /** Ley 19.496 art. 3 bis: the money goes back "a la mayor brevedad" and within forty-five days. */
    public static final Duration REFUND_DEADLINE = Duration.ofDays(45);

    private UUID id;
    private UUID orderId;
    private ReturnKind kind;
    private ReturnStatus status;
    private String reason;
    private UUID requestedBy;
    private Instant requestedAt;
    private Instant deadlineAt;
    private Instant resolvedAt;
    private String resolutionNote;
    private ItemDisposition itemDisposition;
    private Instant dispositionAt;
    private String dispositionNote;
    private Money refundAmount;
    private RefundMethod refundMethod;
    private String refundReference;
    private String refundFileUrl;
    private Instant refundedAt;
    private RefundAccount refundAccount;
    private UUID creditNoteId;
    private Instant createdAt;

    private ReturnRequest() {}

    /**
     * @param requestedBy the customer, when she opens it herself; null when the shop does
     */
    public static ReturnRequest open(UUID orderId, ReturnKind kind, String reason, UUID requestedBy) {
        if (orderId == null) {
            throw new DomainException("A return needs the order it undoes");
        }
        if (kind == null) {
            throw new DomainException("A return needs to say whether it is a retracto");
        }
        if (reason == null || reason.isBlank()) {
            throw new DomainException("A return needs a reason");
        }

        ReturnRequest request = new ReturnRequest();
        request.id = UUID.randomUUID();
        request.orderId = orderId;
        request.kind = kind;
        request.status = ReturnStatus.REQUESTED;
        request.reason = reason.trim();
        request.requestedBy = requestedBy;
        request.requestedAt = Instant.now();
        request.deadlineAt = request.requestedAt.plus(REFUND_DEADLINE);
        request.createdAt = request.requestedAt;
        return request;
    }

    public void approve() {
        assertStatus(ReturnStatus.REQUESTED);
        this.status = ReturnStatus.APPROVED;
    }

    /**
     * A retracto opened within its window is a right the customer exercised, not a request the shop
     * weighs. Only a {@link ReturnKind#DEVOLUCION} can be refused, and only with a reason.
     */
    public void reject(String note) {
        assertStatus(ReturnStatus.REQUESTED);
        if (kind == ReturnKind.RETRACTO) {
            throw new DomainException(
                    "A retracto within its window cannot be refused; it is a right, not a request");
        }
        if (note == null || note.isBlank()) {
            throw new DomainException("Refusing a return requires a reason");
        }
        this.status = ReturnStatus.REJECTED;
        this.resolutionNote = note.trim();
        this.resolvedAt = Instant.now();
    }

    /**
     * The garment arrived. It is not back on the shelf: it goes into reconditioning, and only
     * {@link #resolveDisposition} decides whether it ever returns.
     */
    public void receive() {
        assertStatus(ReturnStatus.APPROVED);
        this.status = ReturnStatus.RECEIVED;
        this.itemDisposition = ItemDisposition.PENDING_RECONDITIONING;
        this.dispositionAt = Instant.now();
    }

    public void resolveDisposition(ItemDisposition disposition, String note) {
        if (itemDisposition != ItemDisposition.PENDING_RECONDITIONING) {
            throw new DomainException("This return has no garment awaiting reconditioning");
        }
        if (disposition != ItemDisposition.RESTOCKED && disposition != ItemDisposition.DISCARDED) {
            throw new DomainException("A garment is either put back on sale or discarded");
        }
        if (disposition == ItemDisposition.DISCARDED && (note == null || note.isBlank())) {
            throw new DomainException("Discarding a garment requires saying why");
        }
        this.itemDisposition = disposition;
        this.dispositionNote = note == null || note.isBlank() ? null : note.trim();
        this.dispositionAt = Instant.now();
    }

    /**
     * Registers the money going back. Allowed while the garment is still being reconditioned: the
     * two tracks are independent on purpose, and the refund is the one with a legal deadline.
     */
    public void registerRefund(Money amount, RefundMethod method, String reference, String fileUrl) {
        assertOneOf(Set.of(ReturnStatus.APPROVED, ReturnStatus.RECEIVED));
        if (amount == null || amount.amount().signum() <= 0) {
            throw new DomainException("A refund needs an amount");
        }
        if (method == null) {
            throw new DomainException("A refund needs to say how the money went back");
        }
        if (reference == null || reference.isBlank()) {
            throw new DomainException("A refund needs its operation reference");
        }
        this.refundAmount = amount;
        this.refundMethod = method;
        this.refundReference = reference.trim();
        this.refundFileUrl = fileUrl == null || fileUrl.isBlank() ? null : fileUrl.trim();
        this.refundedAt = Instant.now();
        this.resolvedAt = this.refundedAt;
        this.status = ReturnStatus.REFUNDED;
        // The account number has done its work. What is left identifies the payment; keeping the
        // number would be holding a customer's bank details for no remaining purpose.
        if (refundAccount != null) {
            this.refundAccount = refundAccount.erased();
        }
    }

    public void attachRefundAccount(RefundAccount account) {
        if (status == ReturnStatus.REFUNDED || status == ReturnStatus.REJECTED) {
            throw new DomainException("This return is closed");
        }
        this.refundAccount = account;
    }

    public void attachCreditNote(UUID documentId) {
        if (documentId == null) {
            throw new DomainException("Attaching a credit note requires the document");
        }
        this.creditNoteId = documentId;
    }

    public boolean isOpen() {
        return status != ReturnStatus.REFUNDED && status != ReturnStatus.REJECTED;
    }

    /** Days left to return the money. Negative once the legal deadline has passed. */
    public long daysUntilDeadline(Instant now) {
        return Duration.between(now, deadlineAt).toDays();
    }

    private void assertStatus(ReturnStatus expected) {
        if (status != expected) {
            throw new DomainException("Cannot do that to a return that is " + status);
        }
    }

    private void assertOneOf(Set<ReturnStatus> expected) {
        if (!expected.contains(status)) {
            throw new DomainException("Cannot do that to a return that is " + status);
        }
    }

    public static ReturnRequest reconstruct(
            UUID id, UUID orderId, ReturnKind kind, ReturnStatus status, String reason,
            UUID requestedBy, Instant requestedAt, Instant deadlineAt, Instant resolvedAt,
            String resolutionNote, ItemDisposition itemDisposition, Instant dispositionAt,
            String dispositionNote, Money refundAmount, RefundMethod refundMethod,
            String refundReference, String refundFileUrl, Instant refundedAt,
            RefundAccount refundAccount, UUID creditNoteId, Instant createdAt) {
        ReturnRequest request = new ReturnRequest();
        request.id = id;
        request.orderId = orderId;
        request.kind = kind;
        request.status = status;
        request.reason = reason;
        request.requestedBy = requestedBy;
        request.requestedAt = requestedAt;
        request.deadlineAt = deadlineAt;
        request.resolvedAt = resolvedAt;
        request.resolutionNote = resolutionNote;
        request.itemDisposition = itemDisposition;
        request.dispositionAt = dispositionAt;
        request.dispositionNote = dispositionNote;
        request.refundAmount = refundAmount;
        request.refundMethod = refundMethod;
        request.refundReference = refundReference;
        request.refundFileUrl = refundFileUrl;
        request.refundedAt = refundedAt;
        request.refundAccount = refundAccount;
        request.creditNoteId = creditNoteId;
        request.createdAt = createdAt;
        return request;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public ReturnKind getKind() { return kind; }
    public ReturnStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public UUID getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolutionNote() { return resolutionNote; }
    public ItemDisposition getItemDisposition() { return itemDisposition; }
    public Instant getDispositionAt() { return dispositionAt; }
    public String getDispositionNote() { return dispositionNote; }
    public Money getRefundAmount() { return refundAmount; }
    public RefundMethod getRefundMethod() { return refundMethod; }
    public String getRefundReference() { return refundReference; }
    public String getRefundFileUrl() { return refundFileUrl; }
    public Instant getRefundedAt() { return refundedAt; }
    public RefundAccount getRefundAccount() { return refundAccount; }
    public UUID getCreditNoteId() { return creditNoteId; }
    public Instant getCreatedAt() { return createdAt; }
}
