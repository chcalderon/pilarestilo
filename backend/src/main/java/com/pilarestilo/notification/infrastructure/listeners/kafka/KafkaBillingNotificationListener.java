package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.billing.domain.events.SalesDocumentIssued;
import com.pilarestilo.notification.application.BillingNotificationDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka transport for billing notifications. This is the live path in production; the behaviour
 * lives in {@link BillingNotificationDispatcher} so it cannot drift from the in-process twin.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaBillingNotificationListener {

    private final BillingNotificationDispatcher dispatcher;

    public KafkaBillingNotificationListener(BillingNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('SalesDocumentIssued')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onSalesDocumentIssued(SalesDocumentIssued event) {
        dispatcher.onSalesDocumentIssued(event);
    }
}
