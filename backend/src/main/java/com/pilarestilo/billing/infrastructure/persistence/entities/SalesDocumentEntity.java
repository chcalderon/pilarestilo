package com.pilarestilo.billing.infrastructure.persistence.entities;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_documents")
public class SalesDocumentEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /*
     * document_type and status are plain Strings rather than @Enumerated: the column carries a CHECK
     * constraint, so the database is already the authority on the allowed values, and the domain
     * parses them on the way in.
     */
    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "folio", nullable = false, length = 40)
    private String folio;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "receiver_rut", length = 20)
    private String receiverRut;

    @Column(name = "receiver_business_name", length = 160)
    private String receiverBusinessName;

    @Column(name = "receiver_business_activity", length = 160)
    private String receiverBusinessActivity;

    @Column(name = "receiver_name", length = 160)
    private String receiverName;

    @Column(name = "receiver_email", length = 255)
    private String receiverEmail;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "replaces_document_id")
    private UUID replacesDocumentId;

    /**
     * SII reference code on a credit note: 1 annuls, 2 corrects text, 3 corrects an amount.
     *
     * <p>Mapped as SMALLINT because that is the column V80 created — three possible values need no
     * more — and {@code ddl-auto: validate} refuses an Integer against it.
     */
    @Column(name = "reference_code")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer referenceCode;

    @Column(name = "issued_by", nullable = false)
    private UUID issuedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getReceiverRut() { return receiverRut; }
    public void setReceiverRut(String receiverRut) { this.receiverRut = receiverRut; }
    public String getReceiverBusinessName() { return receiverBusinessName; }
    public void setReceiverBusinessName(String receiverBusinessName) { this.receiverBusinessName = receiverBusinessName; }
    public String getReceiverBusinessActivity() { return receiverBusinessActivity; }
    public void setReceiverBusinessActivity(String receiverBusinessActivity) { this.receiverBusinessActivity = receiverBusinessActivity; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getReceiverEmail() { return receiverEmail; }
    public void setReceiverEmail(String receiverEmail) { this.receiverEmail = receiverEmail; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getVoidedAt() { return voidedAt; }
    public void setVoidedAt(Instant voidedAt) { this.voidedAt = voidedAt; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public UUID getVoidedBy() { return voidedBy; }
    public void setVoidedBy(UUID voidedBy) { this.voidedBy = voidedBy; }
    public UUID getReplacesDocumentId() { return replacesDocumentId; }
    public void setReplacesDocumentId(UUID replacesDocumentId) { this.replacesDocumentId = replacesDocumentId; }
    public Integer getReferenceCode() { return referenceCode; }
    public void setReferenceCode(Integer referenceCode) { this.referenceCode = referenceCode; }
    public UUID getIssuedBy() { return issuedBy; }
    public void setIssuedBy(UUID issuedBy) { this.issuedBy = issuedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
