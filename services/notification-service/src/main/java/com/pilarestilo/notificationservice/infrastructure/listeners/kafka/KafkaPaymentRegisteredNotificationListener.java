package com.pilarestilo.notificationservice.infrastructure.listeners.kafka;

import com.pilarestilo.notificationservice.application.PaymentRegisteredNotificationDispatcher;
import com.pilarestilo.notificationservice.events.Events;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Transport only — behaviour is in {@link PaymentRegisteredNotificationDispatcher}. */
@Component
@ConditionalOnProperty(prefix = "app.notification.listeners", name = "enabled", havingValue = "true")
public class KafkaPaymentRegisteredNotificationListener {

    private final PaymentRegisteredNotificationDispatcher dispatcher;

    public KafkaPaymentRegisteredNotificationListener(PaymentRegisteredNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('PaymentRegistered')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onPaymentRegistered(Events.PaymentRegistered event) {
        dispatcher.onPaymentRegistered(event);
    }
}
