package com.pilarestilo.shared.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The raw reset token and its stored hash. The raw value goes in the email link and is never
 * persisted; only {@link #hash(String)} of it reaches the database, so a leaked table cannot be
 * turned back into a working link.
 */
public final class PasswordResetTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordResetTokens() {}

    /** 256 bits of entropy, URL-safe, no padding. */
    public static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
