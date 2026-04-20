package com.pilarestilo.order.application.usecases;

import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.mappers.OrderMapper;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UpdateOrderStatusUseCase {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    public UpdateOrderStatusUseCase(OrderRepository orderRepository,
                                     DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderDto execute(UUID orderId, OrderStatus targetStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        OrderStatus previous = order.getStatus();

        switch (targetStatus) {
            case PENDING_PAYMENT -> order.markAsPendingPayment();
            case PAYMENT_UNDER_REVIEW -> order.markAsPaymentUnderReview();
            case PAID -> order.markAsPaid();
            case PREPARING_ORDER -> order.markAsPreparingOrder();
            case SHIPPED -> order.markAsShipped();
            case DELIVERED -> order.markAsDelivered();
            case CANCELLED -> order.cancel();
            default -> throw new DomainException("Unsupported target status: " + targetStatus);
        }

        Order saved = orderRepository.save(order);
        eventPublisher.publish(new OrderStatusChanged(
                saved.getId(), saved.getCustomerId(), previous, saved.getStatus(), Instant.now()
        ));
        return OrderMapper.toDto(saved);
    }
}
