package com.pilarestilo.product.domain.model;

import com.pilarestilo.shared.domain.DomainException;

public class ProductSizeStock {

    private final String size;
    private int stock;

    public ProductSizeStock(String size, int stock) {
        if (stock < 0) throw new DomainException("Size stock cannot be negative");
        this.size = ProductSizeRules.normalizeOrThrow(size);
        this.stock = stock;
    }

    public String getSize() { return size; }
    public int getStock() { return stock; }

    public void setStock(int stock) {
        if (stock < 0) throw new DomainException("Size stock cannot be negative");
        this.stock = stock;
    }
}
