package com.pilarestilo.shared.domain.ports;

import java.util.Map;

/**
 * Sends a product-analytics event to whatever backend is configured (PostHog today, a no-op when
 * analytics is off). Server-side events carry the money numbers that must not depend on a browser
 * staying open — {@code order_created}, {@code order_paid} — keyed to the customer id so they
 * merge with the same person the storefront snippet identifies.
 *
 * <p>Implementations are fire-and-forget: a tracking failure must never surface to the caller or
 * roll back the business transaction that triggered it.
 */
public interface AnalyticsTracker {

    /**
     * @param event      event name, snake_case to match the storefront events
     * @param distinctId the person the event belongs to (the customer id); when blank the event
     *                   is dropped rather than attributed to an anonymous server identity
     * @param properties event properties; values must be JSON-serializable scalars or maps
     */
    void track(String event, String distinctId, Map<String, Object> properties);
}
