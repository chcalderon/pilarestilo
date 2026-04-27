package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.cache.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateCategoryUseCase {

    private final CategoryRepository categoryRepository;

    public UpdateCategoryUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.CATEGORY_LIST, CacheNames.CATEGORY_TREE}, allEntries = true)
    public CategoryDto execute(UUID id, String slug, String nameEs, String nameEn,
                               UUID parentId, int sortOrder, boolean active, boolean featured, String imageUrl) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new DomainException("Category not found: " + id));
        c.update(slug, nameEs, nameEn, parentId, sortOrder, active, featured, imageUrl);
        return CategoryDto.from(categoryRepository.save(c));
    }
}
