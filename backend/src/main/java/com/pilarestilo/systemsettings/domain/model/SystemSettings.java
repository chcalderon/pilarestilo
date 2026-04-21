package com.pilarestilo.systemsettings.domain.model;

import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;

public class SystemSettings {

    private short id;
    private String whatsappNumber;
    private String instagramUrl;
    private String facebookUrl;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpFromEmail;
    private String smtpPasswordEncrypted;
    private boolean smtpAuthEnabled;
    private boolean smtpStarttlsEnabled;
    private Instant updatedAt;
    private String updatedBy;

    private SystemSettings() {}

    public static SystemSettings createDefault() {
        SystemSettings settings = new SystemSettings();
        settings.id = 1;
        settings.whatsappNumber = "+56900000000";
        settings.instagramUrl = "https://instagram.com/pilarestilo";
        settings.facebookUrl = "https://facebook.com/pilarestilo";
        settings.smtpAuthEnabled = true;
        settings.smtpStarttlsEnabled = true;
        settings.updatedAt = Instant.now();
        settings.updatedBy = "system-default";
        return settings;
    }

    public static SystemSettings reconstruct(
            short id,
            String whatsappNumber,
            String instagramUrl,
            String facebookUrl,
            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            String smtpFromEmail,
            String smtpPasswordEncrypted,
            boolean smtpAuthEnabled,
            boolean smtpStarttlsEnabled,
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
        settings.smtpHost = normalizeNullable(smtpHost);
        settings.smtpPort = smtpPort;
        settings.smtpUsername = normalizeNullable(smtpUsername);
        settings.smtpFromEmail = normalizeNullable(smtpFromEmail);
        settings.smtpPasswordEncrypted = normalizeNullable(smtpPasswordEncrypted);
        settings.smtpAuthEnabled = smtpAuthEnabled;
        settings.smtpStarttlsEnabled = smtpStarttlsEnabled;
        settings.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        settings.updatedBy = normalizeNullable(updatedBy);
        return settings;
    }

    public void update(
            String whatsappNumber,
            String instagramUrl,
            String facebookUrl,
            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            String smtpFromEmail,
            String smtpPasswordEncrypted,
            boolean smtpAuthEnabled,
            boolean smtpStarttlsEnabled,
            String updatedBy
    ) {
        this.whatsappNumber = normalizeRequired(whatsappNumber, "WhatsApp number");
        this.instagramUrl = normalizeNullable(instagramUrl);
        this.facebookUrl = normalizeNullable(facebookUrl);
        this.smtpHost = normalizeNullable(smtpHost);
        this.smtpPort = smtpPort;
        this.smtpUsername = normalizeNullable(smtpUsername);
        this.smtpFromEmail = normalizeNullable(smtpFromEmail);
        this.smtpPasswordEncrypted = normalizeNullable(smtpPasswordEncrypted);
        this.smtpAuthEnabled = smtpAuthEnabled;
        this.smtpStarttlsEnabled = smtpStarttlsEnabled;
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

    public short getId() { return id; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getFacebookUrl() { return facebookUrl; }
    public String getSmtpHost() { return smtpHost; }
    public Integer getSmtpPort() { return smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public String getSmtpFromEmail() { return smtpFromEmail; }
    public String getSmtpPasswordEncrypted() { return smtpPasswordEncrypted; }
    public boolean isSmtpAuthEnabled() { return smtpAuthEnabled; }
    public boolean isSmtpStarttlsEnabled() { return smtpStarttlsEnabled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
