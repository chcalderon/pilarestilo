package com.pilarestilo.category.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record UpdateCategoryRequest(
        @NotBlank String slug,
        @NotBlank String nameEs,
        @NotBlank String nameEn,
        UUID parentId,
        @PositiveOrZero int sortOrder,
        boolean active,
        boolean featured,
        String imageUrl,
        boolean menuVisible,
        @Pattern(regexp = "GENERIC|CLOTHING|SHOES|JEWELRY|ACCESSORY|COLLECTION|SEASON", message = "invalid categoryType") String categoryType,
        String heroImageUrl
) {}
