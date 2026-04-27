package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.infrastructure.cache.CacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetFeaturedCategoriesUseCase {

    private final CategoryRepository categoryRepository;

    public GetFeaturedCategoriesUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CATEGORY_LIST, key = "'featured'")
    public List<CategoryDto> execute() {
        return categoryRepository.findFeatured().stream()
                .map(CategoryDto::from)
                .toList();
    }
}
