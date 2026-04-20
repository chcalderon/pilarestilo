package com.pilarestilo.category.application.dto;

import java.util.List;
import java.util.UUID;

public record CategoryTreeNode(
        UUID id,
        String slug,
        String nameEs,
        String nameEn,
        int sortOrder,
        boolean active,
        String imageUrl,
        List<CategoryTreeNode> children
) {}
