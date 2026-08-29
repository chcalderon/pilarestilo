package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Read-only view of {@code orders}. Only the columns {@code NotificationComposer} renders. */
@Entity
@Immutable
@Table(name = "orders")
public class OrderRoEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "public_reference")
    private String publicReference;

    @Column(name = "status")
    private String status;

    @Column(name = "subtotal_amount")
    private BigDecimal subtotalAmount;

    @Column(name = "subtotal_currency")
    private String subtotalCurrency;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "discount_currency")
    private String discountCurrency;

    @Column(name = "net_amount")
    private BigDecimal netAmount;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "tax_rate")
    private BigDecimal taxRate;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "total_currency")
    private String totalCurrency;

    @Column(name = "shipping_courier_id")
    private String shippingCourierId;

    @Column(name = "shipping_courier_name")
    private String shippingCourierName;

    @Column(name = "shipping_zone_code")
    private String shippingZoneCode;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private List<OrderItemRoEntity> items;

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getPublicReference() { return publicReference; }
    public String getStatus() { return status; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public String getSubtotalCurrency() { return subtotalCurrency; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public String getDiscountCurrency() { return discountCurrency; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getTotalCurrency() { return totalCurrency; }
    public String getShippingCourierId() { return shippingCourierId; }
    public String getShippingCourierName() { return shippingCourierName; }
    public String getShippingZoneCode() { return shippingZoneCode; }
    public List<OrderItemRoEntity> getItems() { return items; }
}
