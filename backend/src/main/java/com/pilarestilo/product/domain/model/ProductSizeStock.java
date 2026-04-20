package com.pilarestilo.product.domain.model;

import com.pilarestilo.product.domain.enums.ProductSize;
import com.pilarestilo.shared.domain.DomainException;

public class ProductSizeStock {

    private final ProductSize size;
    private int stock;

    public ProductSizeStock(ProductSize size, int stock) {
        if (size == null) throw new DomainException("Size cannot be null");
        if (stock < 0) throw new DomainException("Size stock cannot be negative");
        this.size = size;
        this.stock = stock;
    }

    public ProductSize getSize() { return size; }
    public int getStock() { return stock; }

    public void setStock(int stock) {
        if (stock < 0) throw new DomainException("Size stock cannot be negative");
        this.stock = stock;
    }
}
