package com.pilarestilo.order.application.usecases;

import com.pilarestilo.discount.application.DiscountRedemptionService;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.mappers.OrderMapper;
import com.pilarestilo.order.application.remote.OrderRemoteCommandClient;
import com.pilarestilo.order.application.remote.OrderRemoteQueryClient;
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
    private final OrderRemoteCommandClient orderRemoteCommandClient;
    private final OrderRemoteQueryClient orderRemoteQueryClient;
    private final DiscountRedemptionService discountRedemptionService;

    public UpdateOrderStatusUseCase(OrderRepository orderRepository,
                                     DomainEventPublisher eventPublisher,
                                     OrderRemoteCommandClient orderRemoteCommandClient,
                                     OrderRemoteQueryClient orderRemoteQueryClient,
                                     DiscountRedemptionService discountRedemptionService) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.orderRemoteCommandClient = orderRemoteCommandClient;
        this.orderRemoteQueryClient = orderRemoteQueryClient;
        this.discountRedemptionService = discountRedemptionService;
    }

    @Transactional
    public OrderDto execute(UUID orderId, OrderStatus targetStatus) {
        if (orderRemoteCommandClient.isWriteEnabled()) {
            OrderDto previous = orderRemoteQueryClient.getById(orderId)
                    .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
            if (previous.status() == targetStatus) {
                return previous;
            }
            OrderDto updated = orderRemoteCommandClient.updateStatus(orderId, targetStatus);
            applyRedemptionSideEffects(orderId, targetStatus);
            eventPublisher.publish(new OrderStatusChanged(
                    updated.id(),
                    updated.customerId(),
                    previous.status(),
                    updated.status(),
                    Instant.now()
            ));
            return updated;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        OrderStatus previous = order.getStatus();
        if (previous == targetStatus) {
            return OrderMapper.toDto(order);
        }

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
        applyRedemptionSideEffects(saved.getId(), targetStatus);
        eventPublisher.publish(new OrderStatusChanged(
                saved.getId(), saved.getCustomerId(), previous, saved.getStatus(), Instant.now()
        ));
        return OrderMapper.toDto(saved);
    }

    /**
     * Settles or releases the order's discount redemption.
     *
     * <p>Hooked here, and only here, because every route to PAID or CANCELLED funnels through this
     * method: OrderInventorySaga (both handlers), the admin PATCH /api/orders/{id}/status endpoint,
     * and the dispatch use cases. A listener on PaymentConfirmed/PaymentRejected would miss the
     * manual admin path, and any new @EventListener would be silently dead whenever
     * APP_DOMAIN_EVENTS_KAFKA_ENABLED is on, since KafkaDomainEventPublisher is @Primary.
     *
     * <p>Do not also call this from OrderInventorySaga: the saga delegates here, so it would
     * decrement times_used twice.
     *
     * <p>Runs inside the surrounding @Transactional, so the ledger commits with the status change.
     * Both operations are idempotent, and both no-op for orders that carried no discount.
     */
    private void applyRedemptionSideEffects(UUID orderId, OrderStatus targetStatus) {
        switch (targetStatus) {
            case PAID -> discountRedemptionService.settle(orderId);
            case CANCELLED -> discountRedemptionService.release(orderId);
            default -> { }
        }
    }
}
