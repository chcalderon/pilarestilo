package com.pilarestilo.orderservice.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

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

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_currency", nullable = false, length = 10)
    private String totalCurrency;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public List<OrderItemEntity> getItems() {
        return items;
    }

    public void clearItems() {
        this.items.clear();
    }

    public void addItem(OrderItemEntity item) {
        item.setOrder(this);
        this.items.add(item);
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public String getSubtotalCurrency() {
        return subtotalCurrency;
    }

    public void setSubtotalCurrency(String subtotalCurrency) {
        this.subtotalCurrency = subtotalCurrency;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getDiscountCurrency() {
        return discountCurrency;
    }

    public void setDiscountCurrency(String discountCurrency) {
        this.discountCurrency = discountCurrency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getTotalCurrency() {
        return totalCurrency;
    }

    public void setTotalCurrency(String totalCurrency) {
        this.totalCurrency = totalCurrency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
