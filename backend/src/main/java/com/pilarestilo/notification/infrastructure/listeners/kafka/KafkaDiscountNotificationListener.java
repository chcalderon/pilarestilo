package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.discount.domain.events.DiscountCodeAssigned;
import com.pilarestilo.notification.application.DiscountNotificationDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka transport for discount assignments, which had no consumer at all until now.
 *
 * <p>The event was published to {@code pe.domain.discount-code-assigned} and read by nobody —
 * the broker reported UNKNOWN_TOPIC_OR_PARTITION because the topic only ever existed as an
 * auto-created empty one. With Kafka on, as production runs, a customer handed an exclusive code
 * was never told about it.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaDiscountNotificationListener {

    private final DiscountNotificationDispatcher dispatcher;

    public KafkaDiscountNotificationListener(DiscountNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('DiscountCodeAssigned')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onDiscountCodeAssigned(DiscountCodeAssigned event) {
        dispatcher.onDiscountCodeAssigned(event);
    }
}
