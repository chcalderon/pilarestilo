package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.notification.application.PaymentRegisteredNotificationDispatcher;
import com.pilarestilo.payment.domain.events.PaymentRegistered;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka transport for {@code PaymentRegistered}, and the live path in production. The behaviour
 * lives in {@link PaymentRegisteredNotificationDispatcher}, which is
 * {@code @Transactional(readOnly = true)} so its repository reads have a session on this transport
 * too.
 *
 * <p>Not optional. {@code KafkaDomainEventPublisher} is {@code @Primary}, so with
 * {@code APP_DOMAIN_EVENTS_KAFKA_ENABLED=true} — which production runs — the in-process
 * {@code @EventListener} never fires and the transfer instructions would silently never be sent.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaPaymentRegisteredNotificationListener {

    private final PaymentRegisteredNotificationDispatcher dispatcher;

    public KafkaPaymentRegisteredNotificationListener(PaymentRegisteredNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('PaymentRegistered')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onPaymentRegistered(PaymentRegistered event) {
        dispatcher.onPaymentRegistered(event);
    }
}
