package com.pilarestilo.inventoryservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProductVariantEmbeddable {

    @Column(name = "color", nullable = false, length = 80)
    private String color;

    @Column(name = "size", nullable = false, length = 32)
    private String size;

    @Column(name = "stock_on_hand", nullable = false)
    private int stockOnHand;

    @Column(name = "stock_reserved", nullable = false)
    private int stockReserved;

    public String getColor() {
        return color;
    }

    public String getSize() {
        return size;
    }

    public int getStock() {
        return available();
    }

    public int getStockOnHand() {
        return stockOnHand;
    }

    public int getStockReserved() {
        return stockReserved;
    }

    public int available() {
        return stockOnHand - stockReserved;
    }
}
