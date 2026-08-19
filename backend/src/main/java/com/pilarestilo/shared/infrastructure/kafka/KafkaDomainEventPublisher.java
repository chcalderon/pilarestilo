package com.pilarestilo.shared.infrastructure.kafka;

import com.pilarestilo.shared.domain.DomainEvent;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.infrastructure.events.DomainEventTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Primary
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DomainEventTopics domainEventTopics;

    public KafkaDomainEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                     DomainEventTopics domainEventTopics) {
        this.kafkaTemplate = kafkaTemplate;
        this.domainEventTopics = domainEventTopics;
    }

    @Override
    public void publish(DomainEvent event) {
        String topic = domainEventTopics.topicFor(event);
        String key = resolveEventKey(event);
        try {
            kafkaTemplate.send(topic, key, event).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Published domain event {} to topic {}", event.getClass().getSimpleName(), topic);
        } catch (InterruptedException ex) {
            /*
             * Swallowing this cleared the flag and left the thread running as if nothing had asked
             * it to stop, which on shutdown means a request the container is trying to drain.
             */
            Thread.currentThread().interrupt();
            throw new DomainException("Could not publish domain event " + event.getClass().getSimpleName() + " to Kafka");
        } catch (Exception ex) {
            throw new DomainException("Could not publish domain event " + event.getClass().getSimpleName() + " to Kafka");
        }
    }

    private String resolveEventKey(DomainEvent event) {
        for (String candidate : new String[]{"orderId", "paymentId", "productId", "customerId", "reviewId", "discountId"}) {
            try {
                Method method = event.getClass().getMethod(candidate);
                Object value = method.invoke(event);
                if (value instanceof UUID uuid) {
                    return uuid.toString();
                }
                if (value != null) {
                    return value.toString();
                }
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }
        return event.getClass().getSimpleName() + "-" + Instant.now().toEpochMilli();
    }
}
