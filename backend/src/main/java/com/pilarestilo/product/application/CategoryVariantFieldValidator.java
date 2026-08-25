package com.pilarestilo.product.application;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.model.ShapeCategoryResolver;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.product.application.dto.ProductVariantInput;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the one shape category (if any) among a product's assigned categories and
 * validates submitted variant values against its field config -- write time only. See
 * ProductSizeRules for why this must never run on read.
 */
@Component
public class CategoryVariantFieldValidator {

    private final CategoryRepository categoryRepository;

    public CategoryVariantFieldValidator(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public CategoryVariantFieldConfig resolveConfig(Set<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return CategoryVariantFieldConfig.genericFallback();
        }
        List<Category> categories = categoryRepository.findAllByIds(categoryIds);
        return ShapeCategoryResolver.resolveOne(categories)
                .map(Category::getVariantFieldConfig)
                .orElseGet(CategoryVariantFieldConfig::genericFallback);
    }

    public void validate(CategoryVariantFieldConfig config, List<ProductVariantInput> variants) {
        if (variants == null) return;
        for (ProductVariantInput variant : variants) {
            validateField(config.primary(), variant.color());
            validateField(config.secondary(), variant.size());
        }
    }

    private void validateField(CategoryVariantFieldConfig.FieldConfig field, String rawValue) {
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

    private void validateToken(CategoryVariantFieldConfig.FieldConfig field, String token) {
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
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
