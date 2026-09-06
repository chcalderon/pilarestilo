package com.pilarestilo.shared.auth.infrastructure.persistence.repositories;

import com.pilarestilo.shared.auth.domain.model.PasswordResetToken;
import com.pilarestilo.shared.auth.domain.ports.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the JPQL {@code @Modifying} queries and the schema mapping against a real Postgres --
 * neither the "invalidate the rest" update nor the expired-row delete can be trusted on inspection.
 */
@Testcontainers
@SpringBootTest
class PasswordResetTokenRepositoryAdapterIT {

    /** The admin row seeded by V2/V6 -- any real users.id works as the FK target. */
    private static final UUID SEEDED_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("pilarestilo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    PasswordResetTokenRepository repository;

    @Autowired
    PasswordResetTokenJpaRepository jpaRepository;

    @Test
    void saves_and_finds_a_token_by_its_hash() {
        String hash = "hash-" + UUID.randomUUID();
        repository.save(PasswordResetToken.issue(SEEDED_USER, hash, Duration.ofMinutes(30)));

        assertThat(repository.findByTokenHash(hash)).isPresent();
        assertThat(repository.findByTokenHash("missing")).isEmpty();
    }

    @Test
    void invalidating_marks_every_unused_row_for_the_user_used() {
        repository.save(PasswordResetToken.issue(SEEDED_USER, "h1-" + UUID.randomUUID(), Duration.ofMinutes(30)));
        repository.save(PasswordResetToken.issue(SEEDED_USER, "h2-" + UUID.randomUUID(), Duration.ofMinutes(30)));

        repository.invalidateUnusedForUser(SEEDED_USER);

        assertThat(jpaRepository.findAll())
                .filteredOn(t -> t.getUserId().equals(SEEDED_USER))
                .isNotEmpty()
                .allMatch(t -> t.getUsedAt() != null);
    }

    @Test
    void findActiveByUserId_returns_the_newest_unused_unexpired_row() {
        repository.save(PasswordResetToken.issue(SEEDED_USER, "old-" + UUID.randomUUID(), Duration.ofMinutes(30)));
        PasswordResetToken newest = repository.save(
                PasswordResetToken.issue(SEEDED_USER, "new-" + UUID.randomUUID(), Duration.ofMinutes(30)));

        var found = repository.findActiveByUserId(SEEDED_USER);

        assertThat(found).isPresent();
        assertThat(found.get().getTokenHash()).isEqualTo(newest.getTokenHash());
    }

    @Test
    void attempt_count_survives_a_round_trip() {
        PasswordResetToken saved = repository.save(
                PasswordResetToken.issue(SEEDED_USER, "h-" + UUID.randomUUID(), Duration.ofMinutes(30)));
        saved.recordFailedAttempt();
        saved.recordFailedAttempt();
        repository.save(saved);

        var reloaded = repository.findActiveByUserId(SEEDED_USER);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getAttemptCount()).isEqualTo(2);
    }

    @Test
    void delete_expired_before_removes_only_the_stale_rows() {
        String fresh = "fresh-" + UUID.randomUUID();
        repository.save(PasswordResetToken.issue(SEEDED_USER, fresh, Duration.ofMinutes(30)));
        repository.save(PasswordResetToken.issue(SEEDED_USER, "stale-" + UUID.randomUUID(), Duration.ofMinutes(-120)));

        int deleted = repository.deleteExpiredBefore(Instant.now().minus(Duration.ofMinutes(60)));

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(repository.findByTokenHash(fresh)).isPresent();
    }
}
