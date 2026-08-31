package com.pilarestilo.order.infrastructure.listeners;

import com.pilarestilo.order.application.usecases.TrackOrderAnalyticsUseCase;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** In-process transport for {@link TrackOrderAnalyticsUseCase}. Carries no behaviour. */
@Component
public class OrderAnalyticsListener {

    private final TrackOrderAnalyticsUseCase useCase;

    public OrderAnalyticsListener(TrackOrderAnalyticsUseCase useCase) {
        this.useCase = useCase;
    }

    @EventListener
    public void onOrderCreated(OrderCreated event) {
        useCase.onOrderCreated(event);
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        useCase.onOrderStatusChanged(event);
    }
}
