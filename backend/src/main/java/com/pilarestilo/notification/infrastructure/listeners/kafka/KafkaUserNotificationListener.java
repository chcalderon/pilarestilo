package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.notification.application.UserNotificationDispatcher;
import com.pilarestilo.user.domain.events.UserRegistered;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka transport for user notifications. This is the live path in production; the
 * behaviour lives in {@link UserNotificationDispatcher} so it cannot drift from the
 * in-process twin.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
@ConditionalOnBooleanProperty(name = "app.notification.kafka-listeners.enabled", matchIfMissing = true)
public class KafkaUserNotificationListener {

    private final UserNotificationDispatcher dispatcher;

    public KafkaUserNotificationListener(UserNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('UserRegistered')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onUserRegistered(UserRegistered event) {
        dispatcher.onUserRegistered(event);
    }
}
