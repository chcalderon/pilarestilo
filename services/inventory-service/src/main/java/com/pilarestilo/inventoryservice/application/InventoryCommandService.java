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
        reserve(productId, qty, null, null);
    }

    @Transactional
    public void reserve(UUID productId, int qty, String variantColor, String variantSize) {
        validate(productId, qty);
        validateVariantSelector(variantColor, variantSize);
        ensureExists(productId);
        boolean variantSelectionProvided = variantColor != null && variantSize != null;
        boolean productHasVariants = productRepository.hasVariants(productId);

        if (!variantSelectionProvided && productHasVariants) {
            throw new IllegalArgumentException("Variant selection (color + size) is required for this product");
        }

        if (variantSelectionProvided) {
            int variantUpdated = productRepository.reserveVariantStock(productId, variantColor, variantSize, qty);
            int sizeUpdated = productRepository.reserveSizeStock(productId, variantSize, qty);
            if (variantUpdated == 0 || sizeUpdated == 0) {
                throw new IllegalStateException("Insufficient stock for variant: " + productId + " / " + variantColor + " / " + variantSize);
            }
            productRepository.syncProductStockFromVariants(productId, Instant.now());
            return;
        }

        int updated = productRepository.reserveStock(productId, qty, Instant.now());
        if (updated == 0) {
            throw new IllegalStateException("Insufficient stock for product: " + productId);
        }
    }

    @Transactional
    public void release(UUID productId, int qty) {
        release(productId, qty, null, null);
    }

    @Transactional
    public void release(UUID productId, int qty, String variantColor, String variantSize) {
        validate(productId, qty);
        validateVariantSelector(variantColor, variantSize);
        ensureExists(productId);
        if (variantColor != null && variantSize != null) {
            productRepository.releaseVariantStock(productId, variantColor, variantSize, qty);
            productRepository.releaseSizeStock(productId, variantSize, qty);
            productRepository.syncProductStockFromVariants(productId, Instant.now());
            return;
        }

        productRepository.releaseStock(productId, qty, Instant.now());
    }

    @Transactional(readOnly = true)
    public void confirm(UUID productId, int qty) {
        confirm(productId, qty, null, null);
    }

    @Transactional(readOnly = true)
    public void confirm(UUID productId, int qty, String variantColor, String variantSize) {
        validate(productId, qty);
        validateVariantSelector(variantColor, variantSize);
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

    private void validateVariantSelector(String variantColor, String variantSize) {
        if ((variantColor == null) != (variantSize == null)) {
            throw new IllegalArgumentException("variantColor and variantSize must be provided together");
        }
        if (variantColor != null && variantColor.trim().isEmpty()) {
            throw new IllegalArgumentException("variantColor cannot be blank");
        }
        if (variantSize != null && variantSize.trim().isEmpty()) {
            throw new IllegalArgumentException("variantSize cannot be blank");
        }
    }
}
