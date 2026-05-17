package com.pilarestilo.order.domain.model;

import com.pilarestilo.shared.application.Money;

import java.util.UUID;

public class OrderItem {

    private final UUID id;
    private final UUID productId;
    private final String productName;
    private final Money unitPrice;
    private final int quantity;
    private final String variantColor;
    private final String variantSize;

    public OrderItem(UUID id, UUID productId, String productName, Money unitPrice, int quantity) {
        this(id, productId, productName, unitPrice, quantity, null, null);
    }

    public OrderItem(UUID id, UUID productId, String productName, Money unitPrice, int quantity,
                     String variantColor, String variantSize) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.variantColor = variantColor;
        this.variantSize = variantSize;
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Money getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public String getVariantColor() { return variantColor; }
    public String getVariantSize() { return variantSize; }
}
