package com.pilarestilo.notificationservice.infrastructure.listeners.kafka;

import com.pilarestilo.notificationservice.application.OrderNotificationDispatcher;
import com.pilarestilo.notificationservice.events.Events;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Transport only — behaviour is in {@link OrderNotificationDispatcher}. Topic names use the
 * monolith's event simple names ({@code OrderCreated} etc), not the local {@code Events.*} names.
 */
@Component
@ConditionalOnProperty(prefix = "app.notification.listeners", name = "enabled", havingValue = "true")
public class KafkaOrderNotificationListener {

    private final OrderNotificationDispatcher dispatcher;

    public KafkaOrderNotificationListener(OrderNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('OrderCreated')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onOrderCreated(Events.OrderCreated event) {
        dispatcher.onOrderCreated(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('OrderStatusChanged')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onOrderStatusChanged(Events.OrderStatusChanged event) {
        dispatcher.onOrderStatusChanged(event);
    }
}
