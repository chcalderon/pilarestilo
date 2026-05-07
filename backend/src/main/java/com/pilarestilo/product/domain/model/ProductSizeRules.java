package com.pilarestilo.product.domain.model;

import com.pilarestilo.shared.domain.DomainException;

import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

final class ProductSizeRules {

    private static final Set<String> ALLOWED_TOKENS = Set.of("XS", "S", "M", "L", "XL", "XXL", "XXXL", "UNICO");

    private ProductSizeRules() {}

    static String normalizeOrThrow(String rawSize) {
        if (rawSize == null) {
            throw new DomainException("Invalid product variant size: null");
        }
        String compact = rawSize.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (compact.isBlank() || !compact.matches("[A-Z-]+")) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        if (compact.startsWith("-") || compact.endsWith("-") || compact.contains("--")) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        List<String> tokens = List.of(compact.split("-")).stream()
                .filter(token -> !token.isBlank())
                .toList();
        if (tokens.isEmpty()) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        if (tokens.stream().anyMatch(token -> !ALLOWED_TOKENS.contains(token))) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        if (tokens.contains("UNICO") && tokens.size() > 1) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        if (tokens.size() != new HashSet<>(tokens).size()) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        return String.join("-", tokens);
    }
}
