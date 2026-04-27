package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.cache.CacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ReorderCategoriesUseCase {

    public record Item(UUID id, int sortOrder) {}

    private final CategoryRepository categoryRepository;

    public ReorderCategoriesUseCase(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheNames.CATEGORY_LIST, CacheNames.CATEGORY_TREE}, allEntries = true)
    public void execute(List<Item> items) {
        for (Item item : items) {
            Category c = categoryRepository.findById(item.id())
                    .orElseThrow(() -> new DomainException("Category not found: " + item.id()));
            c.reorder(item.sortOrder());
            categoryRepository.save(c);
        }
    }
}
