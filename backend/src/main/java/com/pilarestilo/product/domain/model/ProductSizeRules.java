package com.pilarestilo.product.domain.model;

import com.pilarestilo.shared.domain.DomainException;

import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

final class ProductSizeRules {

    private static final Set<String> ALLOWED_TOKENS = Set.of("XS", "S", "M", "L", "XL", "XXL", "XXXL", "UNICO");
    private static final String NUMERIC_SIZE_PATTERN = "\\d{1,2}(?:[.,]\\d{1,2})?";
    /*
     * Linear on purpose. The grouped spelling, "[\\p{L}\\p{N}]+(?:[ -][\\p{L}\\p{N}]+)*", backtracks
     * catastrophically on a long almost-valid size; the separator rules it encoded are checked
     * below in one pass instead.
     */
    private static final String DESCRIPTOR_PATTERN = "[\\p{L}\\p{N} -]+";

    private ProductSizeRules() {}

    static String normalizeOrThrow(String rawSize) {
        if (rawSize == null) {
            throw new DomainException("Invalid product variant size: null");
        }
        String normalized = rawSize.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        String compact = normalized.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");

        if (compact.matches("[A-Z-]+")) {
            return normalizeAlphaSizeOrThrow(rawSize, normalized, compact);
        }
        if (normalized.matches(NUMERIC_SIZE_PATTERN)) {
            return normalized.replace(',', '.');
        }
        if (isValidDescriptor(normalized) && normalized.length() > 1) {
            return normalized;
        }
        throw new DomainException("Invalid product variant size: " + rawSize);
    }

    private static String normalizeAlphaSizeOrThrow(String rawSize, String normalized, String compact) {
        if (compact.startsWith("-") || compact.endsWith("-") || compact.contains("--")) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        List<String> tokens = List.of(compact.split("-")).stream()
                .filter(token -> !token.isBlank())
                .toList();
        if (tokens.isEmpty()) {
            throw new DomainException("Invalid product variant size: " + rawSize);
        }
        if (tokens.stream().allMatch(ALLOWED_TOKENS::contains)) {
            if (tokens.contains("UNICO") && tokens.size() > 1) {
                throw new DomainException("Invalid product variant size: " + rawSize);
            }
            if (tokens.size() != new HashSet<>(tokens).size()) {
                throw new DomainException("Invalid product variant size: " + rawSize);
            }
            return String.join("-", tokens);
        }
        if (tokens.size() == 1 && tokens.getFirst().length() > 1) {
            return normalized;
        }
        throw new DomainException("Invalid product variant size: " + rawSize);
    }

    /** Letters, digits and single separators between them — never at an end, never doubled. */
    private static boolean isValidDescriptor(String candidate) {
        return candidate.matches(DESCRIPTOR_PATTERN)
                && !startsOrEndsWithSeparator(candidate)
                && !candidate.contains("  ")
                && !candidate.contains("--")
                && !candidate.contains(" -")
                && !candidate.contains("- ");
    }

    private static boolean startsOrEndsWithSeparator(String candidate) {
        char first = candidate.charAt(0);
        char last = candidate.charAt(candidate.length() - 1);
        return first == ' ' || first == '-' || last == ' ' || last == '-';
    }
}
