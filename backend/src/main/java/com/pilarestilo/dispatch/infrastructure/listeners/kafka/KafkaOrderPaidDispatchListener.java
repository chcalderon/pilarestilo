package com.pilarestilo.dispatch.infrastructure.listeners.kafka;

import com.pilarestilo.dispatch.application.usecases.CreateDispatchForPaidOrderUseCase;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka transport for {@link CreateDispatchForPaidOrderUseCase}. Carries no behaviour.
 *
 * <p>Without this the queue stayed empty in production: KafkaDomainEventPublisher is @Primary, so
 * the in-process twin never fires once Kafka is on.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaOrderPaidDispatchListener {

    private final CreateDispatchForPaidOrderUseCase useCase;

    public KafkaOrderPaidDispatchListener(CreateDispatchForPaidOrderUseCase useCase) {
        this.useCase = useCase;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-dispatch",
            topics = "#{@domainEventTopics.topicFor('OrderStatusChanged')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onOrderStatusChanged(OrderStatusChanged event) {
        useCase.onOrderStatusChanged(event);
    }
}
