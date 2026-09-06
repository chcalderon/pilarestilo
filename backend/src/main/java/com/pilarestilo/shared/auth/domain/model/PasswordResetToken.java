package com.pilarestilo.shared.auth.domain.model;

import com.pilarestilo.shared.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A single password-reset secret, stored as the hash of a 6-digit code that only ever lived in
 * the email.
 *
 * <p>Single use ({@code usedAt}), short lived ({@code expiresAt}), and locked after
 * {@link #MAX_ATTEMPTS} wrong guesses ({@code attemptCount}) — the code is low-entropy, so the
 * lock is what stops a brute force in the TTL window. Requesting a new code marks every earlier
 * unused row for that user as used, so only the most recent code works.
 */
public class PasswordResetToken {

    /** Wrong-guess budget before the row is dead, however much of the TTL is left. */
    public static final int MAX_ATTEMPTS = 5;

    private UUID id;
    private UUID userId;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt;
    private int attemptCount;

    private PasswordResetToken() {}

    public static PasswordResetToken issue(UUID userId, String tokenHash, Duration ttl) {
        if (userId == null) {
            throw new DomainException("A password reset token belongs to a user");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new DomainException("A password reset token needs a hash");
        }
        PasswordResetToken token = new PasswordResetToken();
        token.id = UUID.randomUUID();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.createdAt = Instant.now();
        token.expiresAt = token.createdAt.plus(ttl);
        token.usedAt = null;
        token.attemptCount = 0;
        return token;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt) && attemptCount < MAX_ATTEMPTS;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    /** A wrong code was submitted against this row. */
    public void recordFailedAttempt() {
        this.attemptCount++;
    }

    /** Rehydration from the stored row; the parameter order follows the schema. */
    public static PasswordResetToken reconstruct(UUID id,
                                                 UUID userId,
                                                 String tokenHash,
                                                 Instant expiresAt,
                                                 Instant usedAt,
                                                 Instant createdAt,
                                                 int attemptCount) {
        PasswordResetToken token = new PasswordResetToken();
        token.id = id;
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.usedAt = usedAt;
        token.createdAt = createdAt;
        token.attemptCount = attemptCount;
        return token;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAttemptCount() { return attemptCount; }
}
