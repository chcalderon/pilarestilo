package com.pilarestilo.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.domain-events.kafka")
public class KafkaDomainEventsProperties {

    private boolean enabled = false;
    private String topicPrefix = "pe.domain";
    private String consumerGroupId = "pe-notification-service";
    private long retryBackoffMs = 1500;
    private long retryMaxAttempts = 3;
    private String dltSuffix = ".dlt";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTopicPrefix() { return topicPrefix; }
    public void setTopicPrefix(String topicPrefix) { this.topicPrefix = topicPrefix; }

    public String getConsumerGroupId() { return consumerGroupId; }
    public void setConsumerGroupId(String consumerGroupId) { this.consumerGroupId = consumerGroupId; }

    public long getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }

    public long getRetryMaxAttempts() { return retryMaxAttempts; }
    public void setRetryMaxAttempts(long retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }

    public String getDltSuffix() { return dltSuffix; }
    public void setDltSuffix(String dltSuffix) { this.dltSuffix = dltSuffix; }
}
