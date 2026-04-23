package com.pilarestilo.systemsettings.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.enums.MediaStorageProvider;
import com.pilarestilo.systemsettings.domain.enums.NotificationProvider;

import java.time.Instant;

public class SystemSettings {

    private short id;
    private String whatsappNumber;
    private String instagramUrl;
    private String facebookUrl;
    private MediaStorageProvider mediaStorageProvider;
    private String mediaS3Endpoint;
    private String mediaS3Region;
    private String mediaS3Bucket;
    private String mediaS3AccessKeyId;
    private String mediaS3SecretKeyEncrypted;
    private boolean mediaS3PathStyleEnabled;
    private String mediaS3PublicBaseUrl;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpFromEmail;
    private String smtpPasswordEncrypted;
    private boolean smtpAuthEnabled;
    private boolean smtpStarttlsEnabled;
    private NotificationProvider notificationProvider;
    private String whatsappSimulatedTo;
    private String whatsappSimulatedSender;
    private String whatsappTwilioApiBaseUrl;
    private String whatsappTwilioAccountSid;
    private String whatsappTwilioAuthTokenEncrypted;
    private String whatsappTwilioFrom;
    private String whatsappTwilioToFallback;
    private String whatsappTwilioSenderAlias;
    private String sendgridApiBaseUrl;
    private String sendgridApiKeyEncrypted;
    private String sendgridFromEmail;
    private String sendgridSenderName;
    private String sendgridToFallback;
    private Instant updatedAt;
    private String updatedBy;

    private SystemSettings() {}

    public static SystemSettings createDefault() {
        SystemSettings settings = new SystemSettings();
        settings.id = 1;
        settings.whatsappNumber = "+56900000000";
        settings.instagramUrl = "https://instagram.com/pilarestilo";
        settings.facebookUrl = "https://facebook.com/pilarestilo";
        settings.mediaStorageProvider = MediaStorageProvider.LOCAL;
        settings.mediaS3PathStyleEnabled = false;
        settings.smtpAuthEnabled = true;
        settings.smtpStarttlsEnabled = true;
        settings.notificationProvider = NotificationProvider.LOG;
        settings.whatsappSimulatedTo = "+56900000000";
        settings.whatsappSimulatedSender = "Pilar Estilo";
        settings.whatsappTwilioApiBaseUrl = "https://api.twilio.com";
        settings.whatsappTwilioToFallback = "+56900000000";
        settings.whatsappTwilioSenderAlias = "Pilar Estilo";
        settings.sendgridApiBaseUrl = "https://api.sendgrid.com";
        settings.sendgridSenderName = "Pilar Estilo";
        settings.updatedAt = Instant.now();
        settings.updatedBy = "system-default";
        return settings;
    }

    public static SystemSettings reconstruct(
            short id,
            String whatsappNumber,
            String instagramUrl,
            String facebookUrl,
            String mediaStorageProvider,
            String mediaS3Endpoint,
            String mediaS3Region,
            String mediaS3Bucket,
            String mediaS3AccessKeyId,
            String mediaS3SecretKeyEncrypted,
            boolean mediaS3PathStyleEnabled,
            String mediaS3PublicBaseUrl,
            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            String smtpFromEmail,
            String smtpPasswordEncrypted,
            boolean smtpAuthEnabled,
            boolean smtpStarttlsEnabled,
            String notificationProvider,
            String whatsappSimulatedTo,
            String whatsappSimulatedSender,
            String whatsappTwilioApiBaseUrl,
            String whatsappTwilioAccountSid,
            String whatsappTwilioAuthTokenEncrypted,
            String whatsappTwilioFrom,
            String whatsappTwilioToFallback,
            String whatsappTwilioSenderAlias,
            String sendgridApiBaseUrl,
            String sendgridApiKeyEncrypted,
            String sendgridFromEmail,
            String sendgridSenderName,
            String sendgridToFallback,
            Instant updatedAt,
            String updatedBy
    ) {
        if (id != 1) {
            throw new DomainException("System settings id must be 1");
        }
        SystemSettings settings = createDefault();
        settings.whatsappNumber = normalizeRequired(whatsappNumber, "WhatsApp number");
        settings.instagramUrl = normalizeNullable(instagramUrl);
        settings.facebookUrl = normalizeNullable(facebookUrl);
        settings.mediaStorageProvider = normalizeMediaStorageProvider(mediaStorageProvider);
        settings.mediaS3Endpoint = normalizeNullable(mediaS3Endpoint);
        settings.mediaS3Region = normalizeNullable(mediaS3Region);
        settings.mediaS3Bucket = normalizeNullable(mediaS3Bucket);
        settings.mediaS3AccessKeyId = normalizeNullable(mediaS3AccessKeyId);
        settings.mediaS3SecretKeyEncrypted = normalizeNullable(mediaS3SecretKeyEncrypted);
        settings.mediaS3PathStyleEnabled = mediaS3PathStyleEnabled;
        settings.mediaS3PublicBaseUrl = normalizeNullable(mediaS3PublicBaseUrl);
        settings.smtpHost = normalizeNullable(smtpHost);
        settings.smtpPort = smtpPort;
        settings.smtpUsername = normalizeNullable(smtpUsername);
        settings.smtpFromEmail = normalizeNullable(smtpFromEmail);
        settings.smtpPasswordEncrypted = normalizeNullable(smtpPasswordEncrypted);
        settings.smtpAuthEnabled = smtpAuthEnabled;
        settings.smtpStarttlsEnabled = smtpStarttlsEnabled;
        settings.notificationProvider = normalizeProvider(notificationProvider);
        settings.whatsappSimulatedTo = normalizeNullable(whatsappSimulatedTo);
        settings.whatsappSimulatedSender = normalizeNullable(whatsappSimulatedSender);
        settings.whatsappTwilioApiBaseUrl = normalizeNullable(whatsappTwilioApiBaseUrl);
        settings.whatsappTwilioAccountSid = normalizeNullable(whatsappTwilioAccountSid);
        settings.whatsappTwilioAuthTokenEncrypted = normalizeNullable(whatsappTwilioAuthTokenEncrypted);
        settings.whatsappTwilioFrom = normalizeNullable(whatsappTwilioFrom);
        settings.whatsappTwilioToFallback = normalizeNullable(whatsappTwilioToFallback);
        settings.whatsappTwilioSenderAlias = normalizeNullable(whatsappTwilioSenderAlias);
        settings.sendgridApiBaseUrl = normalizeNullable(sendgridApiBaseUrl);
        settings.sendgridApiKeyEncrypted = normalizeNullable(sendgridApiKeyEncrypted);
        settings.sendgridFromEmail = normalizeNullable(sendgridFromEmail);
        settings.sendgridSenderName = normalizeNullable(sendgridSenderName);
        settings.sendgridToFallback = normalizeNullable(sendgridToFallback);
        settings.validateMediaConfiguration();
        settings.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        settings.updatedBy = normalizeNullable(updatedBy);
        return settings;
    }

    public void update(
            String whatsappNumber,
            String instagramUrl,
            String facebookUrl,
            String mediaStorageProvider,
            String mediaS3Endpoint,
            String mediaS3Region,
            String mediaS3Bucket,
            String mediaS3AccessKeyId,
            String mediaS3SecretKeyEncrypted,
            boolean mediaS3PathStyleEnabled,
            String mediaS3PublicBaseUrl,
            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            String smtpFromEmail,
            String smtpPasswordEncrypted,
            boolean smtpAuthEnabled,
            boolean smtpStarttlsEnabled,
            String notificationProvider,
            String whatsappSimulatedTo,
            String whatsappSimulatedSender,
            String whatsappTwilioApiBaseUrl,
            String whatsappTwilioAccountSid,
            String whatsappTwilioAuthTokenEncrypted,
            String whatsappTwilioFrom,
            String whatsappTwilioToFallback,
            String whatsappTwilioSenderAlias,
            String sendgridApiBaseUrl,
            String sendgridApiKeyEncrypted,
            String sendgridFromEmail,
            String sendgridSenderName,
            String sendgridToFallback,
            String updatedBy
    ) {
        this.whatsappNumber = normalizeRequired(whatsappNumber, "WhatsApp number");
        this.instagramUrl = normalizeNullable(instagramUrl);
        this.facebookUrl = normalizeNullable(facebookUrl);
        this.mediaStorageProvider = normalizeMediaStorageProvider(mediaStorageProvider);
        this.mediaS3Endpoint = normalizeNullable(mediaS3Endpoint);
        this.mediaS3Region = normalizeNullable(mediaS3Region);
        this.mediaS3Bucket = normalizeNullable(mediaS3Bucket);
        this.mediaS3AccessKeyId = normalizeNullable(mediaS3AccessKeyId);
        this.mediaS3SecretKeyEncrypted = normalizeNullable(mediaS3SecretKeyEncrypted);
        this.mediaS3PathStyleEnabled = mediaS3PathStyleEnabled;
        this.mediaS3PublicBaseUrl = normalizeNullable(mediaS3PublicBaseUrl);
        this.smtpHost = normalizeNullable(smtpHost);
        this.smtpPort = smtpPort;
        this.smtpUsername = normalizeNullable(smtpUsername);
        this.smtpFromEmail = normalizeNullable(smtpFromEmail);
        this.smtpPasswordEncrypted = normalizeNullable(smtpPasswordEncrypted);
        this.smtpAuthEnabled = smtpAuthEnabled;
        this.smtpStarttlsEnabled = smtpStarttlsEnabled;
        this.notificationProvider = normalizeProvider(notificationProvider);
        this.whatsappSimulatedTo = normalizeNullable(whatsappSimulatedTo);
        this.whatsappSimulatedSender = normalizeNullable(whatsappSimulatedSender);
        this.whatsappTwilioApiBaseUrl = normalizeNullable(whatsappTwilioApiBaseUrl);
        this.whatsappTwilioAccountSid = normalizeNullable(whatsappTwilioAccountSid);
        this.whatsappTwilioAuthTokenEncrypted = normalizeNullable(whatsappTwilioAuthTokenEncrypted);
        this.whatsappTwilioFrom = normalizeNullable(whatsappTwilioFrom);
        this.whatsappTwilioToFallback = normalizeNullable(whatsappTwilioToFallback);
        this.whatsappTwilioSenderAlias = normalizeNullable(whatsappTwilioSenderAlias);
        this.sendgridApiBaseUrl = normalizeNullable(sendgridApiBaseUrl);
        this.sendgridApiKeyEncrypted = normalizeNullable(sendgridApiKeyEncrypted);
        this.sendgridFromEmail = normalizeNullable(sendgridFromEmail);
        this.sendgridSenderName = normalizeNullable(sendgridSenderName);
        this.sendgridToFallback = normalizeNullable(sendgridToFallback);
        validateMediaConfiguration();
        this.updatedAt = Instant.now();
        this.updatedBy = normalizeNullable(updatedBy);
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new DomainException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static NotificationProvider normalizeProvider(String rawProvider) {
        try {
            return NotificationProvider.fromRaw(rawProvider);
        } catch (IllegalArgumentException ex) {
            throw new DomainException("Unsupported notification provider: " + rawProvider);
        }
    }

    private static MediaStorageProvider normalizeMediaStorageProvider(String rawProvider) {
        try {
            return MediaStorageProvider.fromRaw(rawProvider);
        } catch (IllegalArgumentException ex) {
            throw new DomainException("Unsupported media storage provider: " + rawProvider);
        }
    }

    private void validateMediaConfiguration() {
        if (mediaStorageProvider == MediaStorageProvider.S3_COMPATIBLE
                && (mediaS3Bucket == null || mediaS3Bucket.isBlank())) {
            throw new DomainException("S3 bucket cannot be blank when S3-compatible storage is enabled");
        }
    }

    public short getId() { return id; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getFacebookUrl() { return facebookUrl; }
    public MediaStorageProvider getMediaStorageProvider() { return mediaStorageProvider; }
    public String getMediaS3Endpoint() { return mediaS3Endpoint; }
    public String getMediaS3Region() { return mediaS3Region; }
    public String getMediaS3Bucket() { return mediaS3Bucket; }
    public String getMediaS3AccessKeyId() { return mediaS3AccessKeyId; }
    public String getMediaS3SecretKeyEncrypted() { return mediaS3SecretKeyEncrypted; }
    public boolean isMediaS3PathStyleEnabled() { return mediaS3PathStyleEnabled; }
    public String getMediaS3PublicBaseUrl() { return mediaS3PublicBaseUrl; }
    public String getSmtpHost() { return smtpHost; }
    public Integer getSmtpPort() { return smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public String getSmtpFromEmail() { return smtpFromEmail; }
    public String getSmtpPasswordEncrypted() { return smtpPasswordEncrypted; }
    public boolean isSmtpAuthEnabled() { return smtpAuthEnabled; }
    public boolean isSmtpStarttlsEnabled() { return smtpStarttlsEnabled; }
    public NotificationProvider getNotificationProvider() { return notificationProvider; }
    public String getWhatsappSimulatedTo() { return whatsappSimulatedTo; }
    public String getWhatsappSimulatedSender() { return whatsappSimulatedSender; }
    public String getWhatsappTwilioApiBaseUrl() { return whatsappTwilioApiBaseUrl; }
    public String getWhatsappTwilioAccountSid() { return whatsappTwilioAccountSid; }
    public String getWhatsappTwilioAuthTokenEncrypted() { return whatsappTwilioAuthTokenEncrypted; }
    public String getWhatsappTwilioFrom() { return whatsappTwilioFrom; }
    public String getWhatsappTwilioToFallback() { return whatsappTwilioToFallback; }
    public String getWhatsappTwilioSenderAlias() { return whatsappTwilioSenderAlias; }
    public String getSendgridApiBaseUrl() { return sendgridApiBaseUrl; }
    public String getSendgridApiKeyEncrypted() { return sendgridApiKeyEncrypted; }
    public String getSendgridFromEmail() { return sendgridFromEmail; }
    public String getSendgridSenderName() { return sendgridSenderName; }
    public String getSendgridToFallback() { return sendgridToFallback; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
