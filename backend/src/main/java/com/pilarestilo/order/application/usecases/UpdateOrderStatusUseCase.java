package com.pilarestilo.order.application.usecases;

import com.pilarestilo.discount.application.DiscountRedemptionService;
import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.inventory.domain.model.StockMovementOrigin;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UpdateOrderStatusUseCase {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;
    private final OrderRemoteCommandClient orderRemoteCommandClient;
    private final OrderRemoteQueryClient orderRemoteQueryClient;
    private final DiscountRedemptionService discountRedemptionService;
    private final InventoryService inventoryService;

    public UpdateOrderStatusUseCase(OrderRepository orderRepository,
                                     DomainEventPublisher eventPublisher,
                                     OrderRemoteCommandClient orderRemoteCommandClient,
                                     OrderRemoteQueryClient orderRemoteQueryClient,
                                     DiscountRedemptionService discountRedemptionService,
                                     InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.orderRemoteCommandClient = orderRemoteCommandClient;
        this.orderRemoteQueryClient = orderRemoteQueryClient;
        this.discountRedemptionService = discountRedemptionService;
        this.inventoryService = inventoryService;
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
            applyCancellationEffects(orderId, previous.status(), targetStatus,
                    updated.items().stream()
                            .map(i -> new ReservedLine(i.productId(), i.quantity(),
                                    i.variantColor(), i.variantSize()))
                            .toList());
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
        applyCancellationEffects(saved.getId(), previous, targetStatus,
                saved.getItems().stream()
                        .map(i -> new ReservedLine(i.getProductId(), i.getQuantity(),
                                i.getVariantColor(), i.getVariantSize()))
                        .toList());
        eventPublisher.publish(new OrderStatusChanged(
                saved.getId(), saved.getCustomerId(), previous, saved.getStatus(), Instant.now()
        ));
        return OrderMapper.toDto(saved);
    }

    /** One order line, in the shape the inventory port needs to give its reservation back. */
    private record ReservedLine(UUID productId, int quantity, String variantColor, String variantSize) {}

    /**
     * Everything that has to unwind when an order reaches PAID or CANCELLED: the discount
     * redemption, and the inventory the order was holding.
     *
     * <p>Hooked here, and only here, because every route to those two states funnels through this
     * method: OrderInventorySaga (both handlers), the admin PATCH /api/orders/{id}/status endpoint,
     * and the dispatch use cases. A listener on PaymentConfirmed/PaymentRejected would miss the
     * manual admin path, and any new @EventListener would be silently dead whenever
     * APP_DOMAIN_EVENTS_KAFKA_ENABLED is on, since KafkaDomainEventPublisher is @Primary.
     *
     * <p>Do not also call these from OrderInventorySaga: the saga delegates here, so it would
     * apply them twice. Both inventory operations used to live in the saga, reachable only from a
     * payment event, which is why doing either by hand went nowhere: an admin cancelling an order
     * left its stock reserved forever, and an admin marking one paid left the units neither
     * available nor sold.
     *
     * <p>Runs inside the surrounding @Transactional, so both commit with the status change.
     */
    private void applyCancellationEffects(UUID orderId,
                                          OrderStatus previousStatus,
                                          OrderStatus targetStatus,
                                          List<ReservedLine> lines) {
        switch (targetStatus) {
            case PAID -> {
                discountRedemptionService.settle(orderId);
                if (stockWasStillReserved(previousStatus)) {
                    for (ReservedLine line : lines) {
                        inventoryService.confirm(line.productId(), line.quantity(),
                                line.variantColor(), line.variantSize(),
                                StockMovementOrigin.forOrder(orderId));
                    }
                }
            }
            case CANCELLED -> {
                discountRedemptionService.release(orderId);
                for (ReservedLine line : lines) {
                    /*
                     * Which operation depends on whether the units are still merely held.
                     *
                     * Cancelling after PAID used to do nothing here, on the reasoning that the goods
                     * had already left the warehouse — but the goods had not left anything except a
                     * column. A sale undone before it ships has its garments back on the rack, and
                     * the shelf count has to say so. They were simply lost until now.
                     */
                    if (stockWasStillReserved(previousStatus)) {
                        inventoryService.release(line.productId(), line.quantity(),
                                line.variantColor(), line.variantSize(),
                                StockMovementOrigin.forOrder(orderId));
                    } else {
                        inventoryService.returnToStock(line.productId(), line.quantity(),
                                line.variantColor(), line.variantSize(),
                                StockMovementOrigin.forOrder(orderId));
                    }
                }
            }
            // Every other target status carries no reservation/discount side effects to undo.
            default -> { }
        }
    }

    /**
     * True while the order's stock is merely held, not yet sold.
     *
     * <p>Reaching PAID runs {@code inventoryService.confirm}, which takes the units out of both
     * on-hand and reserved. So a cancellation before that releases a reservation, and one after it
     * returns units to the shelf — {@code release} would decrement a reservation that no longer
     * exists. The discount keeps the older asymmetry: a settled redemption is not handed back.
     */
    private static boolean stockWasStillReserved(OrderStatus previousStatus) {
        return previousStatus == OrderStatus.CREATED
                || previousStatus == OrderStatus.PENDING_PAYMENT
                || previousStatus == OrderStatus.PAYMENT_UNDER_REVIEW;
    }
}
