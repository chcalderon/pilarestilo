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

    @org.springframework.data.jpa.repository.Query("""
            select p from PublicationEntity p
            where p.status = com.pilarestilo.publication.domain.enums.PublicationStatus.PUBLISHED
              and p.externalPostId is not null
              and p.batchId in (
                  select b.id from PublicationBatchEntity b where b.campaignLabel = :label
              )
            order by p.createdAt asc
            """)
    List<PublicationEntity> findPublishedWithPostIdByCampaignLabel(String label);

    @org.springframework.data.jpa.repository.Query("""
            select p from PublicationEntity p
            where p.status = com.pilarestilo.publication.domain.enums.PublicationStatus.PUBLISHED
              and p.externalPostId is not null
              and p.publishedAt >= :since
            order by p.publishedAt asc
            """)
    List<PublicationEntity> findPublishedWithPostIdSince(Instant since);
}
