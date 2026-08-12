package com.pilarestilo.order.infrastructure.persistence.entities;

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

    @Column(name = "customer_id", nullable = false)
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

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
