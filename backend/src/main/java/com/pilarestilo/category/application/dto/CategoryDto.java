package com.pilarestilo.category.application.dto;

import com.pilarestilo.category.domain.model.Category;

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
        String imageUrl
) {
    public static CategoryDto from(Category c) {
        return new CategoryDto(
                c.getId(), c.getSlug(), c.getNameEs(), c.getNameEn(),
                c.getParentId(), c.getSortOrder(), c.isActive(), c.isFeatured(), c.getImageUrl()
        );
    }
}
