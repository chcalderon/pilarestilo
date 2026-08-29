package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.notification.application.OrderNotificationDispatcher;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka transport for order notifications. This is the live path in production; the
 * behaviour lives in {@link OrderNotificationDispatcher} so it cannot drift from the
 * in-process twin again.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
@ConditionalOnBooleanProperty(name = "app.notification.kafka-listeners.enabled", matchIfMissing = true)
public class KafkaOrderNotificationListener {

    private final OrderNotificationDispatcher dispatcher;

    public KafkaOrderNotificationListener(OrderNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('OrderCreated')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onOrderCreated(OrderCreated event) {
        dispatcher.onOrderCreated(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('OrderStatusChanged')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onOrderStatusChanged(OrderStatusChanged event) {
        dispatcher.onOrderStatusChanged(event);
    }
}
