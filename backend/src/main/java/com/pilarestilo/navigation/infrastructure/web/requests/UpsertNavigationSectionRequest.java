package com.pilarestilo.navigation.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record UpsertNavigationSectionRequest(
        @NotNull UUID rootCategoryId,
        @NotBlank @Pattern(regexp = "COLUMNS|FEATURED_GRID|EDITORIAL", message = "layout must be COLUMNS, FEATURED_GRID, or EDITORIAL") String layout,
        int columnCount,
        String bannerImageUrl,
        String bannerTitle,
        String bannerSubtitle,
        String bannerLink,
        boolean active,
        int sortOrder
) {}
