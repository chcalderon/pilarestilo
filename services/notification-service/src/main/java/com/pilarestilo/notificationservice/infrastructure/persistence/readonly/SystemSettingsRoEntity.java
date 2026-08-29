package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Read-only view of {@code system_settings}: the messaging-config columns this service reads, plus
 * the two bank-transfer-deadline fields. The single row has {@code id = 1}. Same approach as
 * order-service, which maps its own eight-column slice of this table.
 */
@Entity
@Immutable
@Table(name = "system_settings")
public class SystemSettingsRoEntity {

    @Id
    private Short id;

    @Column(name = "notification_providers")
    private String notificationProviders;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "smtp_from_email")
    private String smtpFromEmail;

    @Column(name = "smtp_password_encrypted")
    private String smtpPasswordEncrypted;

    @Column(name = "smtp_auth_enabled")
    private boolean smtpAuthEnabled;

    @Column(name = "smtp_starttls_enabled")
    private boolean smtpStarttlsEnabled;

    @Column(name = "sendgrid_api_base_url")
    private String sendgridApiBaseUrl;

    @Column(name = "sendgrid_api_key_encrypted")
    private String sendgridApiKeyEncrypted;

    @Column(name = "sendgrid_from_email")
    private String sendgridFromEmail;

    @Column(name = "sendgrid_sender_name")
    private String sendgridSenderName;

    @Column(name = "sendgrid_to_fallback")
    private String sendgridToFallback;

    @Column(name = "whatsapp_twilio_api_base_url")
    private String whatsappTwilioApiBaseUrl;

    @Column(name = "whatsapp_twilio_account_sid")
    private String whatsappTwilioAccountSid;

    @Column(name = "whatsapp_twilio_auth_token_encrypted")
    private String whatsappTwilioAuthTokenEncrypted;

    @Column(name = "whatsapp_twilio_from")
    private String whatsappTwilioFrom;

    @Column(name = "whatsapp_twilio_to_fallback")
    private String whatsappTwilioToFallback;

    @Column(name = "whatsapp_twilio_sender_alias")
    private String whatsappTwilioSenderAlias;

    @Column(name = "whatsapp_simulated_to")
    private String whatsappSimulatedTo;

    @Column(name = "whatsapp_simulated_sender")
    private String whatsappSimulatedSender;

    @Column(name = "n8n_webhook_url")
    private String n8nWebhookUrl;

    @Column(name = "n8n_api_key_encrypted")
    private String n8nApiKeyEncrypted;

    @Column(name = "n8n_token_header_name")
    private String n8nTokenHeaderName;

    @Column(name = "bank_transfer_auto_cancel_enabled")
    private boolean bankTransferAutoCancelEnabled;

    @Column(name = "bank_transfer_auto_cancel_timeout_minutes")
    private int bankTransferAutoCancelTimeoutMinutes;

    public Short getId() { return id; }
    public String getNotificationProviders() { return notificationProviders; }
    public String getUpdatedBy() { return updatedBy; }
    public String getSmtpHost() { return smtpHost; }
    public Integer getSmtpPort() { return smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public String getSmtpFromEmail() { return smtpFromEmail; }
    public String getSmtpPasswordEncrypted() { return smtpPasswordEncrypted; }
    public boolean isSmtpAuthEnabled() { return smtpAuthEnabled; }
    public boolean isSmtpStarttlsEnabled() { return smtpStarttlsEnabled; }
    public String getSendgridApiBaseUrl() { return sendgridApiBaseUrl; }
    public String getSendgridApiKeyEncrypted() { return sendgridApiKeyEncrypted; }
    public String getSendgridFromEmail() { return sendgridFromEmail; }
    public String getSendgridSenderName() { return sendgridSenderName; }
    public String getSendgridToFallback() { return sendgridToFallback; }
    public String getWhatsappTwilioApiBaseUrl() { return whatsappTwilioApiBaseUrl; }
    public String getWhatsappTwilioAccountSid() { return whatsappTwilioAccountSid; }
    public String getWhatsappTwilioAuthTokenEncrypted() { return whatsappTwilioAuthTokenEncrypted; }
    public String getWhatsappTwilioFrom() { return whatsappTwilioFrom; }
    public String getWhatsappTwilioToFallback() { return whatsappTwilioToFallback; }
    public String getWhatsappTwilioSenderAlias() { return whatsappTwilioSenderAlias; }
    public String getWhatsappSimulatedTo() { return whatsappSimulatedTo; }
    public String getWhatsappSimulatedSender() { return whatsappSimulatedSender; }
    public String getN8nWebhookUrl() { return n8nWebhookUrl; }
    public String getN8nApiKeyEncrypted() { return n8nApiKeyEncrypted; }
    public String getN8nTokenHeaderName() { return n8nTokenHeaderName; }
    public boolean isBankTransferAutoCancelEnabled() { return bankTransferAutoCancelEnabled; }
    public int getBankTransferAutoCancelTimeoutMinutes() { return bankTransferAutoCancelTimeoutMinutes; }
}
