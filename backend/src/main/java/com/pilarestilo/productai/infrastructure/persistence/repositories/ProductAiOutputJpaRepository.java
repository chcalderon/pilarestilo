package com.pilarestilo.productai.infrastructure.persistence.repositories;

import com.pilarestilo.productai.infrastructure.persistence.entities.ProductAiOutputEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductAiOutputJpaRepository extends JpaRepository<ProductAiOutputEntity, UUID> {
    List<ProductAiOutputEntity> findByJobIdOrderByCreatedAtAsc(UUID jobId);
    void deleteByJobId(UUID jobId);
    Optional<ProductAiOutputEntity> findByJobIdAndAssetId(UUID jobId, UUID assetId);
}
