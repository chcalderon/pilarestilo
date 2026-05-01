package com.pilarestilo.productai.infrastructure.persistence.repositories;

import com.pilarestilo.productai.domain.enums.ProductAiJobStatus;
import com.pilarestilo.productai.infrastructure.persistence.entities.ProductAiJobEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductAiJobJpaRepository extends JpaRepository<ProductAiJobEntity, UUID> {

    @Query("""
           select j
             from ProductAiJobEntity j
            where j.status = com.pilarestilo.productai.domain.enums.ProductAiJobStatus.PENDING
              and (j.nextRetryAt is null or j.nextRetryAt <= :now)
            order by j.createdAt asc
           """)
    List<ProductAiJobEntity> findDuePending(@Param("now") Instant now, Pageable pageable);

    List<ProductAiJobEntity> findTop100ByOrderByCreatedAtDesc();

    boolean existsByDraftIdAndStatusIn(UUID draftId, List<ProductAiJobStatus> statuses);

    Optional<ProductAiJobEntity> findFirstByDraftIdAndStatusOrderByFinishedAtDesc(UUID draftId, ProductAiJobStatus status);
}
