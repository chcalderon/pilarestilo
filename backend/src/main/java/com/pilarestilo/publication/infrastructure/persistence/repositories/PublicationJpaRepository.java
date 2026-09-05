package com.pilarestilo.publication.infrastructure.persistence.repositories;

import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicationJpaRepository extends JpaRepository<PublicationEntity, UUID> {
    Optional<PublicationEntity> findByIdempotencyKey(String idempotencyKey);
    List<PublicationEntity> findAllByOrderByCreatedAtDesc();
    List<PublicationEntity> findTop20ByProductIdOrderByCreatedAtDesc(UUID productId);
    List<PublicationEntity> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
    List<PublicationEntity> findByBatchIdInOrderByCreatedAtAsc(Collection<UUID> batchIds);
    List<PublicationEntity> findByBatchIdIsNullOrderByCreatedAtAsc();
    List<PublicationEntity> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            PublicationStatus status, Instant cutoff);
}
