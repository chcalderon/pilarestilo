package com.pilarestilo.order.infrastructure.persistence.entities;

import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    /** Null for an external sale (V94). The FK to users(id) still holds for non-null values. */
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "public_reference", nullable = false, length = 16)
    private String publicReference;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    @Column(name = "subtotal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "subtotal_currency", nullable = false, length = 10)
    private String subtotalCurrency;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "discount_currency", nullable = false, length = 10)
    private String discountCurrency;

    /** Provenance for discount_amount; see Order.discountId. Nullable by design. */
    @Column(name = "discount_id")
    private UUID discountId;

    @Column(name = "discount_code", length = 50)
    private String discountCode;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_currency", nullable = false, length = 10)
    private String totalCurrency;

    /*
     * NOT NULL since V79 contracted what V76 expanded. They share total_currency: a net in one
     * currency and a total in another would be a different bug entirely. The database also checks
     * that net + tax = total, which is what TaxBreakdown guarantees by deriving the tax as the
     * remainder rather than a second multiplication.
     */
    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "shipping_zone_code", length = 24)
    private String shippingZoneCode;

    @Column(name = "shipping_courier_id", length = 120)
    private String shippingCourierId;

    @Column(name = "shipping_courier_name", length = 160)
    private String shippingCourierName;

    @Column(name = "shipping_payment_mode", length = 32)
    private String shippingPaymentMode;

    @Column(name = "shipping_address_id")
    private UUID shippingAddressId;

    @Column(name = "shipping_address_reference", columnDefinition = "TEXT")
    private String shippingAddressReference;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_channel", nullable = false, length = 20)
    private SalesChannel salesChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 16)
    private DeliveryMethod deliveryMethod = DeliveryMethod.SHIPPING;

    @Column(name = "buyer_name", length = 160)
    private String buyerName;

    @Column(name = "buyer_contact", length = 160)
    private String buyerContact;

    @Column(name = "external_idempotency_key", length = 64)
    private String externalIdempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCustomerId() { return customerId; }
    public String getPublicReference() { return publicReference; }
    public void setPublicReference(String publicReference) { this.publicReference = publicReference; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public List<OrderItemEntity> getItems() { return items; }
    public void setItems(List<OrderItemEntity> items) { this.items = items; }

    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public void setSubtotalAmount(BigDecimal subtotalAmount) { this.subtotalAmount = subtotalAmount; }

    public String getSubtotalCurrency() { return subtotalCurrency; }
    public void setSubtotalCurrency(String subtotalCurrency) { this.subtotalCurrency = subtotalCurrency; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public UUID getDiscountId() { return discountId; }
    public void setDiscountId(UUID discountId) { this.discountId = discountId; }

    public String getDiscountCode() { return discountCode; }
    public void setDiscountCode(String discountCode) { this.discountCode = discountCode; }

    public String getDiscountCurrency() { return discountCurrency; }
    public void setDiscountCurrency(String discountCurrency) { this.discountCurrency = discountCurrency; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getTotalCurrency() { return totalCurrency; }
    public void setTotalCurrency(String totalCurrency) { this.totalCurrency = totalCurrency; }

    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getShippingZoneCode() { return shippingZoneCode; }
    public void setShippingZoneCode(String shippingZoneCode) { this.shippingZoneCode = shippingZoneCode; }

    public String getShippingCourierId() { return shippingCourierId; }
    public void setShippingCourierId(String shippingCourierId) { this.shippingCourierId = shippingCourierId; }

    public String getShippingCourierName() { return shippingCourierName; }
    public void setShippingCourierName(String shippingCourierName) { this.shippingCourierName = shippingCourierName; }

    public String getShippingPaymentMode() { return shippingPaymentMode; }
    public void setShippingPaymentMode(String shippingPaymentMode) { this.shippingPaymentMode = shippingPaymentMode; }

    public UUID getShippingAddressId() { return shippingAddressId; }
    public void setShippingAddressId(UUID shippingAddressId) { this.shippingAddressId = shippingAddressId; }

    public String getShippingAddressReference() { return shippingAddressReference; }
    public void setShippingAddressReference(String shippingAddressReference) { this.shippingAddressReference = shippingAddressReference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public SalesChannel getSalesChannel() { return salesChannel; }
    public void setSalesChannel(SalesChannel salesChannel) { this.salesChannel = salesChannel; }

    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(DeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public String getBuyerContact() { return buyerContact; }
    public void setBuyerContact(String buyerContact) { this.buyerContact = buyerContact; }

    public String getExternalIdempotencyKey() { return externalIdempotencyKey; }
    public void setExternalIdempotencyKey(String externalIdempotencyKey) { this.externalIdempotencyKey = externalIdempotencyKey; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
