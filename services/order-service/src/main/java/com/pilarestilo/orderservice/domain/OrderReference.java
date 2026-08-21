package com.pilarestilo.orderservice.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Deliberate duplicate of {@code com.pilarestilo.order.domain.model.OrderReference} in the monolith.
 *
 * <p>order-service writes into the same {@code orders} table, and {@code public_reference} is NOT
 * NULL, so it has to mint the value too. The algorithm is a pure function of the order id
 * precisely so both services — and V67's SQL backfill — produce identical output without sharing
 * a sequence or a module.
 *
 * <p>If this changes, change it in all three places. OrderReferenceSqlParityIT in the monolith
 * pins the expected output.
 */
public final class OrderReference {

    private static final String PREFIX = "PE-";
    private static final int HEX_LENGTH = 10;

    private OrderReference() {}

    public static String forOrderId(UUID orderId) {
        return PREFIX + HexFormat.of().withUpperCase()
                .formatHex(md5(orderId.toString()))
                .substring(0, HEX_LENGTH);
    }

    /*
     * MD5 on purpose, and it stays. Sonar reads this as a weak hash and it is right about the
     * algorithm and wrong about the use: the reference is an identifier, printed in emails, never
     * secret and never checked for authorisation. It must also stay byte-for-byte identical to the
     * monolith's OrderReference and to V67's SQL backfill — this service and the monolith both
     * write the orders table, so a change on one side alone would let a customer quote a code that
     * matches no order.
     */
    @SuppressWarnings("java:S4790")
    private static byte[] md5(String input) {
        try {
            return MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
