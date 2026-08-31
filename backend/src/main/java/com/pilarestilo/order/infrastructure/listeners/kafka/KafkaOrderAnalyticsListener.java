package com.pilarestilo.order.infrastructure.listeners.kafka;

import com.pilarestilo.order.application.usecases.TrackOrderAnalyticsUseCase;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka transport for {@link TrackOrderAnalyticsUseCase}. Carries no behaviour.
 *
 * <p>Needed for the same reason as the dispatch twin: {@code KafkaDomainEventPublisher} is
 * {@code @Primary}, so with {@code APP_DOMAIN_EVENTS_KAFKA_ENABLED} on — production — the
 * in-process {@code @EventListener} never fires and no order event would reach PostHog.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaOrderAnalyticsListener {

    private final TrackOrderAnalyticsUseCase useCase;

    public KafkaOrderAnalyticsListener(TrackOrderAnalyticsUseCase useCase) {
        this.useCase = useCase;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-analytics",
            topics = "#{@domainEventTopics.topicFor('OrderCreated')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onOrderCreated(OrderCreated event) {
        useCase.onOrderCreated(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-analytics",
            topics = "#{@domainEventTopics.topicFor('OrderStatusChanged')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onOrderStatusChanged(OrderStatusChanged event) {
        useCase.onOrderStatusChanged(event);
    }
}
