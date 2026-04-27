package com.pilarestilo.category.domain.ports;

import com.pilarestilo.category.domain.model.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(UUID id);

    Optional<Category> findBySlug(String slug);

    List<Category> findAll();

    List<Category> findChildren(UUID parentId);

    void deleteById(UUID id);

    boolean existsBySlug(String slug);

    boolean hasAssociatedProducts(UUID categoryId);

    List<Category> findFeatured();
}
