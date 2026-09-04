package com.pilarestilo.order.domain.model;

import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.application.TaxBreakdown;
import com.pilarestilo.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Order {

    private UUID id;
    private UUID customerId;
    /** Short human-quotable code; see OrderReference. */
    private String publicReference;
    private List<OrderItem> items;
    private Money subtotal;
    private Money discountAmount;
    /**
     * Which code produced {@link #discountAmount}, kept as provenance only — never read by the
     * state machine. Discounts are hard-deleted, and the ledger's FK is ON DELETE SET NULL, so
     * without this snapshot deleting a code erases every record of which orders used it.
     * Null for orders created before V67 and for orders with no code.
     */
    private UUID discountId;
    private String discountCode;
    private Money totalAmount;
    /**
     * The total split the way a boleta reports it. Derived from {@link #totalAmount} at the rate in
     * force when the order was created, and snapshotted rather than recomputed on read: a future
     * change to the IVA must not restate what a past sale declared.
     */
    private Money netAmount;
    private Money taxAmount;
    private BigDecimal taxRate;
    private PaymentMethod paymentMethod;
    private String shippingZoneCode;
    private String shippingCourierId;
    private String shippingCourierName;
    private String shippingPaymentMode;
    private UUID shippingAddressId;
    private String shippingAddressReference;
    private String notes;
    private SalesChannel salesChannel;
    /** Every order has one. Web orders and shipped external sales are SHIPPING. */
    private DeliveryMethod deliveryMethod = DeliveryMethod.SHIPPING;
    /** Free-text buyer snapshot for an external sale. Null for a web order — it has {@link #customerId}. */
    private String buyerName;
    private String buyerContact;
    /** Dedupes a double-submitted external sale. Null for a web order. */
    private String externalIdempotencyKey;
    /**
     * Dedupes a double-submitted web checkout (refresh mid-request, a fast double-click racing the
     * disabled state). Generated client-side once per checkout attempt. Null for an external sale,
     * which has {@link #externalIdempotencyKey} instead — the two never overlap.
     */
    private String idempotencyKey;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private Order() {}

    /**
     * Reconstructs an Order from persistence without triggering business-rule validation.
     * Only for use by repository adapters.
     */
    // One parameter per column an order actually has; these overloads exist so older callers don't
    // need to pass columns added later, not to be split further.
    @SuppressWarnings("java:S107")
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
                SalesChannel.ECOMMERCE, status, createdAt, updatedAt, null);
    }

    @SuppressWarnings("java:S107")
    public static Order reconstruct(UUID id, UUID customerId, List<OrderItem> items,
                                     Money subtotal, Money discountAmount, Money totalAmount,
                                     PaymentMethod paymentMethod, String shippingZoneCode,
                                     String shippingCourierId, String shippingCourierName,
                                     String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                     String notes, SalesChannel salesChannel,
                                     OrderStatus status, Instant createdAt, Instant updatedAt,
                                     String publicReference) {
        return reconstruct(id, customerId, items, subtotal, discountAmount, totalAmount,
                paymentMethod, shippingZoneCode, shippingCourierId, shippingCourierName,
                shippingPaymentMode, shippingAddressId, shippingAddressReference, notes,
                salesChannel, status, createdAt, updatedAt, publicReference, null);
    }

    /**
     * @param taxRate the rate the order was created under. Only the rate is carried: net and tax are
     *                derived from it and the total by {@link TaxBreakdown}, which is also what wrote
     *                the columns and what V76 backfilled them with, so a stored pair and a derived
     *                pair cannot disagree. Null means a row from before the column existed.
     */
    @SuppressWarnings("java:S107")
    public static Order reconstruct(UUID id, UUID customerId, List<OrderItem> items,
                                     Money subtotal, Money discountAmount, Money totalAmount,
                                     PaymentMethod paymentMethod, String shippingZoneCode,
                                     String shippingCourierId, String shippingCourierName,
                                     String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                     String notes, SalesChannel salesChannel,
                                     OrderStatus status, Instant createdAt, Instant updatedAt,
                                     String publicReference, BigDecimal taxRate) {
        return reconstruct(id, customerId, items, subtotal, discountAmount, totalAmount, paymentMethod,
                shippingZoneCode, shippingCourierId, shippingCourierName, shippingPaymentMode,
                shippingAddressId, shippingAddressReference, notes, salesChannel, status, createdAt,
                updatedAt, publicReference, taxRate,
                DeliveryMethod.SHIPPING, null, null, null);
    }

    @SuppressWarnings("java:S107")
    public static Order reconstruct(UUID id, UUID customerId, List<OrderItem> items,
                                     Money subtotal, Money discountAmount, Money totalAmount,
                                     PaymentMethod paymentMethod, String shippingZoneCode,
                                     String shippingCourierId, String shippingCourierName,
                                     String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                     String notes, SalesChannel salesChannel,
                                     OrderStatus status, Instant createdAt, Instant updatedAt,
                                     String publicReference, BigDecimal taxRate,
                                     DeliveryMethod deliveryMethod, String buyerName, String buyerContact,
                                     String externalIdempotencyKey) {
        return reconstruct(id, customerId, items, subtotal, discountAmount, totalAmount, paymentMethod,
                shippingZoneCode, shippingCourierId, shippingCourierName, shippingPaymentMode,
                shippingAddressId, shippingAddressReference, notes, salesChannel, status, createdAt,
                updatedAt, publicReference, taxRate, deliveryMethod, buyerName, buyerContact,
                externalIdempotencyKey, null);
    }

    @SuppressWarnings("java:S107")
    public static Order reconstruct(UUID id, UUID customerId, List<OrderItem> items,
                                     Money subtotal, Money discountAmount, Money totalAmount,
                                     PaymentMethod paymentMethod, String shippingZoneCode,
                                     String shippingCourierId, String shippingCourierName,
                                     String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                     String notes, SalesChannel salesChannel,
                                     OrderStatus status, Instant createdAt, Instant updatedAt,
                                     String publicReference, BigDecimal taxRate,
                                     DeliveryMethod deliveryMethod, String buyerName, String buyerContact,
                                     String externalIdempotencyKey, String idempotencyKey) {
        Order order = new Order();
        order.applyTaxRate(totalAmount, taxRate);
        order.id = id;
        // Stored value wins. V67's repair loop salts duplicates, so a derived value could differ
        // from the code the customer was actually given. Older rows predate the column.
        order.publicReference = publicReference != null
                ? publicReference
                : OrderReference.forOrderId(id);
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
        order.deliveryMethod = deliveryMethod != null ? deliveryMethod : DeliveryMethod.SHIPPING;
        order.buyerName = buyerName;
        order.buyerContact = buyerContact;
        order.externalIdempotencyKey = externalIdempotencyKey;
        order.idempotencyKey = idempotencyKey;
        order.status = status;
        order.createdAt = createdAt;
        order.updatedAt = updatedAt;
        return order;
    }

    /**
     * Splits the total into net and tax. The rate falls back to {@link TaxBreakdown#DEFAULT_RATE}
     * so an order can never end up without a breakdown; a missing rate means a caller that predates
     * the column, not a sale outside the VAT system.
     */
    private void applyTaxRate(Money totalAmount, BigDecimal taxRate) {
        BigDecimal rate = taxRate == null ? TaxBreakdown.DEFAULT_RATE : taxRate;
        TaxBreakdown breakdown = TaxBreakdown.fromGross(totalAmount, rate);
        this.netAmount = breakdown.net();
        this.taxAmount = breakdown.tax();
        this.taxRate = rate;
    }

    /**
     * Records which code was applied. Deliberately not a constructor argument: it changes no
     * behaviour, and the factories already carry seventeen parameters.
     */
    public void recordDiscountProvenance(UUID discountId, String discountCode) {
        this.discountId = discountId;
        this.discountCode = discountCode;
    }

    // One parameter per column an order actually has; these overloads exist so older callers don't
    // need to pass columns added later, not to be split further.
    @SuppressWarnings("java:S107")
    public static Order create(UUID customerId, List<OrderItem> items, Money discountAmount,
                                PaymentMethod paymentMethod, String shippingZoneCode,
                                String shippingCourierId, String shippingCourierName,
                                String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                String notes) {
        return create(customerId, items, discountAmount, paymentMethod, shippingZoneCode,
                shippingCourierId, shippingCourierName, shippingPaymentMode, shippingAddressId,
                shippingAddressReference, notes, null);
    }

    @SuppressWarnings("java:S107")
    public static Order create(UUID customerId, List<OrderItem> items, Money discountAmount,
                                PaymentMethod paymentMethod, String shippingZoneCode,
                                String shippingCourierId, String shippingCourierName,
                                String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                String notes, SalesChannel salesChannel) {
        return create(customerId, items, discountAmount, paymentMethod, shippingZoneCode,
                shippingCourierId, shippingCourierName, shippingPaymentMode, shippingAddressId,
                shippingAddressReference, notes, salesChannel, null, DeliveryMethod.SHIPPING);
    }

    /**
     * @param taxRate the VAT rate configured for the shop when the order is placed. Snapshotted onto
     *                the order so a later change to it cannot restate this sale. Null falls back to
     *                {@link TaxBreakdown#DEFAULT_RATE}.
     */
    @SuppressWarnings("java:S107")
    private static void validateForCreation(UUID customerId, List<OrderItem> items, PaymentMethod paymentMethod,
                                             String shippingZoneCode, String shippingCourierId,
                                             String shippingCourierName, String shippingPaymentMode,
                                             UUID shippingAddressId, String shippingAddressReference) {
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
    }

    @SuppressWarnings("java:S107")
    public static Order create(UUID customerId, List<OrderItem> items, Money discountAmount,
                                PaymentMethod paymentMethod, String shippingZoneCode,
                                String shippingCourierId, String shippingCourierName,
                                String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                String notes, SalesChannel salesChannel, BigDecimal taxRate) {
        return create(customerId, items, discountAmount, paymentMethod, shippingZoneCode,
                shippingCourierId, shippingCourierName, shippingPaymentMode, shippingAddressId,
                shippingAddressReference, notes, salesChannel, taxRate, DeliveryMethod.SHIPPING);
    }

    /** The web checkout's entry point — the only caller that has an idempotency key to give. */
    @SuppressWarnings("java:S107")
    public static Order create(UUID customerId, List<OrderItem> items, Money discountAmount,
                                PaymentMethod paymentMethod, String shippingZoneCode,
                                String shippingCourierId, String shippingCourierName,
                                String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                String notes, SalesChannel salesChannel, BigDecimal taxRate,
                                String idempotencyKey) {
        return create(customerId, items, discountAmount, paymentMethod, shippingZoneCode,
                shippingCourierId, shippingCourierName, shippingPaymentMode, shippingAddressId,
                shippingAddressReference, notes, salesChannel, taxRate, DeliveryMethod.SHIPPING,
                idempotencyKey);
    }

    @SuppressWarnings("java:S107")
    public static Order create(UUID customerId, List<OrderItem> items, Money discountAmount,
                                PaymentMethod paymentMethod, String shippingZoneCode,
                                String shippingCourierId, String shippingCourierName,
                                String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                String notes, SalesChannel salesChannel, BigDecimal taxRate,
                                DeliveryMethod deliveryMethod) {
        return create(customerId, items, discountAmount, paymentMethod, shippingZoneCode,
                shippingCourierId, shippingCourierName, shippingPaymentMode, shippingAddressId,
                shippingAddressReference, notes, salesChannel, taxRate, deliveryMethod, null);
    }

    @SuppressWarnings("java:S107")
    public static Order create(UUID customerId, List<OrderItem> items, Money discountAmount,
                                PaymentMethod paymentMethod, String shippingZoneCode,
                                String shippingCourierId, String shippingCourierName,
                                String shippingPaymentMode, UUID shippingAddressId, String shippingAddressReference,
                                String notes, SalesChannel salesChannel, BigDecimal taxRate,
                                DeliveryMethod deliveryMethod, String idempotencyKey) {
        validateForCreation(customerId, items, paymentMethod, shippingZoneCode, shippingCourierId,
                shippingCourierName, shippingPaymentMode, shippingAddressId, shippingAddressReference);

        Money subtotal = items.stream()
                .map(item -> item.getUnitPrice().multiply(item.getQuantity()))
                .reduce(Money.zero(), Money::add);

        Money total = subtotal.subtract(discountAmount != null ? discountAmount : Money.zero());

        Order order = new Order();
        order.id = UUID.randomUUID();
        // Derived from the id rather than passed in: create() is the only place that mints the id,
        // so this is the one spot where the two cannot drift. uq_orders_public_reference is the
        // backstop -- a fresh UUID colliding with an existing reference is ~1 in 10 million at
        // 100k orders, and the salted overload exists for the migration's repair loop.
        order.publicReference = OrderReference.forOrderId(order.id);
        order.customerId = customerId;
        order.items = List.copyOf(items);
        order.subtotal = subtotal;
        order.discountAmount = discountAmount != null ? discountAmount : Money.zero();
        order.totalAmount = total;
        order.applyTaxRate(total, taxRate);
        order.paymentMethod = paymentMethod;
        order.shippingZoneCode = shippingZoneCode.trim();
        order.shippingCourierId = shippingCourierId.trim();
        order.shippingCourierName = shippingCourierName.trim();
        order.shippingPaymentMode = shippingPaymentMode.trim();
        order.shippingAddressId = shippingAddressId;
        order.shippingAddressReference = shippingAddressReference.trim();
        order.notes = notes;
        order.salesChannel = salesChannel != null ? salesChannel : SalesChannel.ECOMMERCE;
        order.deliveryMethod = deliveryMethod != null ? deliveryMethod : DeliveryMethod.SHIPPING;
        order.idempotencyKey = idempotencyKey;
        order.status = OrderStatus.CREATED;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    /**
     * A sale that already happened off-platform (Instagram / Facebook / WhatsApp, and later POS /
     * MercadoLibre). No registered customer, no courier or zone of ours, a free-text address — or
     * none, for pickup. Payment has already been received; the caller moves this straight to PAID.
     */
    @SuppressWarnings("java:S107")
    public static Order createExternalSale(String buyerName, String buyerContact,
                                           List<OrderItem> items, PaymentMethod paymentMethod,
                                           DeliveryMethod deliveryMethod, String shippingAddressText,
                                           String notes, SalesChannel salesChannel, BigDecimal taxRate,
                                           String externalIdempotencyKey) {
        if (isBlank(buyerName)) {
            throw new DomainException("Buyer name is required");
        }
        if (isBlank(buyerContact)) {
            throw new DomainException("Buyer contact is required");
        }
        if (items == null || items.isEmpty()) {
            throw new DomainException("Order must have at least one item");
        }
        if (paymentMethod == null) {
            throw new DomainException("Payment method cannot be null");
        }
        if (deliveryMethod == null) {
            throw new DomainException("Delivery method cannot be null");
        }
        if (deliveryMethod == DeliveryMethod.SHIPPING && isBlank(shippingAddressText)) {
            throw new DomainException("A shipping address is required for a shipped sale");
        }

        Money subtotal = items.stream()
                .map(item -> item.getUnitPrice().multiply(item.getQuantity()))
                .reduce(Money.zero(), Money::add);

        Order order = new Order();
        order.id = UUID.randomUUID();
        order.publicReference = OrderReference.forOrderId(order.id);
        order.customerId = null;
        order.items = List.copyOf(items);
        order.subtotal = subtotal;
        order.discountAmount = Money.zero();
        order.totalAmount = subtotal;
        order.applyTaxRate(subtotal, taxRate);
        order.paymentMethod = paymentMethod;
        order.shippingZoneCode = null;
        order.shippingCourierId = null;
        order.shippingCourierName = null;
        order.shippingPaymentMode = null;
        order.shippingAddressId = null;
        order.shippingAddressReference = deliveryMethod == DeliveryMethod.SHIPPING
                ? shippingAddressText.trim() : null;
        order.notes = notes;
        order.salesChannel = salesChannel != null ? salesChannel : SalesChannel.MANUAL;
        order.deliveryMethod = deliveryMethod;
        order.buyerName = buyerName.trim();
        order.buyerContact = buyerContact.trim();
        order.externalIdempotencyKey = externalIdempotencyKey;
        order.status = OrderStatus.CREATED;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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
    public String getPublicReference() { return publicReference; }
    public UUID getCustomerId() { return customerId; }
    public List<OrderItem> getItems() { return items; }
    public Money getSubtotal() { return subtotal; }
    public Money getDiscountAmount() { return discountAmount; }
    public UUID getDiscountId() { return discountId; }
    public String getDiscountCode() { return discountCode; }
    public Money getTotalAmount() { return totalAmount; }
    public Money getNetAmount() { return netAmount; }
    public Money getTaxAmount() { return taxAmount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getShippingZoneCode() { return shippingZoneCode; }
    public String getShippingCourierId() { return shippingCourierId; }
    public String getShippingCourierName() { return shippingCourierName; }
    public String getShippingPaymentMode() { return shippingPaymentMode; }
    public UUID getShippingAddressId() { return shippingAddressId; }
    public String getShippingAddressReference() { return shippingAddressReference; }
    public String getNotes() { return notes; }
    public SalesChannel getSalesChannel() { return salesChannel; }
    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public String getBuyerName() { return buyerName; }
    public String getBuyerContact() { return buyerContact; }
    public String getExternalIdempotencyKey() { return externalIdempotencyKey; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

}
