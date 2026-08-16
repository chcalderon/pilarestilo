package com.pilarestilo.dispatch.infrastructure.listeners;

import com.pilarestilo.dispatch.application.usecases.CreateDispatchForPaidOrderUseCase;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** In-process transport for {@link CreateDispatchForPaidOrderUseCase}. Carries no behaviour. */
@Component
public class OrderPaidDispatchListener {

    private final CreateDispatchForPaidOrderUseCase useCase;

    public OrderPaidDispatchListener(CreateDispatchForPaidOrderUseCase useCase) {
        this.useCase = useCase;
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        useCase.onOrderStatusChanged(event);
    }
}
