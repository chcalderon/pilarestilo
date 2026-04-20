package com.pilarestilo.payment.infrastructure.listeners;

import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.application.usecases.RegisterPaymentUseCase;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final OrderRepository orderRepository;

    public OrderEventListener(RegisterPaymentUseCase registerPaymentUseCase,
                               OrderRepository orderRepository) {
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.orderRepository = orderRepository;
    }

    @EventListener
    public void onOrderCreated(OrderCreated event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new DomainException("Order not found during payment registration: " + event.orderId()));
        registerPaymentUseCase.execute(event.orderId(), order.getPaymentMethod());
    }
}
