package com.pilarestilo.inventoryservice.application;

import com.pilarestilo.inventoryservice.persistence.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class InventoryCommandService {

    private final ProductRepository productRepository;

    public InventoryCommandService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void reserve(UUID productId, int qty) {
        validate(productId, qty);
        ensureExists(productId);
        int updated = productRepository.reserveStock(productId, qty, Instant.now());
        if (updated == 0) {
            throw new IllegalStateException("Insufficient stock for product: " + productId);
        }
    }

    @Transactional
    public void release(UUID productId, int qty) {
        validate(productId, qty);
        ensureExists(productId);
        productRepository.releaseStock(productId, qty, Instant.now());
    }

    @Transactional(readOnly = true)
    public void confirm(UUID productId, int qty) {
        validate(productId, qty);
        ensureExists(productId);
        // no-op: stock was already reserved during order creation.
    }

    private void validate(UUID productId, int qty) {
        if (productId == null) {
            throw new IllegalArgumentException("Product id is required");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }

    private void ensureExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new NoSuchElementException("Product not found: " + productId);
        }
    }
}
