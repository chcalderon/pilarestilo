package com.pilarestilo.order.domain.model;

import com.pilarestilo.shared.application.Money;

import java.util.UUID;

public class OrderItem {

    private final UUID id;
    private final UUID productId;
    private final String productName;
    private final Money unitPrice;
    private final int quantity;

    public OrderItem(UUID id, UUID productId, String productName, Money unitPrice, int quantity) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Money getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
}
