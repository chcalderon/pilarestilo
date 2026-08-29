package com.pilarestilo.shared.auth.domain.ports;

import com.pilarestilo.shared.auth.domain.model.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Marks every unused token for the user as used, so a newly issued link is the only live one. */
    void invalidateUnusedForUser(UUID userId);

    /** Removes rows that expired before the cutoff. Returns how many were deleted. */
    int deleteExpiredBefore(Instant cutoff);
}
