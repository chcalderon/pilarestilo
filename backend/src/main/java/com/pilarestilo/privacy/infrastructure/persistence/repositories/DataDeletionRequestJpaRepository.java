package com.pilarestilo.privacy.infrastructure.persistence.repositories;

import com.pilarestilo.privacy.infrastructure.persistence.entities.DataDeletionRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DataDeletionRequestJpaRepository extends JpaRepository<DataDeletionRequestEntity, UUID> {

    Optional<DataDeletionRequestEntity> findByUserIdAndStatus(UUID userId, String status);

    Page<DataDeletionRequestEntity> findByStatusOrderByRequestedAtAsc(String status, Pageable pageable);
}
