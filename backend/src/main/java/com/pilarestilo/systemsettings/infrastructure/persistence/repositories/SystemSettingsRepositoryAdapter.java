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
        entity.setMediaStorageProvider(settings.getMediaStorageProvider().name());
        entity.setMediaS3Endpoint(settings.getMediaS3Endpoint());
        entity.setMediaS3Region(settings.getMediaS3Region());
        entity.setMediaS3Bucket(settings.getMediaS3Bucket());
        entity.setMediaS3AccessKeyId(settings.getMediaS3AccessKeyId());
        entity.setMediaS3SecretKeyEncrypted(settings.getMediaS3SecretKeyEncrypted());
        entity.setMediaS3PathStyleEnabled(settings.isMediaS3PathStyleEnabled());
        entity.setMediaS3PublicBaseUrl(settings.getMediaS3PublicBaseUrl());
        entity.setSmtpHost(settings.getSmtpHost());
        entity.setSmtpPort(settings.getSmtpPort());
        entity.setSmtpUsername(settings.getSmtpUsername());
        entity.setSmtpFromEmail(settings.getSmtpFromEmail());
        entity.setSmtpPasswordEncrypted(settings.getSmtpPasswordEncrypted());
        entity.setSmtpAuthEnabled(settings.isSmtpAuthEnabled());
        entity.setSmtpStarttlsEnabled(settings.isSmtpStarttlsEnabled());
        entity.setNotificationProvider(settings.getNotificationProvider().name());
        entity.setWhatsappSimulatedTo(settings.getWhatsappSimulatedTo());
        entity.setWhatsappSimulatedSender(settings.getWhatsappSimulatedSender());
        entity.setWhatsappTwilioApiBaseUrl(settings.getWhatsappTwilioApiBaseUrl());
        entity.setWhatsappTwilioAccountSid(settings.getWhatsappTwilioAccountSid());
        entity.setWhatsappTwilioAuthTokenEncrypted(settings.getWhatsappTwilioAuthTokenEncrypted());
        entity.setWhatsappTwilioFrom(settings.getWhatsappTwilioFrom());
        entity.setWhatsappTwilioToFallback(settings.getWhatsappTwilioToFallback());
        entity.setWhatsappTwilioSenderAlias(settings.getWhatsappTwilioSenderAlias());
        entity.setSendgridApiBaseUrl(settings.getSendgridApiBaseUrl());
        entity.setSendgridApiKeyEncrypted(settings.getSendgridApiKeyEncrypted());
        entity.setSendgridFromEmail(settings.getSendgridFromEmail());
        entity.setSendgridSenderName(settings.getSendgridSenderName());
        entity.setSendgridToFallback(settings.getSendgridToFallback());
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
                entity.getMediaStorageProvider(),
                entity.getMediaS3Endpoint(),
                entity.getMediaS3Region(),
                entity.getMediaS3Bucket(),
                entity.getMediaS3AccessKeyId(),
                entity.getMediaS3SecretKeyEncrypted(),
                entity.isMediaS3PathStyleEnabled(),
                entity.getMediaS3PublicBaseUrl(),
                entity.getSmtpHost(),
                entity.getSmtpPort(),
                entity.getSmtpUsername(),
                entity.getSmtpFromEmail(),
                entity.getSmtpPasswordEncrypted(),
                entity.isSmtpAuthEnabled(),
                entity.isSmtpStarttlsEnabled(),
                entity.getNotificationProvider(),
                entity.getWhatsappSimulatedTo(),
                entity.getWhatsappSimulatedSender(),
                entity.getWhatsappTwilioApiBaseUrl(),
                entity.getWhatsappTwilioAccountSid(),
                entity.getWhatsappTwilioAuthTokenEncrypted(),
                entity.getWhatsappTwilioFrom(),
                entity.getWhatsappTwilioToFallback(),
                entity.getWhatsappTwilioSenderAlias(),
                entity.getSendgridApiBaseUrl(),
                entity.getSendgridApiKeyEncrypted(),
                entity.getSendgridFromEmail(),
                entity.getSendgridSenderName(),
                entity.getSendgridToFallback(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }
}
