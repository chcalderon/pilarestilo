package com.pilarestilo.product.domain.model;

import com.pilarestilo.shared.domain.DomainException;

public class ProductVariant {

    private final String color;
    private final String size;
    private final int stockOnHand;
    private final int stockReserved;

    /** Backward-compatible constructor: reserved starts at 0. */
    public ProductVariant(String color, String size, int stockOnHand) {
        this(color, size, stockOnHand, 0);
    }

    public ProductVariant(String color, String size, int stockOnHand, int stockReserved) {
        if (color == null || color.isBlank()) {
            throw new DomainException("Product variant color cannot be blank");
        }
        if (stockOnHand < 0) {
            throw new DomainException("Product variant stock cannot be negative");
        }
        if (stockReserved < 0) {
            throw new DomainException("Product variant reserved stock cannot be negative");
        }
        if (stockReserved > stockOnHand) {
            throw new DomainException("Product variant reserved stock cannot exceed stock on hand");
        }
        this.color = color.trim();
        this.size = ProductSizeRules.normalizeOrThrow(size);
        this.stockOnHand = stockOnHand;
        this.stockReserved = stockReserved;
    }

    public String getColor() { return color; }
    public String getSize() { return size; }

    /** Stock on hand (physical quantity, including reserved). */
    public int getStockOnHand() { return stockOnHand; }

    /** Stock reserved (committed to pending orders, not yet shipped). */
    public int getStockReserved() { return stockReserved; }

    /** Available stock = on-hand minus reserved. */
    public int available() { return stockOnHand - stockReserved; }

    /**
     * Alias for {@link #getStockOnHand()} kept for backward compatibility.
     * Callers that used getStock() before Fase 4 continue to compile;
     * note that this no longer reflects "available" — use {@link #available()} for that.
     */
    @SuppressWarnings("java:S4144")
    public int getStock() { return stockOnHand; }
}
