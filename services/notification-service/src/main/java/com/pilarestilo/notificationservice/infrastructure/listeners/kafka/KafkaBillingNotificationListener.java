package com.pilarestilo.notificationservice.infrastructure.listeners.kafka;

import com.pilarestilo.notificationservice.application.BillingNotificationDispatcher;
import com.pilarestilo.notificationservice.events.Events;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Transport only — behaviour is in {@link BillingNotificationDispatcher}. */
@Component
@ConditionalOnProperty(prefix = "app.notification.listeners", name = "enabled", havingValue = "true")
public class KafkaBillingNotificationListener {

    private final BillingNotificationDispatcher dispatcher;

    public KafkaBillingNotificationListener(BillingNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('SalesDocumentIssued')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onSalesDocumentIssued(Events.SalesDocumentIssued event) {
        dispatcher.onSalesDocumentIssued(event);
    }
}
