package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.billing.domain.events.SalesDocumentIssued;
import com.pilarestilo.notification.application.BillingNotificationDispatcher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * In-process transport for billing notifications. Dead whenever Kafka is enabled, because
 * {@code KafkaDomainEventPublisher} is {@code @Primary} then — see
 * {@link BillingNotificationDispatcher}, which holds the behaviour both transports share.
 */
@Component
public class BillingNotificationListener {

    private final BillingNotificationDispatcher dispatcher;

    public BillingNotificationListener(BillingNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void onSalesDocumentIssued(SalesDocumentIssued event) {
        dispatcher.onSalesDocumentIssued(event);
    }
}
