package com.pilarestilo.privacy.infrastructure.persistence.repositories;

import com.pilarestilo.privacy.domain.enums.ConsentType;
import com.pilarestilo.privacy.domain.model.DataConsent;
import com.pilarestilo.privacy.domain.ports.DataConsentRepository;
import com.pilarestilo.privacy.infrastructure.persistence.entities.DataConsentEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DataConsentRepositoryAdapter implements DataConsentRepository {

    private final DataConsentJpaRepository jpaRepository;

    public DataConsentRepositoryAdapter(DataConsentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public DataConsent save(DataConsent consent) {
        return toDomain(jpaRepository.save(toEntity(consent)));
    }

    @Override
    public List<DataConsent> findByUserId(UUID userId) {
        return jpaRepository.findByUserIdOrderByAcceptedAtDesc(userId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<DataConsent> findLive(UUID userId, ConsentType type) {
        return jpaRepository
                .findByUserIdAndConsentTypeAndRevokedAtIsNull(userId, type.name())
                .map(this::toDomain);
    }

    private DataConsentEntity toEntity(DataConsent consent) {
        DataConsentEntity entity = new DataConsentEntity();
        entity.setId(consent.getId());
        entity.setUserId(consent.getUserId());
        entity.setConsentType(consent.getType().name());
        entity.setPolicyVersion(consent.getPolicyVersion());
        entity.setAcceptedAt(consent.getAcceptedAt());
        entity.setRevokedAt(consent.getRevokedAt());
        entity.setIpAddress(consent.getIpAddress());
        entity.setUserAgent(consent.getUserAgent());
        entity.setCreatedAt(consent.getCreatedAt());
        return entity;
    }

    private DataConsent toDomain(DataConsentEntity entity) {
        return DataConsent.reconstruct(
                entity.getId(),
                entity.getUserId(),
                ConsentType.valueOf(entity.getConsentType()),
                entity.getPolicyVersion(),
                entity.getAcceptedAt(),
                entity.getRevokedAt(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getCreatedAt());
    }
}
