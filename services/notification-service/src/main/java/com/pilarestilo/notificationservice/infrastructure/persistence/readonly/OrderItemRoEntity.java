package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.UUID;

/** Read-only view of {@code order_items}. */
@Entity
@Immutable
@Table(name = "order_items")
public class OrderItemRoEntity {

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "variant_color")
    private String variantColor;

    @Column(name = "variant_size")
    private String variantSize;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "unit_price_amount")
    private BigDecimal unitPriceAmount;

    @Column(name = "unit_price_currency")
    private String unitPriceCurrency;

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getProductName() { return productName; }
    public String getVariantColor() { return variantColor; }
    public String getVariantSize() { return variantSize; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPriceAmount() { return unitPriceAmount; }
    public String getUnitPriceCurrency() { return unitPriceCurrency; }
}
