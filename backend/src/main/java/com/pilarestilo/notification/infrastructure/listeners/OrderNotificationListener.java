package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.application.OrderNotificationDispatcher;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * In-process transport for order notifications. Dead whenever Kafka is enabled, because
 * {@code KafkaDomainEventPublisher} is {@code @Primary} then — see
 * {@link OrderNotificationDispatcher}, which holds the behaviour both transports share.
 */
@Component
public class OrderNotificationListener {

    private final OrderNotificationDispatcher dispatcher;

    public OrderNotificationListener(OrderNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void onOrderCreated(OrderCreated event) {
        dispatcher.onOrderCreated(event);
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        dispatcher.onOrderStatusChanged(event);
    }
}
