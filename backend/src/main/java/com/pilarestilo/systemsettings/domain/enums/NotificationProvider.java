package com.pilarestilo.systemsettings.domain.enums;

public enum NotificationProvider {
    LOG,
    WHATSAPP_SIMULATED,
    WHATSAPP_TWILIO,
    EMAIL_SENDGRID,
    EMAIL_SMTP,
    N8N_WEBHOOK;

    public static NotificationProvider fromRaw(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return LOG;
        }
        return NotificationProvider.valueOf(rawValue.trim().toUpperCase());
    }
}
