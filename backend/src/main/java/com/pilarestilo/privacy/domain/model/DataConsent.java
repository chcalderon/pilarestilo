package com.pilarestilo.privacy.domain.model;

import com.pilarestilo.privacy.domain.enums.ConsentType;
import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

/**
 * One thing a customer agreed to, and the version of the text she agreed to.
 *
 * <p>The version is the whole point. "She accepted the terms" is worth nothing a year later, when
 * the terms have been rewritten twice; "she accepted version 2026-08 on the third, from this
 * address" is what the Ley 21.719 asks the shop to be able to show.
 *
 * <p>Nothing here is editable. Withdrawing marks the row revoked and leaves it standing, because
 * the shop has to be able to say both that consent was given and that it was taken back.
 */
public class DataConsent {

    private UUID id;
    private UUID userId;
    private ConsentType type;
    private String policyVersion;
    private Instant acceptedAt;
    private Instant revokedAt;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;

    private DataConsent() {}

    public static DataConsent accept(UUID userId,
                                     ConsentType type,
                                     String policyVersion,
                                     String ipAddress,
                                     String userAgent) {
        if (userId == null) {
            throw new DomainException("A consent belongs to somebody");
        }
        if (type == null) {
            throw new DomainException("A consent has to say what was agreed to");
        }
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new DomainException("A consent without the version of the text proves nothing");
        }

        DataConsent consent = new DataConsent();
        consent.id = UUID.randomUUID();
        consent.userId = userId;
        consent.type = type;
        consent.policyVersion = policyVersion.trim();
        consent.acceptedAt = Instant.now();
        consent.ipAddress = trimToNull(ipAddress);
        consent.userAgent = truncate(trimToNull(userAgent));
        consent.createdAt = consent.acceptedAt;
        return consent;
    }

    /** The customer took it back. The row stays; only the withdrawal is recorded. */
    public void revoke() {
        if (revokedAt != null) {
            return;
        }
        this.revokedAt = Instant.now();
    }

    public boolean isLive() {
        return revokedAt == null;
    }

    /**
     * Rehydration from the stored row, so the parameter count follows the schema rather than a
     * caller's convenience. Every other module reconstructs the same way — {@code Order} takes far
     * more — and a builder for the two smallest would be a second pattern to keep straight.
     */
    @SuppressWarnings("java:S107")
    public static DataConsent reconstruct(UUID id,
                                          UUID userId,
                                          ConsentType type,
                                          String policyVersion,
                                          Instant acceptedAt,
                                          Instant revokedAt,
                                          String ipAddress,
                                          String userAgent,
                                          Instant createdAt) {
        DataConsent consent = new DataConsent();
        consent.id = id;
        consent.userId = userId;
        consent.type = type;
        consent.policyVersion = policyVersion;
        consent.acceptedAt = acceptedAt;
        consent.revokedAt = revokedAt;
        consent.ipAddress = ipAddress;
        consent.userAgent = userAgent;
        consent.createdAt = createdAt;
        return consent;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The column holds 300; a browser sending more is not a reason to lose the whole consent. */
    private static String truncate(String value) {
        if (value == null || value.length() <= 300) {
            return value;
        }
        return value.substring(0, 300);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public ConsentType getType() { return type; }
    public String getPolicyVersion() { return policyVersion; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}
