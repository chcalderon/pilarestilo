package com.pilarestilo.navigation.application.mappers;

import com.pilarestilo.navigation.application.dto.NavigationSectionDto;
import com.pilarestilo.navigation.domain.model.NavigationSection;
import org.springframework.stereotype.Component;

@Component
public class NavigationSectionMapper {

    public NavigationSectionDto toDto(NavigationSection section) {
        return new NavigationSectionDto(
                section.getId(),
                section.getRootCategoryId(),
                section.getLayout() != null ? section.getLayout().name() : "COLUMNS",
                section.getColumnCount(),
                section.getBannerImageUrl(),
                section.getBannerTitle(),
                section.getBannerSubtitle(),
                section.getBannerLink(),
                section.isActive(),
                section.getSortOrder(),
                section.getCreatedAt(),
                section.getUpdatedAt()
        );
    }
}
