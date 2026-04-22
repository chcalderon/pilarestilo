package com.pilarestilo.inventoryservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProductSizeStockEmbeddable {

    @Column(name = "size", nullable = false, length = 8)
    private String size;

    @Column(name = "stock", nullable = false)
    private int stock;

    public String getSize() {
        return size;
    }

    public int getStock() {
        return stock;
    }
}
