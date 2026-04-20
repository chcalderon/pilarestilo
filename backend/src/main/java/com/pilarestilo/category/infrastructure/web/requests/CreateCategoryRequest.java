package com.pilarestilo.category.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank String slug,
        @NotBlank String nameEs,
        @NotBlank String nameEn,
        UUID parentId,
        @PositiveOrZero int sortOrder,
        String imageUrl
) {}
