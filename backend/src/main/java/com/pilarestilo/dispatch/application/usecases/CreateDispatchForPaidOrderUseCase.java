package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Puts a paid order into the dispatch queue.
 *
 * <p>This lived in an {@code @EventListener} and nowhere else, which meant it did not run at all
 * whenever {@code APP_DOMAIN_EVENTS_KAFKA_ENABLED} is on — production — because
 * {@code KafkaDomainEventPublisher} is {@code @Primary}. Paid orders never reached the queue, so
 * the shop had no way to know something needed sending. The behaviour lives here now and the two
 * listeners are transports with none of their own, the same shape as the notification dispatchers.
 */
@Service
public class CreateDispatchForPaidOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateDispatchForPaidOrderUseCase.class);

    private final DispatchRepository dispatchRepository;
    private final GetOrderUseCase getOrderUseCase;

    public CreateDispatchForPaidOrderUseCase(DispatchRepository dispatchRepository,
                                             GetOrderUseCase getOrderUseCase) {
        this.dispatchRepository = dispatchRepository;
        this.getOrderUseCase = getOrderUseCase;
    }

    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.newStatus() != OrderStatus.PAID) {
            return;
        }
        if (dispatchRepository.existsByOrderId(event.orderId())) {
            return;
        }
        dispatchRepository.save(buildDispatch(event.orderId()));
        log.info("Created PENDING dispatch for order {}", event.orderId());
    }

    /**
     * Copies the order's shipping details onto the dispatch so the queue can be worked without a
     * second lookup, and so a later change to the address does not rewrite history.
     *
     * <p>If the order cannot be read, the dispatch is still created — losing it would lose the
     * only record that something has to be sent — but at WARN with the reason, because the row it
     * leaves behind shows an empty zone, courier and reference, and that has been mistaken for a
     * display bug.
     */
    private Dispatch buildDispatch(UUID orderId) {
        try {
            OrderDto order = getOrderUseCase.execute(orderId);
            return Dispatch.create(
                    orderId,
                    order.shippingZoneCode(),
                    order.shippingCourierId(),
                    order.shippingCourierName(),
                    order.shippingAddressReference()
            );
        } catch (RuntimeException ex) {
            log.warn("Dispatch for order {} created without its shipping snapshot: {}",
                    orderId, ex.getMessage());
            return Dispatch.create(orderId);
        }
    }
}
