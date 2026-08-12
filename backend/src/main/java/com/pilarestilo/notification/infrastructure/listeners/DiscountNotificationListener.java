package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.discount.domain.events.DiscountCodeAssigned;
import com.pilarestilo.notification.application.DiscountNotificationDispatcher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * In-process transport. Dead whenever Kafka is enabled — see {@link
 * DiscountNotificationDispatcher}, which holds the behaviour both transports share.
 */
@Component
public class DiscountNotificationListener {

    private final DiscountNotificationDispatcher dispatcher;

    public DiscountNotificationListener(DiscountNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void onDiscountCodeAssigned(DiscountCodeAssigned event) {
        dispatcher.onDiscountCodeAssigned(event);
    }
}
