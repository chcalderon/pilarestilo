package com.pilarestilo.product.domain.model;

import com.pilarestilo.shared.domain.DomainException;

/**
 * Purely structural: trim, collapse internal whitespace, reject blank, cap length.
 *
 * <p>What a value <em>means</em> -- whether "S" is a valid size for this product's
 * category, whether "34" is in range -- is the category's variant field config,
 * enforced at write time by {@code CategoryVariantFieldValidator}, not this class.
 * This class only guards against garbage making it into storage at all: it runs on
 * every read, via {@code ProductVariant}'s constructor, so it must never become
 * stricter in a way that could reject a variant that was valid when it was saved.
 */
final class ProductSizeRules {

    private static final int MAX_LENGTH = 40;

    private ProductSizeRules() {}

    static String normalizeOrThrow(String rawSize) {
        if (rawSize == null) {
            throw new DomainException("Invalid product variant size: null");
        }
        String normalized = rawSize.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new DomainException("Invalid product variant size: exceeds " + MAX_LENGTH + " characters");
        }
        return normalized;
    }
}
