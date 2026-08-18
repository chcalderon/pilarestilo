package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.notification.application.ReturnNotificationDispatcher;
import com.pilarestilo.returns.domain.events.RefundRegistered;
import com.pilarestilo.returns.domain.events.ReturnApproved;
import com.pilarestilo.returns.domain.events.ReturnRequested;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka transport for return notifications. This is the live path in production; the behaviour
 * lives in {@link ReturnNotificationDispatcher} so it cannot drift from the in-process twin.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaReturnNotificationListener {

    private final ReturnNotificationDispatcher dispatcher;

    public KafkaReturnNotificationListener(ReturnNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('ReturnRequested')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onReturnRequested(ReturnRequested event) {
        dispatcher.onReturnRequested(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('ReturnApproved')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onReturnApproved(ReturnApproved event) {
        dispatcher.onReturnApproved(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('RefundRegistered')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onRefundRegistered(RefundRegistered event) {
        dispatcher.onRefundRegistered(event);
    }
}
