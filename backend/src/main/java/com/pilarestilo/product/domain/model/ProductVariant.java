package com.pilarestilo.product.domain.model;

import com.pilarestilo.product.domain.enums.ProductSize;
import com.pilarestilo.shared.domain.DomainException;

public class ProductVariant {

    private final String color;
    private final ProductSize size;
    private final int stock;

    public ProductVariant(String color, ProductSize size, int stock) {
        if (color == null || color.isBlank()) {
            throw new DomainException("Product variant color cannot be blank");
        }
        if (size == null) {
            throw new DomainException("Product variant size cannot be null");
        }
        if (stock < 0) {
            throw new DomainException("Product variant stock cannot be negative");
        }
        this.color = color.trim();
        this.size = size;
        this.stock = stock;
    }

    public String getColor() { return color; }
    public ProductSize getSize() { return size; }
    public int getStock() { return stock; }
}
