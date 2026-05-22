package com.pilarestilo.publication.infrastructure.persistence.repositories;

import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicationJpaRepository extends JpaRepository<PublicationEntity, UUID> {
    Optional<PublicationEntity> findByIdempotencyKey(String idempotencyKey);
    List<PublicationEntity> findAllByOrderByCreatedAtDesc();
}
