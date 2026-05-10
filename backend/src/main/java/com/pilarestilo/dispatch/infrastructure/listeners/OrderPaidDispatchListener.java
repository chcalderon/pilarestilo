package com.pilarestilo.dispatch.infrastructure.listeners;

import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
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
    private final GetOrderUseCase getOrderUseCase;

    public OrderPaidDispatchListener(DispatchRepository dispatchRepository,
                                     GetOrderUseCase getOrderUseCase) {
        this.dispatchRepository = dispatchRepository;
        this.getOrderUseCase = getOrderUseCase;
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.newStatus() != OrderStatus.PAID) return;
        if (dispatchRepository.existsByOrderId(event.orderId())) return;
        dispatchRepository.save(resolveDispatchToCreate(event.orderId()));
        log.info("Created PENDING dispatch for order {}", event.orderId());
    }

    private Dispatch resolveDispatchToCreate(java.util.UUID orderId) {
        try {
            OrderDto order = getOrderUseCase.execute(orderId);
            return Dispatch.create(
                    orderId,
                    order.shippingZoneCode(),
                    order.shippingCourierId(),
                    order.shippingCourierName(),
                    order.shippingAddressReference()
            );
        } catch (Exception ex) {
            log.warn("Could not preload shipping snapshot in dispatch for order {}: {}", orderId, ex.getMessage());
            return Dispatch.create(orderId);
        }
    }
}
