package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

/** Read-only view of {@code sales_documents}. */
@Entity
@Immutable
@Table(name = "sales_documents")
public class SalesDocumentRoEntity {

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "document_type")
    private String documentType;

    @Column(name = "folio")
    private String folio;

    @Column(name = "net_amount")
    private BigDecimal netAmount;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "tax_rate")
    private BigDecimal taxRate;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "currency")
    private String currency;

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getDocumentType() { return documentType; }
    public String getFolio() { return folio; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
}
