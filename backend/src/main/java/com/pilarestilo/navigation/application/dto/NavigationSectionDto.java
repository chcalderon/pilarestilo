package com.pilarestilo.navigation.application.dto;

import java.time.Instant;
import java.util.UUID;

public record NavigationSectionDto(
        UUID id,
        UUID rootCategoryId,
        String layout,
        int columnCount,
        String bannerImageUrl,
        String bannerTitle,
        String bannerSubtitle,
        String bannerLink,
        boolean active,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {}
