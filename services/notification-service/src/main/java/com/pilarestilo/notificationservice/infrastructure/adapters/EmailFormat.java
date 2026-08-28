package com.pilarestilo.notificationservice.infrastructure.adapters;

/** Shared between the SMTP and SendGrid senders so the two never drift apart on what counts as an email. */
final class EmailFormat {

    private EmailFormat() {}

    @SuppressWarnings("java:S8786")
    static boolean looksLikeEmail(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}
