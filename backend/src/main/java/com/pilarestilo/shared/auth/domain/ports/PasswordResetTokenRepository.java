package com.pilarestilo.shared.auth.domain.ports;

import com.pilarestilo.shared.auth.domain.model.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * The newest unused, unexpired token for the user, if any. The attempt-count lock is judged by
     * the caller (a locked row still comes back, and yields the one generic failure).
     */
    Optional<PasswordResetToken> findActiveByUserId(UUID userId);

    /**
     * Bumps {@code attempt_count} for one row, in its own transaction — a wrong code must stick
     * even though the reset use case rolls back the request that carried it.
     */
    void recordFailedAttempt(UUID tokenId);

    /** Marks every unused token for the user as used, so a newly issued link is the only live one. */
    void invalidateUnusedForUser(UUID userId);

    /** Removes rows that expired before the cutoff. Returns how many were deleted. */
    int deleteExpiredBefore(Instant cutoff);
}
