package com.pilarestilo.dispatch.infrastructure.listeners;

import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPaidDispatchListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidDispatchListener.class);
    private final DispatchRepository dispatchRepository;

    public OrderPaidDispatchListener(DispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.newStatus() != OrderStatus.PAID) return;
        if (dispatchRepository.existsByOrderId(event.orderId())) return;
        dispatchRepository.save(Dispatch.create(event.orderId()));
        log.info("Created PENDING dispatch for order {}", event.orderId());
    }
}
