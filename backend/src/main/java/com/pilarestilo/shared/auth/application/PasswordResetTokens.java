package com.pilarestilo.shared.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * The reset code and its stored hash. The code goes in the email and is never persisted; only
 * {@link #hash(String)} of it reaches the database, so a leaked table cannot be turned back into
 * a working code.
 */
public final class PasswordResetTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordResetTokens() {}

    /**
     * A 6-digit numeric code, zero-padded, e.g. {@code "418302"}. Low entropy on purpose — paired
     * with a 30-minute TTL, single use, and a {@code PasswordResetToken.MAX_ATTEMPTS} lock.
     */
    public static String newCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public static String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
