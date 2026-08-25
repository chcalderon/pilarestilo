package com.pilarestilo.category.domain.valueobjects;

import com.pilarestilo.shared.domain.DomainException;

import java.util.List;

public record CategoryVariantFieldConfig(FieldConfig primary, FieldConfig secondary) {

    public enum InputType { FREE_TEXT, OPTIONS, RANGE }

    public record FieldConfig(
            String label,
            InputType inputType,
            List<String> options,
            Integer min,
            Integer max,
            boolean allowMultiple,
            boolean allowCustom
    ) {
        public FieldConfig {
            if (label == null || label.isBlank()) {
                throw new DomainException("Variant field label cannot be blank");
            }
            label = label.trim();
            options = options == null ? List.of() : List.copyOf(options);
            if (inputType == InputType.OPTIONS && options.isEmpty()) {
                throw new DomainException("Variant field with OPTIONS input type requires at least one option");
            }
            if (inputType == InputType.RANGE) {
                if (min == null || max == null) {
                    throw new DomainException("Variant field with RANGE input type requires both min and max");
                }
                if (min >= max) {
                    throw new DomainException("Variant field RANGE min must be less than max");
                }
            }
        }
    }

    public static CategoryVariantFieldConfig genericFallback() {
        return new CategoryVariantFieldConfig(
                new FieldConfig("Variante", InputType.FREE_TEXT, List.of(), null, null, true, true),
                new FieldConfig("Detalle", InputType.FREE_TEXT, List.of(), null, null, true, true)
        );
    }
}
