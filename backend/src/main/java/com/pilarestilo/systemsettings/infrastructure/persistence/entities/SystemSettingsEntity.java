package com.pilarestilo.systemsettings.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "system_settings")
public class SystemSettingsEntity {

    @Id
    private Short id;

    @Column(name = "whatsapp_number", nullable = false, length = 40)
    private String whatsappNumber;

    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    @Column(name = "facebook_url", length = 500)
    private String facebookUrl;

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 255)
    private String smtpUsername;

    @Column(name = "smtp_from_email", length = 255)
    private String smtpFromEmail;

    @Column(name = "smtp_password_encrypted", columnDefinition = "TEXT")
    private String smtpPasswordEncrypted;

    @Column(name = "smtp_auth_enabled", nullable = false)
    private boolean smtpAuthEnabled;

    @Column(name = "smtp_starttls_enabled", nullable = false)
    private boolean smtpStarttlsEnabled;

    @Column(name = "notification_provider", nullable = false, length = 40)
    private String notificationProvider;

    @Column(name = "whatsapp_simulated_to", length = 40)
    private String whatsappSimulatedTo;

    @Column(name = "whatsapp_simulated_sender", length = 120)
    private String whatsappSimulatedSender;

    @Column(name = "whatsapp_twilio_api_base_url", length = 255)
    private String whatsappTwilioApiBaseUrl;

    @Column(name = "whatsapp_twilio_account_sid", length = 255)
    private String whatsappTwilioAccountSid;

    @Column(name = "whatsapp_twilio_auth_token_encrypted", columnDefinition = "TEXT")
    private String whatsappTwilioAuthTokenEncrypted;

    @Column(name = "whatsapp_twilio_from", length = 80)
    private String whatsappTwilioFrom;

    @Column(name = "whatsapp_twilio_to_fallback", length = 80)
    private String whatsappTwilioToFallback;

    @Column(name = "whatsapp_twilio_sender_alias", length = 120)
    private String whatsappTwilioSenderAlias;

    @Column(name = "sendgrid_api_base_url", length = 255)
    private String sendgridApiBaseUrl;

    @Column(name = "sendgrid_api_key_encrypted", columnDefinition = "TEXT")
    private String sendgridApiKeyEncrypted;

    @Column(name = "sendgrid_from_email", length = 255)
    private String sendgridFromEmail;

    @Column(name = "sendgrid_sender_name", length = 120)
    private String sendgridSenderName;

    @Column(name = "sendgrid_to_fallback", length = 255)
    private String sendgridToFallback;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }
    public String getInstagramUrl() { return instagramUrl; }
    public void setInstagramUrl(String instagramUrl) { this.instagramUrl = instagramUrl; }
    public String getFacebookUrl() { return facebookUrl; }
    public void setFacebookUrl(String facebookUrl) { this.facebookUrl = facebookUrl; }
    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }
    public Integer getSmtpPort() { return smtpPort; }
    public void setSmtpPort(Integer smtpPort) { this.smtpPort = smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String smtpUsername) { this.smtpUsername = smtpUsername; }
    public String getSmtpFromEmail() { return smtpFromEmail; }
    public void setSmtpFromEmail(String smtpFromEmail) { this.smtpFromEmail = smtpFromEmail; }
    public String getSmtpPasswordEncrypted() { return smtpPasswordEncrypted; }
    public void setSmtpPasswordEncrypted(String smtpPasswordEncrypted) { this.smtpPasswordEncrypted = smtpPasswordEncrypted; }
    public boolean isSmtpAuthEnabled() { return smtpAuthEnabled; }
    public void setSmtpAuthEnabled(boolean smtpAuthEnabled) { this.smtpAuthEnabled = smtpAuthEnabled; }
    public boolean isSmtpStarttlsEnabled() { return smtpStarttlsEnabled; }
    public void setSmtpStarttlsEnabled(boolean smtpStarttlsEnabled) { this.smtpStarttlsEnabled = smtpStarttlsEnabled; }
    public String getNotificationProvider() { return notificationProvider; }
    public void setNotificationProvider(String notificationProvider) { this.notificationProvider = notificationProvider; }
    public String getWhatsappSimulatedTo() { return whatsappSimulatedTo; }
    public void setWhatsappSimulatedTo(String whatsappSimulatedTo) { this.whatsappSimulatedTo = whatsappSimulatedTo; }
    public String getWhatsappSimulatedSender() { return whatsappSimulatedSender; }
    public void setWhatsappSimulatedSender(String whatsappSimulatedSender) { this.whatsappSimulatedSender = whatsappSimulatedSender; }
    public String getWhatsappTwilioApiBaseUrl() { return whatsappTwilioApiBaseUrl; }
    public void setWhatsappTwilioApiBaseUrl(String whatsappTwilioApiBaseUrl) { this.whatsappTwilioApiBaseUrl = whatsappTwilioApiBaseUrl; }
    public String getWhatsappTwilioAccountSid() { return whatsappTwilioAccountSid; }
    public void setWhatsappTwilioAccountSid(String whatsappTwilioAccountSid) { this.whatsappTwilioAccountSid = whatsappTwilioAccountSid; }
    public String getWhatsappTwilioAuthTokenEncrypted() { return whatsappTwilioAuthTokenEncrypted; }
    public void setWhatsappTwilioAuthTokenEncrypted(String whatsappTwilioAuthTokenEncrypted) {
        this.whatsappTwilioAuthTokenEncrypted = whatsappTwilioAuthTokenEncrypted;
    }
    public String getWhatsappTwilioFrom() { return whatsappTwilioFrom; }
    public void setWhatsappTwilioFrom(String whatsappTwilioFrom) { this.whatsappTwilioFrom = whatsappTwilioFrom; }
    public String getWhatsappTwilioToFallback() { return whatsappTwilioToFallback; }
    public void setWhatsappTwilioToFallback(String whatsappTwilioToFallback) { this.whatsappTwilioToFallback = whatsappTwilioToFallback; }
    public String getWhatsappTwilioSenderAlias() { return whatsappTwilioSenderAlias; }
    public void setWhatsappTwilioSenderAlias(String whatsappTwilioSenderAlias) { this.whatsappTwilioSenderAlias = whatsappTwilioSenderAlias; }
    public String getSendgridApiBaseUrl() { return sendgridApiBaseUrl; }
    public void setSendgridApiBaseUrl(String sendgridApiBaseUrl) { this.sendgridApiBaseUrl = sendgridApiBaseUrl; }
    public String getSendgridApiKeyEncrypted() { return sendgridApiKeyEncrypted; }
    public void setSendgridApiKeyEncrypted(String sendgridApiKeyEncrypted) { this.sendgridApiKeyEncrypted = sendgridApiKeyEncrypted; }
    public String getSendgridFromEmail() { return sendgridFromEmail; }
    public void setSendgridFromEmail(String sendgridFromEmail) { this.sendgridFromEmail = sendgridFromEmail; }
    public String getSendgridSenderName() { return sendgridSenderName; }
    public void setSendgridSenderName(String sendgridSenderName) { this.sendgridSenderName = sendgridSenderName; }
    public String getSendgridToFallback() { return sendgridToFallback; }
    public void setSendgridToFallback(String sendgridToFallback) { this.sendgridToFallback = sendgridToFallback; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
