package com.pilarestilo.payment.infrastructure.listeners.kafka;

import com.pilarestilo.order.application.sagas.OrderInventorySaga;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaPaymentEventListener {

    private final OrderInventorySaga orderInventorySaga;

    public KafkaPaymentEventListener(OrderInventorySaga orderInventorySaga) {
        this.orderInventorySaga = orderInventorySaga;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-payment",
            topics = "#{@domainEventTopics.topicFor('PaymentConfirmed')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onPaymentConfirmed(PaymentConfirmed event) {
        orderInventorySaga.onPaymentConfirmed(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-payment",
            topics = "#{@domainEventTopics.topicFor('PaymentRejected')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onPaymentRejected(PaymentRejected event) {
        orderInventorySaga.onPaymentRejected(event);
    }
}
