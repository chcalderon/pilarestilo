package com.pilarestilo.notificationservice.infrastructure.listeners.kafka;

import com.pilarestilo.notificationservice.application.DiscountNotificationDispatcher;
import com.pilarestilo.notificationservice.events.Events;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Transport only — behaviour is in {@link DiscountNotificationDispatcher}. */
@Component
@ConditionalOnProperty(prefix = "app.notification.listeners", name = "enabled", havingValue = "true")
public class KafkaDiscountNotificationListener {

    private final DiscountNotificationDispatcher dispatcher;

    public KafkaDiscountNotificationListener(DiscountNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('DiscountCodeAssigned')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onDiscountCodeAssigned(Events.DiscountCodeAssigned event) {
        dispatcher.onDiscountCodeAssigned(event);
    }
}
