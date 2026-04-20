package com.pilarestilo.category.infrastructure.persistence.repositories;

import com.pilarestilo.category.domain.model.Category;
import com.pilarestilo.category.domain.ports.CategoryRepository;
import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpa) {
        this.jpa = jpa;
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

    private CategoryEntity toEntity(Category c) {
        CategoryEntity e = new CategoryEntity();
        e.setId(c.getId());
        e.setSlug(c.getSlug());
        e.setNameEs(c.getNameEs());
        e.setNameEn(c.getNameEn());
        e.setParentId(c.getParentId());
        e.setSortOrder(c.getSortOrder());
        e.setActive(c.isActive());
        e.setImageUrl(c.getImageUrl());
        e.setCreatedAt(c.getCreatedAt() != null ? c.getCreatedAt() : Instant.now());
        return e;
    }

    private Category toDomain(CategoryEntity e) {
        Category c = Category.create(
                e.getSlug(), e.getNameEs(), e.getNameEn(),
                e.getParentId(), e.getSortOrder(), e.getImageUrl()
        );
        c.setId(e.getId());
        c.setActive(e.isActive());
        c.setCreatedAt(e.getCreatedAt());
        return c;
    }
}
