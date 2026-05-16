package com.pilarestilo.order.domain.model;

import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Order {

    private UUID id;
    private UUID customerId;
    private List<OrderItem> items;
    private Money subtotal;
    private Money discountAmount;
    private Money totalAmount;
    private PaymentMethod paymentMethod;
    private String shippingZoneCode;
    private String shippingCourierId;
    private String shippingCourierName;
    private String shippingPaymentMode;
    private UUID shippingAddressId;
    private String shippingAddressReference;
    private String notes;
    private SalesChannel salesChannel;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private Order() {}

    /**
     * Reconstructs an Order from persistence without triggering business-rule validation.
     * Only for use by repository adapters.
     */
    public static Order reconstruct(UUID id, UUID customerId, List<OrderItem> items,
                                     Money subtotal, Money discountAmount, Money totalAmount,
                                     PaymentMethod paymentMethod, String shippingZoneCode,
                                     String shippingCourierId, String shippingCourierName,
                                     String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                     String notes,
                                     OrderStatus status, Instant createdAt, Instant updatedAt) {
        return reconstruct(id, customerId, items, subtotal, discountAmount, totalAmount,
                paymentMethod, shippingZoneCode, shippingCourierId, shippingCourierName,
                shippingPaymentMode, shippingAddressId, shippingAddressReference, notes,
                SalesChannel.ECOMMERCE, status, createdAt, updatedAt);
    }

    public static Order reconstruct(UUID id, UUID customerId, List<OrderItem> items,
                                     Money subtotal, Money discountAmount, Money totalAmount,
                                     PaymentMethod paymentMethod, String shippingZoneCode,
                                     String shippingCourierId, String shippingCourierName,
                                     String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                     String notes, SalesChannel salesChannel,
                                     OrderStatus status, Instant createdAt, Instant updatedAt) {
        Order order = new Order();
        order.id = id;
        order.customerId = customerId;
        order.items = items;
        order.subtotal = subtotal;
        order.discountAmount = discountAmount;
        order.totalAmount = totalAmount;
        order.paymentMethod = paymentMethod;
        order.shippingZoneCode = shippingZoneCode;
        order.shippingCourierId = shippingCourierId;
        order.shippingCourierName = shippingCourierName;
        order.shippingPaymentMode = shippingPaymentMode;
        order.shippingAddressId = shippingAddressId;
        order.shippingAddressReference = shippingAddressReference;
        order.notes = notes;
        order.salesChannel = salesChannel != null ? salesChannel : SalesChannel.ECOMMERCE;
        order.status = status;
        order.createdAt = createdAt;
        order.updatedAt = updatedAt;
        return order;
    }

    public static Order create(UUID customerId, List<OrderItem> items, Money discountAmount,
                                PaymentMethod paymentMethod, String shippingZoneCode,
                                String shippingCourierId, String shippingCourierName,
                                String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                String notes) {
        return create(customerId, items, discountAmount, paymentMethod, shippingZoneCode,
                shippingCourierId, shippingCourierName, shippingPaymentMode, shippingAddressId,
                shippingAddressReference, notes, null);
    }

    public static Order create(UUID customerId, List<OrderItem> items, Money discountAmount,
                                PaymentMethod paymentMethod, String shippingZoneCode,
                                String shippingCourierId, String shippingCourierName,
                                String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                String notes, SalesChannel salesChannel) {
        if (customerId == null) {
            throw new DomainException("Customer ID cannot be null");
        }
        if (items == null || items.isEmpty()) {
            throw new DomainException("Order must have at least one item");
        }
        if (paymentMethod == null) {
            throw new DomainException("Payment method cannot be null");
        }
        if (shippingZoneCode == null || shippingZoneCode.isBlank()) {
            throw new DomainException("Shipping zone cannot be empty");
        }
        if (shippingCourierId == null || shippingCourierId.isBlank()) {
            throw new DomainException("Shipping courier cannot be empty");
        }
        if (shippingCourierName == null || shippingCourierName.isBlank()) {
            throw new DomainException("Shipping courier name cannot be empty");
        }
        if (shippingPaymentMode == null || shippingPaymentMode.isBlank()) {
            throw new DomainException("Shipping payment mode cannot be empty");
        }
        if (shippingAddressId == null) {
            throw new DomainException("Shipping address id cannot be null");
        }
        if (shippingAddressReference == null || shippingAddressReference.isBlank()) {
            throw new DomainException("Shipping address reference cannot be empty");
        }

        Money subtotal = items.stream()
                .map(item -> item.getUnitPrice().multiply(item.getQuantity()))
                .reduce(Money.zero(), Money::add);

        Money total = subtotal.subtract(discountAmount != null ? discountAmount : Money.zero());

        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.items = List.copyOf(items);
        order.subtotal = subtotal;
        order.discountAmount = discountAmount != null ? discountAmount : Money.zero();
        order.totalAmount = total;
        order.paymentMethod = paymentMethod;
        order.shippingZoneCode = shippingZoneCode.trim();
        order.shippingCourierId = shippingCourierId.trim();
        order.shippingCourierName = shippingCourierName.trim();
        order.shippingPaymentMode = shippingPaymentMode.trim();
        order.shippingAddressId = shippingAddressId;
        order.shippingAddressReference = shippingAddressReference.trim();
        order.notes = notes;
        order.salesChannel = salesChannel != null ? salesChannel : SalesChannel.ECOMMERCE;
        order.status = OrderStatus.CREATED;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    public void markAsPendingPayment() {
        assertStatus(OrderStatus.CREATED);
        this.status = OrderStatus.PENDING_PAYMENT;
        updateTimestamp();
    }

    public void markAsPaymentUnderReview() {
        assertStatus(OrderStatus.PENDING_PAYMENT);
        this.status = OrderStatus.PAYMENT_UNDER_REVIEW;
        updateTimestamp();
    }

    public void markAsPaid() {
        assertOneOf(Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_UNDER_REVIEW));
        this.status = OrderStatus.PAID;
        updateTimestamp();
    }

    public void markAsPreparingOrder() {
        assertStatus(OrderStatus.PAID);
        this.status = OrderStatus.PREPARING_ORDER;
        updateTimestamp();
    }

    public void markAsShipped() {
        assertStatus(OrderStatus.PREPARING_ORDER);
        this.status = OrderStatus.SHIPPED;
        updateTimestamp();
    }

    public void markAsDelivered() {
        assertStatus(OrderStatus.SHIPPED);
        this.status = OrderStatus.DELIVERED;
        updateTimestamp();
    }

    public void cancel() {
        if (this.status == OrderStatus.DELIVERED) {
            throw new DomainException("Cannot cancel a delivered order");
        }
        this.status = OrderStatus.CANCELLED;
        updateTimestamp();
    }

    private void assertStatus(OrderStatus expected) {
        if (this.status != expected) {
            throw new DomainException("Cannot transition from " + this.status + " — expected " + expected);
        }
    }

    private void assertOneOf(Set<OrderStatus> expected) {
        if (!expected.contains(this.status)) {
            throw new DomainException("Invalid status transition from " + this.status);
        }
    }

    private void updateTimestamp() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public List<OrderItem> getItems() { return items; }
    public Money getSubtotal() { return subtotal; }
    public Money getDiscountAmount() { return discountAmount; }
    public Money getTotalAmount() { return totalAmount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getShippingZoneCode() { return shippingZoneCode; }
    public String getShippingCourierId() { return shippingCourierId; }
    public String getShippingCourierName() { return shippingCourierName; }
    public String getShippingPaymentMode() { return shippingPaymentMode; }
    public UUID getShippingAddressId() { return shippingAddressId; }
    public String getShippingAddressReference() { return shippingAddressReference; }
    public String getNotes() { return notes; }
    public SalesChannel getSalesChannel() { return salesChannel; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

}
