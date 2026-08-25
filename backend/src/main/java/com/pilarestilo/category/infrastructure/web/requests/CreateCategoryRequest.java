package com.pilarestilo.category.infrastructure.web.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank String slug,
        @NotBlank String nameEs,
        @NotBlank String nameEn,
        UUID parentId,
        @PositiveOrZero int sortOrder,
        String imageUrl,
        boolean active,
        boolean featured,
        boolean menuVisible,
        @Pattern(regexp = "GENERIC|CLOTHING|SHOES|JEWELRY|ACCESSORY|COLLECTION|SEASON", message = "invalid categoryType") String categoryType,
        String heroImageUrl,
        boolean definesVariantFields,
        @Valid CategoryVariantFieldRequest primary,
        @Valid CategoryVariantFieldRequest secondary
) {}
