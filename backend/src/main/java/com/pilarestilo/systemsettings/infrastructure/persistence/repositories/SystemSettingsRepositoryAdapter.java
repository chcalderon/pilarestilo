package com.pilarestilo.systemsettings.infrastructure.persistence.repositories;

import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.persistence.entities.SystemSettingsEntity;
import org.springframework.stereotype.Component;

@Component
public class SystemSettingsRepositoryAdapter implements SystemSettingsRepository {

    private static final short SINGLETON_ID = 1;

    private final SystemSettingsJpaRepository jpaRepository;

    public SystemSettingsRepositoryAdapter(SystemSettingsJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SystemSettings get() {
        return jpaRepository.findById(SINGLETON_ID)
                .map(this::toDomain)
                .orElseGet(SystemSettings::createDefault);
    }

    @Override
    public SystemSettings save(SystemSettings settings) {
        SystemSettingsEntity saved = jpaRepository.save(toEntity(settings));
        return toDomain(saved);
    }

    private SystemSettingsEntity toEntity(SystemSettings settings) {
        SystemSettingsEntity entity = new SystemSettingsEntity();
        entity.setId(SINGLETON_ID);
        entity.setWhatsappNumber(settings.getWhatsappNumber());
        entity.setInstagramUrl(settings.getInstagramUrl());
        entity.setFacebookUrl(settings.getFacebookUrl());
        entity.setSmtpHost(settings.getSmtpHost());
        entity.setSmtpPort(settings.getSmtpPort());
        entity.setSmtpUsername(settings.getSmtpUsername());
        entity.setSmtpFromEmail(settings.getSmtpFromEmail());
        entity.setSmtpPasswordEncrypted(settings.getSmtpPasswordEncrypted());
        entity.setSmtpAuthEnabled(settings.isSmtpAuthEnabled());
        entity.setSmtpStarttlsEnabled(settings.isSmtpStarttlsEnabled());
        entity.setUpdatedAt(settings.getUpdatedAt());
        entity.setUpdatedBy(settings.getUpdatedBy());
        return entity;
    }

    private SystemSettings toDomain(SystemSettingsEntity entity) {
        return SystemSettings.reconstruct(
                entity.getId(),
                entity.getWhatsappNumber(),
                entity.getInstagramUrl(),
                entity.getFacebookUrl(),
                entity.getSmtpHost(),
                entity.getSmtpPort(),
                entity.getSmtpUsername(),
                entity.getSmtpFromEmail(),
                entity.getSmtpPasswordEncrypted(),
                entity.isSmtpAuthEnabled(),
                entity.isSmtpStarttlsEnabled(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }
}
