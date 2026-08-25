package com.pilarestilo.category.application.usecases;

import com.pilarestilo.category.application.dto.CategoryDto;
import com.pilarestilo.category.domain.enums.CategoryType;
import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.category.infrastructure.web.requests.CategoryVariantFieldRequest;
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

    // One parameter per field the category form actually submits.
    @SuppressWarnings("java:S107")
    @Transactional
    @CacheEvict(cacheNames = {CacheNames.CATEGORY_LIST, CacheNames.CATEGORY_TREE}, allEntries = true)
    public CategoryDto execute(UUID id, String slug, String nameEs, String nameEn,
                               UUID parentId, int sortOrder, boolean active, boolean featured, String imageUrl,
                               boolean menuVisible, String categoryType, String heroImageUrl,
                               boolean definesVariantFields, CategoryVariantFieldRequest primary,
                               CategoryVariantFieldRequest secondary) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new DomainException("Category not found: " + id));
        c.update(slug, nameEs, nameEn, parentId, sortOrder, active, featured, imageUrl);
        CategoryType type;
        try {
            type = categoryType != null ? CategoryType.valueOf(categoryType) : CategoryType.GENERIC;
        } catch (IllegalArgumentException _) {
            throw new DomainException("Invalid categoryType: " + categoryType);
        }
        c.updateMenuMetadata(menuVisible, type, heroImageUrl);
        c.updateVariantFieldConfig(definesVariantFields, toVariantFieldConfig(definesVariantFields, primary, secondary));
        return CategoryDto.from(categoryRepository.save(c));
    }

    private CategoryVariantFieldConfig toVariantFieldConfig(
            boolean definesVariantFields, CategoryVariantFieldRequest primary, CategoryVariantFieldRequest secondary) {
        if (!definesVariantFields) return null;
        if (primary == null || secondary == null) {
            throw new DomainException("primary and secondary field config are required when defining variant fields");
        }
        return new CategoryVariantFieldConfig(toFieldConfig(primary), toFieldConfig(secondary));
    }

    private CategoryVariantFieldConfig.FieldConfig toFieldConfig(CategoryVariantFieldRequest req) {
        return new CategoryVariantFieldConfig.FieldConfig(
                req.label(),
                CategoryVariantFieldConfig.InputType.valueOf(req.inputType()),
                req.options(), req.min(), req.max(), req.allowMultiple(), req.allowCustom());
    }
}
