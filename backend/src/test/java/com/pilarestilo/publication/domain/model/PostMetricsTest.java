package com.pilarestilo.publication.domain.model;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostMetricsTest {

    @Test
    void empty_has_all_null_fields() {
        PostMetrics m = PostMetrics.empty();
        assertNull(m.likes());
        assertNull(m.impressions());
    }

    @Test
    void result_ok_carries_the_metrics_and_no_error() {
        var r = PublicationMetricsFetcher.Result.ok(new PostMetrics(10L, 8L, 5L, 1L, 0L, 2L));
        assertTrue(r.metrics().isPresent());
        assertEquals(5L, r.metrics().get().likes());
        assertNull(r.error());
    }

    @Test
    void result_failed_carries_the_message_and_no_metrics() {
        var r = PublicationMetricsFetcher.Result.failed("403 permission denied");
        assertFalse(r.metrics().isPresent());
        assertEquals("403 permission denied", r.error());
    }
}
