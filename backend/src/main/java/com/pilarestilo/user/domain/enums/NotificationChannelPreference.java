package com.pilarestilo.user.domain.enums;

public enum NotificationChannelPreference {
    AUTO,
    WHATSAPP,
    EMAIL,
    BOTH;

    public static NotificationChannelPreference fromRaw(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return AUTO;
        }
        return NotificationChannelPreference.valueOf(rawValue.trim().toUpperCase());
    }
}
