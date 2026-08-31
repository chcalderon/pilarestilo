package com.pilarestilo.shared.infrastructure.adapters;

import com.pilarestilo.shared.domain.ports.AnalyticsTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The {@link AnalyticsTracker} used when {@code app.analytics.posthog.enabled} is false (the
 * default) or unset — so the order flows can depend on the port unconditionally. Logs at DEBUG
 * only, to confirm wiring during local work without adding noise in production.
 */
@Component
@ConditionalOnProperty(prefix = "app.analytics.posthog", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpAnalyticsTracker implements AnalyticsTracker {

    private static final Logger log = LoggerFactory.getLogger(NoOpAnalyticsTracker.class);

    @Override
    public void track(String event, String distinctId, Map<String, Object> properties) {
        log.debug("analytics disabled — dropping event {} for {}", event, distinctId);
    }
}
