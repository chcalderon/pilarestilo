package com.pilarestilo.billing.domain.model;

import com.pilarestilo.billing.domain.enums.SalesDocumentStatus;
import com.pilarestilo.billing.domain.enums.SalesDocumentType;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.application.TaxBreakdown;
import com.pilarestilo.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A boleta or factura backing one sale.
 *
 * <p>The document is issued outside this system today — by hand in the SII's eBoleta app — so what
 * is recorded here is the folio, the amounts it carries and, optionally, the file itself. Nothing
 * about it is editable: a mistake is corrected by voiding this document and issuing another, which
 * is why {@code replacesDocumentId} exists and why the amounts are a snapshot rather than a read of
 * the order.
 *
 * <p>The buyer's name and email are copied in for the same reason. A tax document is kept for six
 * years, and the user row behind it may be anonymised long before that under Ley 21.719.
 *
 * <p>A {@code NOTA_CREDITO} is the same record with two extra obligations: it names the document it
 * acts upon and says what it does to it. Nothing else about it differs, because it too is emitted by
 * hand and only recorded here.
 */
public class SalesDocument {

    private UUID id;
    private UUID orderId;
    private SalesDocumentType type;
    private String folio;
    private Instant issuedAt;
    private Money netAmount;
    private Money taxAmount;
    private BigDecimal taxRate;
    private Money totalAmount;
    private String receiverRut;
    private String receiverName;
    private String receiverEmail;
    private String fileUrl;
    private SalesDocumentStatus status;
    private Instant voidedAt;
    private String voidReason;
    private UUID voidedBy;
    private UUID replacesDocumentId;
    private Integer referenceCode;
    private UUID issuedBy;
    private Instant createdAt;

    private SalesDocument() {}

    public static SalesDocument issue(
            UUID orderId,
            SalesDocumentType type,
            String folio,
            TaxBreakdown amounts,
            String receiverRut,
            String receiverName,
            String receiverEmail,
            String fileUrl,
            UUID issuedBy,
            UUID replacesDocumentId
    ) {
        if (orderId == null) {
            throw new DomainException("Sales document requires an order");
        }
        if (type == null) {
            throw new DomainException("Sales document requires a type");
        }
        if (folio == null || folio.isBlank()) {
            throw new DomainException("Sales document requires a folio");
        }
        if (amounts == null) {
            throw new DomainException("Sales document requires its amounts");
        }
        if (issuedBy == null) {
            throw new DomainException("Sales document requires the user who issued it");
        }
        // A factura names its receiver; a boleta does not have to.
        if (type == SalesDocumentType.FACTURA && (receiverRut == null || receiverRut.isBlank())) {
            throw new DomainException("A factura requires the receiver RUT");
        }

        SalesDocument document = new SalesDocument();
        document.id = UUID.randomUUID();
        document.orderId = orderId;
        document.type = type;
        document.folio = folio.trim();
        document.issuedAt = Instant.now();
        document.netAmount = amounts.net();
        document.taxAmount = amounts.tax();
        document.taxRate = amounts.rate();
        document.totalAmount = amounts.total();
        document.receiverRut = trimToNull(receiverRut);
        document.receiverName = trimToNull(receiverName);
        document.receiverEmail = trimToNull(receiverEmail);
        document.fileUrl = trimToNull(fileUrl);
        document.status = SalesDocumentStatus.ISSUED;
        document.replacesDocumentId = replacesDocumentId;
        document.issuedBy = issuedBy;
        document.createdAt = document.issuedAt;
        return document;
    }

    /**
     * Registers the nota de credito that undoes a declared sale, in whole or in part.
     *
     * @param referenceCode what the SII asks a credit note to declare about the document it
     *                      references: {@link #REFERENCE_ANNULS}, {@link #REFERENCE_CORRECTS_TEXT}
     *                      or {@link #REFERENCE_CORRECTS_AMOUNT}
     */
    public static SalesDocument creditNote(
            UUID orderId,
            String folio,
            TaxBreakdown amounts,
            int referenceCode,
            SalesDocument references,
            String fileUrl,
            UUID issuedBy
    ) {
        if (references == null) {
            throw new DomainException("A credit note has to say which document it acts upon");
        }
        if (referenceCode != REFERENCE_ANNULS
                && referenceCode != REFERENCE_CORRECTS_TEXT
                && referenceCode != REFERENCE_CORRECTS_AMOUNT) {
            throw new DomainException(
                    "A credit note references with code 1 (annuls), 2 (corrects text) or 3 (corrects amount)");
        }

        SalesDocument document = issue(
                orderId,
                SalesDocumentType.NOTA_CREDITO,
                folio,
                amounts,
                references.getReceiverRut(),
                references.getReceiverName(),
                references.getReceiverEmail(),
                fileUrl,
                issuedBy,
                references.getId());
        document.referenceCode = referenceCode;
        return document;
    }

    /** The credit note leaves the referenced document without effect. */
    public static final int REFERENCE_ANNULS = 1;
    /** It corrects wording — a misspelled name — and moves no money. */
    public static final int REFERENCE_CORRECTS_TEXT = 2;
    /** It corrects an amount, which is what a partial refund needs. */
    public static final int REFERENCE_CORRECTS_AMOUNT = 3;

    public void voidDocument(String reason, UUID voidedBy) {
        if (status == SalesDocumentStatus.VOIDED) {
            throw new DomainException("Sales document " + folio + " is already voided");
        }
        if (reason == null || reason.isBlank()) {
            throw new DomainException("Voiding a sales document requires a reason");
        }
        if (voidedBy == null) {
            throw new DomainException("Voiding a sales document requires the user who did it");
        }
        this.status = SalesDocumentStatus.VOIDED;
        this.voidedAt = Instant.now();
        this.voidReason = reason.trim();
        this.voidedBy = voidedBy;
    }

    public boolean isLive() {
        return status == SalesDocumentStatus.ISSUED;
    }

    /**
     * Files the scan of a document that was registered without one.
     *
     * <p>Not a correction: nothing a tax authority reads changes, because the folio and the amounts
     * are what the document says and they stay exactly as issued. What changes is whether the shop
     * can find the paper again.
     *
     * <p>Only onto an empty slot, and only while the document stands. Replacing an attachment would
     * let the picture of a boleta be swapped for another one after the fact, which is the one thing
     * an append-only record exists to prevent; and a voided document keeps the file that proves
     * what was voided.
     */
    public void attachFile(String storedName) {
        if (!isLive()) {
            throw new DomainException("A voided document keeps the file it was voided with");
        }
        if (fileUrl != null) {
            throw new DomainException("This document already has a file; issue a correction instead of replacing it");
        }
        String trimmed = trimToNull(storedName);
        if (trimmed == null) {
            throw new DomainException("There is no file to attach");
        }
        this.fileUrl = trimmed;
    }

    public static SalesDocument reconstruct(
            UUID id,
            UUID orderId,
            SalesDocumentType type,
            String folio,
            Instant issuedAt,
            Money netAmount,
            Money taxAmount,
            BigDecimal taxRate,
            Money totalAmount,
            String receiverRut,
            String receiverName,
            String receiverEmail,
            String fileUrl,
            SalesDocumentStatus status,
            Instant voidedAt,
            String voidReason,
            UUID voidedBy,
            UUID replacesDocumentId,
            Integer referenceCode,
            UUID issuedBy,
            Instant createdAt
    ) {
        SalesDocument document = new SalesDocument();
        document.id = id;
        document.orderId = orderId;
        document.type = type;
        document.folio = folio;
        document.issuedAt = issuedAt;
        document.netAmount = netAmount;
        document.taxAmount = taxAmount;
        document.taxRate = taxRate;
        document.totalAmount = totalAmount;
        document.receiverRut = receiverRut;
        document.receiverName = receiverName;
        document.receiverEmail = receiverEmail;
        document.fileUrl = fileUrl;
        document.status = status;
        document.voidedAt = voidedAt;
        document.voidReason = voidReason;
        document.voidedBy = voidedBy;
        document.replacesDocumentId = replacesDocumentId;
        document.referenceCode = referenceCode;
        document.issuedBy = issuedBy;
        document.createdAt = createdAt;
        return document;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public SalesDocumentType getType() { return type; }
    public String getFolio() { return folio; }
    public Instant getIssuedAt() { return issuedAt; }
    public Money getNetAmount() { return netAmount; }
    public Money getTaxAmount() { return taxAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public Money getTotalAmount() { return totalAmount; }
    public String getReceiverRut() { return receiverRut; }
    public String getReceiverName() { return receiverName; }
    public String getReceiverEmail() { return receiverEmail; }
    public String getFileUrl() { return fileUrl; }
    public SalesDocumentStatus getStatus() { return status; }
    public Instant getVoidedAt() { return voidedAt; }
    public String getVoidReason() { return voidReason; }
    public UUID getVoidedBy() { return voidedBy; }
    public UUID getReplacesDocumentId() { return replacesDocumentId; }
    public Integer getReferenceCode() { return referenceCode; }
    public UUID getIssuedBy() { return issuedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
