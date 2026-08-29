package com.pilarestilo.notificationservice.infrastructure.listeners.kafka;

import com.pilarestilo.notificationservice.application.ReturnNotificationDispatcher;
import com.pilarestilo.notificationservice.events.Events;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Transport only — behaviour is in {@link ReturnNotificationDispatcher}. */
@Component
@ConditionalOnProperty(prefix = "app.notification.listeners", name = "enabled", havingValue = "true")
public class KafkaReturnNotificationListener {

    private final ReturnNotificationDispatcher dispatcher;

    public KafkaReturnNotificationListener(ReturnNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('ReturnRequested')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onReturnRequested(Events.ReturnRequested event) {
        dispatcher.onReturnRequested(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('ReturnApproved')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onReturnApproved(Events.ReturnApproved event) {
        dispatcher.onReturnApproved(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('RefundRegistered')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onRefundRegistered(Events.RefundRegistered event) {
        dispatcher.onRefundRegistered(event);
    }
}
