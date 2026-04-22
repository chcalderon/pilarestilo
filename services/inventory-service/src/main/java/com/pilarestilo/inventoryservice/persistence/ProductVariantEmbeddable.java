package com.pilarestilo.inventoryservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProductVariantEmbeddable {

    @Column(name = "color", nullable = false, length = 80)
    private String color;

    @Column(name = "size", nullable = false, length = 8)
    private String size;

    @Column(name = "stock", nullable = false)
    private int stock;

    public String getColor() {
        return color;
    }

    public String getSize() {
        return size;
    }

    public int getStock() {
        return stock;
    }
}
