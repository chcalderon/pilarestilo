package com.pilarestilo.order.domain.model;

import com.pilarestilo.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The short code a customer writes in the message of a bank transfer, and the one an admin uses to
 * find the order from a bank statement. Format: {@code PE-} plus ten uppercase hex characters,
 * e.g. {@code PE-3F9A2C71B4}.
 *
 * <p>Hex was chosen for its alphabet. {@code 0-9A-F} contains none of the pairs that get mistyped
 * or misheard — no O/0, no I/1/l, no S/5 — so no custom alphabet has to be defined and kept in
 * sync between the application and SQL.
 *
 * <p>Derived from the order id rather than drawn from a sequence, so {@code order-service} can
 * compute the same value from its own database when {@code APP_ORDER_REMOTE_WRITE_ENABLED} routes
 * creation there. The V67 backfill relies on this too: {@code UPPER(SUBSTR(MD5(id::text),1,10))} in
 * Postgres produces byte-identical output, which OrderReferenceSqlParityIT asserts.
 *
 * <p>MD5 is an identifier derivation here, not a security primitive. Nothing about the reference
 * is secret — it is printed in emails — and it is never used for authorisation.
 */
public final class OrderReference {

    private static final String PREFIX = "PE-";
    private static final int HEX_LENGTH = 10;

    private OrderReference() {}

    public static String forOrderId(UUID orderId) {
        return forOrderId(orderId, 0);
    }

    /**
     * @param salt attempt number. 0 produces the canonical reference; higher values are the
     *             collision escape hatch, matching the repair loop in V67 so a reference minted
     *             here and one repaired by the migration can never disagree.
     */
    public static String forOrderId(UUID orderId, int salt) {
        if (orderId == null) {
            throw new DomainException("Order id cannot be null");
        }
        String input = salt == 0 ? orderId.toString() : orderId + "#" + salt;
        return PREFIX + HexFormat.of().withUpperCase()
                .formatHex(md5(input))
                .substring(0, HEX_LENGTH);
    }

    /** Accepts what a human might type: lower case, stray spaces, a missing prefix. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (cleaned.startsWith("PE")) {
            cleaned = cleaned.substring(2);
        }
        return cleaned.isEmpty() ? null : PREFIX + cleaned;
    }

    private static byte[] md5(String input) {
        try {
            return MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // MD5 is required of every JVM implementation; unreachable in practice.
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
