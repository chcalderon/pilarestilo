package com.pilarestilo.shared.auth.infrastructure.persistence.repositories;

import com.pilarestilo.shared.auth.infrastructure.persistence.entities.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {

    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    @Query("SELECT t FROM PasswordResetTokenEntity t WHERE t.userId = :userId AND t.usedAt IS NULL "
            + "AND t.expiresAt > :now ORDER BY t.createdAt DESC")
    List<PasswordResetTokenEntity> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PasswordResetTokenEntity t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
    void invalidateUnusedForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE PasswordResetTokenEntity t SET t.attemptCount = t.attemptCount + 1 WHERE t.id = :id")
    void incrementAttemptCount(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
