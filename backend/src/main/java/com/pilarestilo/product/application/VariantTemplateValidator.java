package com.pilarestilo.product.application;

import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.model.VariantTemplate;
import com.pilarestilo.varianttemplate.domain.ports.VariantTemplateRepository;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves a product's variant template (if any) and validates submitted variant values against
 * its field config -- write time only. See ProductSizeRules for why this must never run on read.
 */
@Component
public class VariantTemplateValidator {

    private final VariantTemplateRepository variantTemplateRepository;

    public VariantTemplateValidator(VariantTemplateRepository variantTemplateRepository) {
        this.variantTemplateRepository = variantTemplateRepository;
    }

    public VariantFieldConfig resolveConfig(UUID variantTemplateId) {
        if (variantTemplateId == null) {
            return VariantFieldConfig.genericFallback();
        }
        return variantTemplateRepository.findById(variantTemplateId)
                .map(VariantTemplate::getConfig)
                .orElseThrow(() -> new DomainException("Variant template not found: " + variantTemplateId));
    }

    public void validate(VariantFieldConfig config, List<ProductVariantInput> variants) {
        if (variants == null) return;
        for (ProductVariantInput variant : variants) {
            validateField(config.primary(), variant.color());
            validateField(config.secondary(), variant.size());
        }
    }

    private void validateField(VariantFieldConfig.FieldConfig field, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isBlank()) {
            throw new DomainException(field.label() + " cannot be blank");
        }
        List<String> tokens = field.allowMultiple() ? List.of(value.split("-")) : List.of(value);
        Set<String> seen = new HashSet<>();
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isBlank()) {
                throw new DomainException(field.label() + ": empty value in combined field");
            }
            if (!seen.add(token.toLowerCase())) {
                throw new DomainException(field.label() + ": duplicated value " + token);
            }
            validateToken(field, token);
        }
    }

    private void validateToken(VariantFieldConfig.FieldConfig field, String token) {
        switch (field.inputType()) {
            case FREE_TEXT -> { /* non-blank already checked above */ }
            case OPTIONS -> {
                boolean inList = field.options().stream().anyMatch(option -> option.equalsIgnoreCase(token));
                if (!inList && !field.allowCustom()) {
                    throw new DomainException(field.label() + ": " + token + " is not one of " + field.options());
                }
            }
            case RANGE -> {
                Integer number = parseIntOrNull(token);
                boolean inRange = number != null && number >= field.min() && number <= field.max();
                if (!inRange && !field.allowCustom()) {
                    throw new DomainException(field.label() + ": " + token + " is not between "
                            + field.min() + " and " + field.max());
                }
            }
        }
    }

    private static Integer parseIntOrNull(String token) {
        try {
            return Integer.valueOf(token);
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
