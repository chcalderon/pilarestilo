package com.pilarestilo.shared.auth.infrastructure;

import com.pilarestilo.shared.auth.domain.ports.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Prunes password-reset tokens a day past their expiry. They are single-use and short-lived, so a
 * lazy sweep is enough; keeping a day's grace leaves the rows around long enough to be useful when
 * looking into a "the link didn't work" report.
 */
@Component
public class ResetTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ResetTokenCleanupJob.class);

    private final PasswordResetTokenRepository tokenRepository;

    public ResetTokenCleanupJob(PasswordResetTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Scheduled(cron = "${app.password-reset.cleanup-cron:0 30 3 * * *}")
    public void run() {
        int removed = tokenRepository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Pruned {} expired password-reset tokens", removed);
        }
    }
}
