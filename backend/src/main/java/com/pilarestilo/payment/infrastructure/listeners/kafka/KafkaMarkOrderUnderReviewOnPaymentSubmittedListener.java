package com.pilarestilo.payment.infrastructure.listeners.kafka;

import com.pilarestilo.payment.application.MarkOrderUnderReviewOnPaymentSubmittedHandler;
import com.pilarestilo.payment.domain.events.PaymentSubmitted;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka transport for {@link PaymentSubmitted} → order moves to PAYMENT_UNDER_REVIEW, and the live
 * path in production. The behaviour lives in {@link MarkOrderUnderReviewOnPaymentSubmittedHandler},
 * which is {@code @Transactional} so the reads it makes have a session on this transport too.
 */
@Component
@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")
public class KafkaMarkOrderUnderReviewOnPaymentSubmittedListener {

    private final MarkOrderUnderReviewOnPaymentSubmittedHandler handler;

    public KafkaMarkOrderUnderReviewOnPaymentSubmittedListener(MarkOrderUnderReviewOnPaymentSubmittedHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(
            groupId = "${app.domain-events.kafka.consumer-group-id:pe-backend-domain-events}-payment",
            topics = "#{@domainEventTopics.topicFor('PaymentSubmitted')}",
            containerFactory = "domainEventsKafkaListenerContainerFactory"
    )
    public void onPaymentSubmitted(PaymentSubmitted event) {
        handler.handle(event.paymentId());
    }
}
