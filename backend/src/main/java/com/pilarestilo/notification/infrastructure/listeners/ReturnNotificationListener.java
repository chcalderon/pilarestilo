package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.application.ReturnNotificationDispatcher;
import com.pilarestilo.returns.domain.events.RefundRegistered;
import com.pilarestilo.returns.domain.events.ReturnApproved;
import com.pilarestilo.returns.domain.events.ReturnRequested;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * In-process transport for return notifications. Dead whenever Kafka is enabled, because
 * {@code KafkaDomainEventPublisher} is {@code @Primary} then — see
 * {@link ReturnNotificationDispatcher}, which holds the behaviour both transports share.
 */
@Component
public class ReturnNotificationListener {

    private final ReturnNotificationDispatcher dispatcher;

    public ReturnNotificationListener(ReturnNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void onReturnRequested(ReturnRequested event) {
        dispatcher.onReturnRequested(event);
    }

    @EventListener
    public void onReturnApproved(ReturnApproved event) {
        dispatcher.onReturnApproved(event);
    }

    @EventListener
    public void onRefundRegistered(RefundRegistered event) {
        dispatcher.onRefundRegistered(event);
    }
}
