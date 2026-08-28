package com.pilarestilo.notificationservice.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Maps an event's simple name to its Kafka topic and dead-letter topic, byte-for-byte the same
 * convention the monolith publishes with ({@code pe.domain.<kebab-case>}, DLT suffix {@code .dlt}).
 */
@Component("domainEventTopics")
public class DomainEventTopics {

    private final String topicPrefix;
    private final String dltSuffix;

    public DomainEventTopics(
            @Value("${app.domain-events.kafka.topic-prefix:pe.domain}") String topicPrefix,
            @Value("${app.domain-events.kafka.dlt-suffix:.dlt}") String dltSuffix
    ) {
        this.topicPrefix = topicPrefix;
        this.dltSuffix = dltSuffix;
    }

    public String topicFor(String eventType) {
        return topicPrefix + "." + toKebabCase(eventType);
    }

    public String deadLetterTopicFor(String topic) {
        return topic + dltSuffix;
    }

    private String toKebabCase(String input) {
        return input
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9\\-]", "-")
                .toLowerCase();
    }
}
