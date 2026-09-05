package com.pilarestilo.publication.infrastructure.persistence.repositories;

import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PublicationBatchJpaRepository extends JpaRepository<PublicationBatchEntity, UUID> {
    List<PublicationBatchEntity> findAllByOrderByCreatedAtDesc();
}
