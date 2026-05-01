package com.pilarestilo.productai.infrastructure.persistence.repositories;

import com.pilarestilo.productai.infrastructure.persistence.entities.ProductAiAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductAiAssetJpaRepository extends JpaRepository<ProductAiAssetEntity, UUID> {
    List<ProductAiAssetEntity> findByDraftIdOrderBySortOrderAscCreatedAtAsc(UUID draftId);
}
