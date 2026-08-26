package com.pilarestilo.category.infrastructure.persistence.repositories;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import com.pilarestilo.product.infrastructure.persistence.repositories.ProductJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
        return c;
    }
}
