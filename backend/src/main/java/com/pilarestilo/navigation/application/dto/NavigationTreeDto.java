package com.pilarestilo.navigation.application.dto;

import java.util.List;
import java.util.UUID;

public record NavigationTreeDto(
        List<SectionDto> sections
) {
    public record SectionDto(
            UUID rootCategoryId,
            String rootCategorySlug,
            String rootCategoryName,
            String heroImageUrl,
            String layout,
            int columnCount,
            String bannerImageUrl,
            String bannerTitle,
            String bannerSubtitle,
            String bannerLink,
            List<ChildDto> children
    ) {}

    public record ChildDto(
            UUID id,
            String slug,
            String name,
            String imageUrl,
            boolean featured,
            List<ChildDto> children
    ) {}
}
