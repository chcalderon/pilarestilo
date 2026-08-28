package com.pilarestilo.notification.infrastructure.listeners.kafka;

import com.pilarestilo.notification.application.PaymentNotificationDispatcher;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.events.PaymentSubmitted;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka transport for payment events, and the live path in production.
 *
 * <p>The behaviour lives in {@link PaymentNotificationDispatcher} so it cannot drift from the
 * in-process twin again — it already had, in both directions.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaPaymentNotificationListener {

    private final PaymentNotificationDispatcher dispatcher;

    public KafkaPaymentNotificationListener(PaymentNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * A Kafka listener runs outside any request, and open-in-view is disabled, so the repository
     * reads the dispatcher makes have no ambient session. A transaction keeps them on one, which
     * is why this transport is not a bare delegation like the others.
     *
     * <p>Writable, not read-only: this branch also records the in-app notification, and a
     * read-only transaction puts Hibernate in manual flush mode, so that insert would never
     * reach the database.
     */
    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('PaymentConfirmed')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    @Transactional
    public void onPaymentConfirmed(PaymentConfirmed event) {
        dispatcher.onPaymentConfirmed(event);
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('PaymentRejected')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    @Transactional(readOnly = true)
    public void onPaymentRejected(PaymentRejected event) {
        dispatcher.onPaymentRejected(event);
    }

    /** Read-only now: this branch only emails the reviewers. The order's move to
     *  PAYMENT_UNDER_REVIEW is handled separately by the payment module. */
    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-notification",
            topics = "#{@domainEventTopics.topicFor('PaymentSubmitted')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    @Transactional(readOnly = true)
    public void onPaymentSubmitted(PaymentSubmitted event) {
        dispatcher.onPaymentSubmitted(event);
    }
}
