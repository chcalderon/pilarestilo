package com.pilarestilo.shared.auth.domain;

import com.pilarestilo.shared.auth.domain.model.PasswordResetToken;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetTokenTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void a_freshly_issued_token_is_usable() {
        PasswordResetToken token = PasswordResetToken.issue(userId, "abc123", Duration.ofMinutes(30));
        assertNotNull(token.getId());
        assertEquals(userId, token.getUserId());
        assertTrue(token.isUsable(Instant.now()));
    }

    @Test
    void marking_a_token_used_makes_it_unusable() {
        PasswordResetToken token = PasswordResetToken.issue(userId, "abc123", Duration.ofMinutes(30));
        Instant now = Instant.now();
        token.markUsed(now);
        assertFalse(token.isUsable(now));
        assertEquals(now, token.getUsedAt());
    }

    @Test
    void an_expired_token_is_not_usable() {
        PasswordResetToken token = PasswordResetToken.issue(userId, "abc123", Duration.ofMinutes(-1));
        assertFalse(token.isUsable(Instant.now()));
    }

    @Test
    void reconstruct_carries_a_stored_row_back_intact() {
        Instant created = Instant.now().minusSeconds(60);
        Instant expires = created.plusSeconds(1800);
        PasswordResetToken token = PasswordResetToken.reconstruct(
                UUID.randomUUID(), userId, "hash", expires, null, created);
        assertTrue(token.isUsable(created.plusSeconds(10)));
        assertFalse(token.isUsable(expires.plusSeconds(1)));
    }
}
