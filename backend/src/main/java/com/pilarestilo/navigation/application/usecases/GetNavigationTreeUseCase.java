package com.pilarestilo.navigation.application.usecases;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.navigation.application.dto.NavigationTreeDto;
import com.pilarestilo.navigation.domain.model.NavigationSection;
import com.pilarestilo.navigation.domain.ports.NavigationSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GetNavigationTreeUseCase {

    private static final int MAX_DEPTH = 3;

    private final NavigationSectionRepository navigationSectionRepository;
    private final CategoryRepository categoryRepository;

    public GetNavigationTreeUseCase(NavigationSectionRepository navigationSectionRepository,
                                    CategoryRepository categoryRepository) {
        this.navigationSectionRepository = navigationSectionRepository;
        this.categoryRepository = categoryRepository;
    }

    public NavigationTreeDto execute(String locale) {
        String resolvedLocale = (locale == null || locale.isBlank()) ? "es" : locale.toLowerCase();

        // Load all active categories once and build a lookup map keyed by parentId
        List<Category> allActive = categoryRepository.findAll().stream()
                .filter(Category::isActive)
                .toList();

        Map<UUID, List<Category>> byParent = allActive.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        Map<UUID, Category> byId = allActive.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        List<NavigationSection> sections = navigationSectionRepository.findAllActiveOrdered();

        if (sections.isEmpty()) {
            return buildFallbackTree(allActive, byParent, resolvedLocale);
        }

        List<NavigationTreeDto.SectionDto> sectionDtos = sections.stream()
                .map(section -> toSectionDto(section, byId, byParent, resolvedLocale))
                .filter(Objects::nonNull)
                .toList();

        return new NavigationTreeDto(sectionDtos);
    }

    private NavigationTreeDto buildFallbackTree(List<Category> allActive,
                                                  Map<UUID, List<Category>> byParent,
                                                  String locale) {
        List<Category> roots = allActive.stream()
                .filter(c -> c.getParentId() == null)
                .filter(Category::isMenuVisible)
                .sorted(java.util.Comparator.comparingInt(Category::getSortOrder))
                .toList();

        List<NavigationTreeDto.SectionDto> sections = roots.stream()
                .map(root -> new NavigationTreeDto.SectionDto(
                        root.getId(),
                        root.getSlug(),
                        localize(root, locale),
                        root.getHeroImageUrl(),
                        "COLUMNS",
                        4,
                        null, null, null, null,
                        buildChildren(root.getId(), byParent, locale, 1)
                ))
                .toList();

        return new NavigationTreeDto(sections);
    }

    private NavigationTreeDto.SectionDto toSectionDto(NavigationSection section,
                                                       Map<UUID, Category> byId,
                                                       Map<UUID, List<Category>> byParent,
                                                       String locale) {
        UUID rootId = section.getRootCategoryId();
        if (rootId == null) return null;

        Category root = byId.get(rootId);
        if (root == null || !root.isActive()) return null;

        List<NavigationTreeDto.ChildDto> children = buildChildren(rootId, byParent, locale, 1);

        return new NavigationTreeDto.SectionDto(
                root.getId(),
                root.getSlug(),
                localize(root, locale),
                root.getHeroImageUrl(),
                section.getLayout() != null ? section.getLayout().name() : "COLUMNS",
                section.getColumnCount(),
                section.getBannerImageUrl(),
                section.getBannerTitle(),
                section.getBannerSubtitle(),
                section.getBannerLink(),
                children
        );
    }

    private List<NavigationTreeDto.ChildDto> buildChildren(UUID parentId,
                                                            Map<UUID, List<Category>> byParent,
                                                            String locale,
                                                            int depth) {
        if (depth > MAX_DEPTH) return List.of();

        List<Category> children = byParent.getOrDefault(parentId, List.of()).stream()
                .filter(Category::isActive)
                .filter(Category::isMenuVisible)
                .sorted(java.util.Comparator.comparingInt(Category::getSortOrder))
                .toList();

        return children.stream()
                .map(c -> new NavigationTreeDto.ChildDto(
                        c.getId(),
                        c.getSlug(),
                        localize(c, locale),
                        c.getImageUrl(),
                        c.isFeatured(),
                        buildChildren(c.getId(), byParent, locale, depth + 1)
                ))
                .toList();
    }

    private String localize(Category category, String locale) {
        if ("en".equals(locale)) {
            String name = category.getNameEn();
            return (name != null && !name.isBlank()) ? name : category.getNameEs();
        }
        // default: es
        String name = category.getNameEs();
        return (name != null && !name.isBlank()) ? name : category.getNameEn();
    }
}
