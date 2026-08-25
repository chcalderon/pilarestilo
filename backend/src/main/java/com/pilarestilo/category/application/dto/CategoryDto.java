package com.pilarestilo.category.application.dto;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;

import java.util.List;
import java.util.UUID;

public record CategoryDto(
        UUID id,
        String slug,
        String nameEs,
        String nameEn,
        UUID parentId,
        int sortOrder,
        boolean active,
        boolean featured,
        String imageUrl,
        boolean menuVisible,
        String categoryType,
        String heroImageUrl,
        boolean definesVariantFields,
        CategoryVariantFieldConfigDto variantFieldConfig
) {
    public static CategoryDto from(Category c) {
        return new CategoryDto(
                c.getId(), c.getSlug(), c.getNameEs(), c.getNameEn(),
                c.getParentId(), c.getSortOrder(), c.isActive(), c.isFeatured(), c.getImageUrl(),
                c.isMenuVisible(),
                c.getCategoryType() != null ? c.getCategoryType().name() : "GENERIC",
                c.getHeroImageUrl(),
                c.isDefinesVariantFields(),
                CategoryVariantFieldConfigDto.from(c.getVariantFieldConfig())
        );
    }

    public record CategoryVariantFieldConfigDto(FieldDto primary, FieldDto secondary) {
        public static CategoryVariantFieldConfigDto from(CategoryVariantFieldConfig config) {
            if (config == null) return null;
            return new CategoryVariantFieldConfigDto(FieldDto.from(config.primary()), FieldDto.from(config.secondary()));
        }

        public record FieldDto(String label, String inputType, List<String> options, Integer min, Integer max,
                                boolean allowMultiple, boolean allowCustom) {
            public static FieldDto from(CategoryVariantFieldConfig.FieldConfig field) {
                return new FieldDto(field.label(), field.inputType().name(), field.options(),
                        field.min(), field.max(), field.allowMultiple(), field.allowCustom());
            }
        }
    }
}
