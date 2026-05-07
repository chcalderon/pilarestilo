package com.pilarestilo.product.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProductSizeStockEmbeddable {

    @Column(name = "size", nullable = false, length = 32)
    private String size;

    @Column(name = "stock", nullable = false)
    private int stock;

    protected ProductSizeStockEmbeddable() {}

    public ProductSizeStockEmbeddable(String size, int stock) {
        this.size = size;
        this.stock = stock;
    }

    public String getSize() { return size; }
    public int getStock() { return stock; }
}
