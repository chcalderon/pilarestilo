package com.pilarestilo.publication.infrastructure.persistence.repositories;

import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PublicationMetricsJpaRepository extends JpaRepository<PublicationMetricsEntity, UUID> {
    List<PublicationMetricsEntity> findByPublicationIdIn(Collection<UUID> ids);
}
