package com.pilarestilo.product.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.ColumnDefault;

@Embeddable
public class ProductVariantEmbeddable {

    @Column(name = "color", nullable = false, length = 80)
    private String color;

    @Column(name = "size", nullable = false, length = 32)
    private String size;

    @Column(name = "stock_on_hand", nullable = false)
    private int stockOnHand;

    @ColumnDefault("0")
    @Column(name = "stock_reserved", nullable = false)
    private int stockReserved;

    protected ProductVariantEmbeddable() {}

    /** Backward-compatible constructor: reserved starts at 0. */
    public ProductVariantEmbeddable(String color, String size, int stockOnHand) {
        this(color, size, stockOnHand, 0);
    }

    public ProductVariantEmbeddable(String color, String size, int stockOnHand, int stockReserved) {
        this.color = color;
        this.size = size;
        this.stockOnHand = stockOnHand;
        this.stockReserved = stockReserved;
    }

    public String getColor() { return color; }
    public String getSize() { return size; }
    public int getStockOnHand() { return stockOnHand; }
    public int getStockReserved() { return stockReserved; }

    public void setColor(String color) { this.color = color; }
    public void setSize(String size) { this.size = size; }
    public void setStockOnHand(int stockOnHand) { this.stockOnHand = stockOnHand; }
    public void setStockReserved(int stockReserved) { this.stockReserved = stockReserved; }
}
