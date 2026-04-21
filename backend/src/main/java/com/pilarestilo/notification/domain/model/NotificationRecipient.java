package com.pilarestilo.notification.domain.model;

public record NotificationRecipient(
        String phone,
        String email
) {
    public static NotificationRecipient of(String phone, String email) {
        return new NotificationRecipient(normalize(phone), normalize(email));
    }

    public static NotificationRecipient unknown() {
        return new NotificationRecipient(null, null);
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

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
