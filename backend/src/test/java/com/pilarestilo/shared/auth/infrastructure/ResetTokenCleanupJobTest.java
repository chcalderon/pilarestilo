package com.pilarestilo.shared.auth.infrastructure;

import com.pilarestilo.shared.auth.domain.ports.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetTokenCleanupJobTest {

    @Mock PasswordResetTokenRepository tokenRepository;

    @Test
    void it_deletes_tokens_that_expired_more_than_a_day_ago() {
        when(tokenRepository.deleteExpiredBefore(org.mockito.ArgumentMatchers.any())).thenReturn(3);
        ResetTokenCleanupJob job = new ResetTokenCleanupJob(tokenRepository);

        Instant before = Instant.now();
        job.run();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(tokenRepository).deleteExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue())
                .isBetween(before.minus(Duration.ofDays(1)).minusSeconds(5),
                        after.minus(Duration.ofDays(1)).plusSeconds(5));
    }
}
