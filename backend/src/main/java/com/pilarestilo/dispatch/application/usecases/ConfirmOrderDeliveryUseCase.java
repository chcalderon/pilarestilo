package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.GetOrderUseCase;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConfirmOrderDeliveryUseCase {

    private final DispatchRepository dispatchRepository;
    private final GetOrderUseCase getOrderUseCase;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;

    public ConfirmOrderDeliveryUseCase(DispatchRepository dispatchRepository,
                                       GetOrderUseCase getOrderUseCase,
                                       UpdateOrderStatusUseCase updateOrderStatusUseCase) {
        this.dispatchRepository = dispatchRepository;
        this.getOrderUseCase = getOrderUseCase;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
    }

    @Transactional
    public OrderDto execute(UUID orderId, UUID customerId) {
        OrderDto order = getOrderUseCase.execute(orderId);
        if (!order.customerId().equals(customerId)) {
            throw new DomainException("You can only confirm your own orders");
        }

        if (order.status() == OrderStatus.DELIVERED) {
            return order;
        }
        if (order.status() != OrderStatus.SHIPPED) {
            throw new DomainException("Order is not ready to be marked as delivered");
        }

        Dispatch dispatch = dispatchRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DomainException("Dispatch not found for order"));

        if (!"DELIVERED".equals(dispatch.getStatus().name())) {
            dispatch.deliver();
            dispatchRepository.save(dispatch);
        }

        return updateOrderStatusUseCase.execute(orderId, OrderStatus.DELIVERED);
    }
}
