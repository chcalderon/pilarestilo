package com.pilarestilo.category.infrastructure.persistence.repositories;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.domain.valueobjects.CategoryVariantFieldConfig;
import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import com.pilarestilo.product.infrastructure.persistence.repositories.ProductJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;
    private final ProductJpaRepository productJpa;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpa, ProductJpaRepository productJpa) {
        this.jpa = jpa;
        this.productJpa = productJpa;
    }

    @Override
    public Category save(Category c) {
        CategoryEntity e = toEntity(c);
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return jpa.findBySlug(slug).map(this::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Category> findAllByIds(Collection<UUID> ids) {
        return jpa.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Category> findChildren(UUID parentId) {
        return jpa.findByParentId(parentId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpa.existsBySlug(slug);
    }

    @Override
    public boolean hasAssociatedProducts(UUID categoryId) {
        return productJpa.countByCategoriesId(categoryId) > 0;
    }

    @Override
    public List<Category> findFeatured() {
        return jpa.findByFeaturedTrueAndActiveTrueOrderBySortOrderAsc().stream().map(this::toDomain).toList();
    }

    private CategoryEntity toEntity(Category c) {
        CategoryEntity e = new CategoryEntity();
        e.setId(c.getId());
        e.setSlug(c.getSlug());
        e.setNameEs(c.getNameEs());
        e.setNameEn(c.getNameEn());
        e.setParentId(c.getParentId());
        e.setSortOrder(c.getSortOrder());
        e.setActive(c.isActive());
        e.setFeatured(c.isFeatured());
        e.setImageUrl(c.getImageUrl());
        e.setCreatedAt(c.getCreatedAt() != null ? c.getCreatedAt() : Instant.now());
        e.setMenuVisible(c.isMenuVisible());
        e.setCategoryType(c.getCategoryType());
        e.setHeroImageUrl(c.getHeroImageUrl());
        e.setDefinesVariantFields(c.isDefinesVariantFields());
        e.setVariantFieldConfig(toRawConfig(c.getVariantFieldConfig()));
        return e;
    }

    private Category toDomain(CategoryEntity e) {
        Category c = Category.create(
                e.getSlug(), e.getNameEs(), e.getNameEn(),
                e.getParentId(), e.getSortOrder(), e.getImageUrl()
        );
        c.setId(e.getId());
        c.setActive(e.isActive());
        c.setFeatured(e.isFeatured());
        c.setCreatedAt(e.getCreatedAt());
        c.setMenuVisible(e.isMenuVisible());
        c.setCategoryType(e.getCategoryType());
        c.setHeroImageUrl(e.getHeroImageUrl());
        c.updateVariantFieldConfig(e.isDefinesVariantFields(), fromRawConfig(e.getVariantFieldConfig()));
        return c;
    }

    private static Map<String, Object> toRawConfig(CategoryVariantFieldConfig config) {
        if (config == null) return null;
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("primary", toRawField(config.primary()));
        raw.put("secondary", toRawField(config.secondary()));
        return raw;
    }

    private static Map<String, Object> toRawField(CategoryVariantFieldConfig.FieldConfig field) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("label", field.label());
        raw.put("inputType", field.inputType().name());
        raw.put("options", field.options());
        raw.put("min", field.min());
        raw.put("max", field.max());
        raw.put("allowMultiple", field.allowMultiple());
        raw.put("allowCustom", field.allowCustom());
        return raw;
    }

    @SuppressWarnings("unchecked")
    private static CategoryVariantFieldConfig fromRawConfig(Map<String, Object> raw) {
        if (raw == null) return null;
        return new CategoryVariantFieldConfig(
                fromRawField((Map<String, Object>) raw.get("primary")),
                fromRawField((Map<String, Object>) raw.get("secondary")));
    }

    @SuppressWarnings("unchecked")
    private static CategoryVariantFieldConfig.FieldConfig fromRawField(Map<String, Object> raw) {
        List<String> options = raw.get("options") == null
                ? List.of()
                : ((List<Object>) raw.get("options")).stream().map(String::valueOf).toList();
        return new CategoryVariantFieldConfig.FieldConfig(
                (String) raw.get("label"),
                CategoryVariantFieldConfig.InputType.valueOf((String) raw.get("inputType")),
                options,
                raw.get("min") == null ? null : ((Number) raw.get("min")).intValue(),
                raw.get("max") == null ? null : ((Number) raw.get("max")).intValue(),
                Boolean.TRUE.equals(raw.get("allowMultiple")),
                Boolean.TRUE.equals(raw.get("allowCustom")));
    }
}
