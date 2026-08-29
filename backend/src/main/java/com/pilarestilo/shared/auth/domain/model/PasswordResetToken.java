package com.pilarestilo.shared.auth.domain.model;

import com.pilarestilo.shared.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A single password-reset link, stored as the hash of a token that only ever lived in the email.
 *
 * <p>Single use ({@code usedAt}) and short lived ({@code expiresAt}). Requesting a new link marks
 * every earlier unused row for that user as used, so only the most recent link works.
 */
public class PasswordResetToken {

    private UUID id;
    private UUID userId;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt;

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
        return token;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    /** Rehydration from the stored row; the parameter order follows the schema. */
    public static PasswordResetToken reconstruct(UUID id,
                                                 UUID userId,
                                                 String tokenHash,
                                                 Instant expiresAt,
                                                 Instant usedAt,
                                                 Instant createdAt) {
        PasswordResetToken token = new PasswordResetToken();
        token.id = id;
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.usedAt = usedAt;
        token.createdAt = createdAt;
        return token;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
