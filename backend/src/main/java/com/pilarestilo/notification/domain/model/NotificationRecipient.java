package com.pilarestilo.notification.domain.model;

public record NotificationRecipient(
        String phone,
        String email,
        NotificationChannelPreference preference
) {
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

    public static NotificationRecipient of(String phone, String email) {
        return new NotificationRecipient(normalize(phone), normalize(email), NotificationChannelPreference.AUTO);
    }

    public static NotificationRecipient of(String phone, String email, String preference) {
        return new NotificationRecipient(
                normalize(phone),
                normalize(email),
                normalizePreference(preference)
        );
    }

    public static NotificationRecipient unknown() {
        return new NotificationRecipient(null, null, NotificationChannelPreference.AUTO);
    }

    public String preferredPhoneThenEmail() {
        if (phone != null && !phone.isBlank()) {
            return phone;
        }
        if (email != null && !email.isBlank()) {
            return email;
        }
        return "unknown";
    }

    public String preferredEmailThenPhone() {
        if (email != null && !email.isBlank()) {
            return email;
        }
        if (phone != null && !phone.isBlank()) {
            return phone;
        }
        return "unknown";
    }

    public boolean allowsWhatsApp() {
        return preference == NotificationChannelPreference.AUTO
                || preference == NotificationChannelPreference.WHATSAPP
                || preference == NotificationChannelPreference.BOTH;
    }

    public boolean allowsEmail() {
        return preference == NotificationChannelPreference.AUTO
                || preference == NotificationChannelPreference.EMAIL
                || preference == NotificationChannelPreference.BOTH;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static NotificationChannelPreference normalizePreference(String rawPreference) {
        try {
            return NotificationChannelPreference.fromRaw(rawPreference);
        } catch (IllegalArgumentException _) {
            return NotificationChannelPreference.AUTO;
        }
    }
}
