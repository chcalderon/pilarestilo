package com.pilarestilo.notificationservice.infrastructure.listeners.kafka;

import com.pilarestilo.notificationservice.application.UserNotificationDispatcher;
import com.pilarestilo.notificationservice.events.Events;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Transport only — behaviour is in {@link UserNotificationDispatcher}. */
@Component
@ConditionalOnProperty(prefix = "app.notification.listeners", name = "enabled", havingValue = "true")
public class KafkaUserNotificationListener {

    private final UserNotificationDispatcher dispatcher;

    public KafkaUserNotificationListener(UserNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('UserRegistered')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onUserRegistered(Events.UserRegistered event) {
        dispatcher.onUserRegistered(event);
    }
}
