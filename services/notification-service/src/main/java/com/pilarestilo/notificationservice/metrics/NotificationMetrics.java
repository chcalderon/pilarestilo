package com.pilarestilo.notificationservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * {@code notification_send_failures_total{channel=...}} — a dead channel becomes visible in Grafana
 * instead of guessed at from log lines. Each channel sender bumps this on its catch branch.
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void countSendFailure(String channel) {
        registry.counter("notification_send_failures_total", "channel", channel).increment();
    }
}
