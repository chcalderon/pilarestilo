package com.pilarestilo.product.domain.model;

import com.pilarestilo.shared.domain.DomainException;

public class ProductVariant {

    private final String color;
    private final String size;
    private final int stock;

    public ProductVariant(String color, String size, int stock) {
        if (color == null || color.isBlank()) {
            throw new DomainException("Product variant color cannot be blank");
        }
        if (stock < 0) {
            throw new DomainException("Product variant stock cannot be negative");
        }
        this.color = color.trim();
        this.size = ProductSizeRules.normalizeOrThrow(size);
        this.stock = stock;
    }

    public String getColor() { return color; }
    public String getSize() { return size; }
    public int getStock() { return stock; }
}
