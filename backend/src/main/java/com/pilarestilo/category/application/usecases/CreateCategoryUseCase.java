package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.domain.enums.CategoryType;
import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.cache.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateCategoryUseCase {

    private final CategoryRepository categoryRepository;

    public CreateCategoryUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // One parameter per field the category form actually submits.
    @SuppressWarnings("java:S107")
    @Transactional
    @CacheEvict(cacheNames = {CacheNames.CATEGORY_LIST, CacheNames.CATEGORY_TREE}, allEntries = true)
    public CategoryDto execute(String slug, String nameEs, String nameEn,
                               UUID parentId, int sortOrder, String imageUrl,
                               boolean active, boolean featured, boolean menuVisible,
                               String categoryType, String heroImageUrl) {
        if (categoryRepository.existsBySlug(slug)) {
            throw new DomainException("Category slug already exists: " + slug);
        }
        Category c = Category.create(slug, nameEs, nameEn, parentId, sortOrder, imageUrl);
        c.setActive(active);
        c.setFeatured(featured);
        CategoryType type;
        try {
            type = categoryType != null ? CategoryType.valueOf(categoryType) : CategoryType.GENERIC;
        } catch (IllegalArgumentException _) {
            throw new DomainException("Invalid categoryType: " + categoryType);
        }
        c.updateMenuMetadata(menuVisible, type, heroImageUrl);
        return CategoryDto.from(categoryRepository.save(c));
    }
}
