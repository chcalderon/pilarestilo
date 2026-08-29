package com.pilarestilo.notificationservice.infrastructure.listeners.kafka;

import com.pilarestilo.notificationservice.application.PaymentNotificationDispatcher;
import com.pilarestilo.notificationservice.events.Events;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Transport only — behaviour is in {@link PaymentNotificationDispatcher}. */
@Component
@ConditionalOnProperty(prefix = "app.notification.listeners", name = "enabled", havingValue = "true")
public class KafkaPaymentNotificationListener {

    private final PaymentNotificationDispatcher dispatcher;

    public KafkaPaymentNotificationListener(PaymentNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('PaymentConfirmed')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onPaymentConfirmed(Events.PaymentConfirmed event) {
        dispatcher.onPaymentConfirmed(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('PaymentSubmitted')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onPaymentSubmitted(Events.PaymentSubmitted event) {
        dispatcher.onPaymentSubmitted(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}",
            topics = "#{@domainEventTopics.topicFor('PaymentRejected')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory")
    public void onPaymentRejected(Events.PaymentRejected event) {
        dispatcher.onPaymentRejected(event);
    }
}
