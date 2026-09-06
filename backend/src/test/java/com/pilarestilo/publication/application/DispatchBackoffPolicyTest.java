package com.pilarestilo.publication.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchBackoffPolicyTest {

    private final DispatchBackoffPolicy policy =
            new DispatchBackoffPolicy(List.of(2, 10, 30, 120, 360));

    @Test
    void delay_for_each_retry_index_matches_the_configured_minutes() {
        assertEquals(Duration.ofMinutes(2), policy.delayFor(0));
        assertEquals(Duration.ofMinutes(10), policy.delayFor(1));
        assertEquals(Duration.ofMinutes(30), policy.delayFor(2));
        assertEquals(Duration.ofMinutes(120), policy.delayFor(3));
        assertEquals(Duration.ofMinutes(360), policy.delayFor(4));
    }

    @Test
    void can_retry_until_the_list_is_exhausted() {
        assertTrue(policy.canRetry(0));
        assertTrue(policy.canRetry(4));
        assertFalse(policy.canRetry(5));
        assertEquals(5, policy.maxRetries());
    }
}
