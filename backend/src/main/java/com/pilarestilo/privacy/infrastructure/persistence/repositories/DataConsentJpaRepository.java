package com.pilarestilo.privacy.infrastructure.persistence.repositories;

import com.pilarestilo.privacy.infrastructure.persistence.entities.DataConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataConsentJpaRepository extends JpaRepository<DataConsentEntity, UUID> {

    List<DataConsentEntity> findByUserIdOrderByAcceptedAtDesc(UUID userId);

    Optional<DataConsentEntity> findByUserIdAndConsentTypeAndRevokedAtIsNull(UUID userId, String consentType);
}
