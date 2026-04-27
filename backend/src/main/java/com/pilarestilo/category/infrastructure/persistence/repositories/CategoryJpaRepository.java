package com.pilarestilo.category.infrastructure.persistence.repositories;

import com.pilarestilo.category.infrastructure.persistence.entities.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, UUID> {
    Optional<CategoryEntity> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<CategoryEntity> findByParentId(UUID parentId);
    List<CategoryEntity> findByFeaturedTrueAndActiveTrueOrderBySortOrderAsc();
}
