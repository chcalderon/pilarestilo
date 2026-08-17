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
    public UUID getIssuedBy() { return issuedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
