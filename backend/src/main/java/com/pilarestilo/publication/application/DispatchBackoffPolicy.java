package com.pilarestilo.publication.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * The fixed backoff schedule for automatic dispatch retries. {@code retryCount} is how many
 * automatic retries the row has already made (0 on the first failure). The list length is the cap.
 */
@Component
public class DispatchBackoffPolicy {

    private final List<Integer> backoffMinutes;

    public DispatchBackoffPolicy(
            @Value("${app.social-publishing.dispatch.backoff-minutes}") List<Integer> backoffMinutes) {
        this.backoffMinutes = List.copyOf(backoffMinutes);
    }

    public boolean canRetry(int retryCount) {
        return retryCount < backoffMinutes.size();
    }

    public Duration delayFor(int retryCount) {
        return Duration.ofMinutes(backoffMinutes.get(retryCount));
    }

    public int maxRetries() {
        return backoffMinutes.size();
    }
}
