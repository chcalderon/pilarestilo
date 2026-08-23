package com.pilarestilo.notification.infrastructure.adapters;

/** Shared between the SMTP and SendGrid senders so the two never drift apart on what counts as an email. */
final class EmailFormat {

    private EmailFormat() {}

    // The middle group has to stay backtracking, not possessive: a domain with more than one dot
    // (mail.example.com) needs it to give back characters until the trailing \. finds its match.
    // Backtracking here is bounded by the one literal split point, not nested or unbounded, and
    // the input is a configured sender address or a contact on file -- not adversarial.
    @SuppressWarnings("java:S8786")
    static boolean looksLikeEmail(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }
}
