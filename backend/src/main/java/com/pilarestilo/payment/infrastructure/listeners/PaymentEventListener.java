package com.pilarestilo.payment.infrastructure.listeners;

import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

@Component
public class PaymentEventListener {

    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public PaymentEventListener(UpdateOrderStatusUseCase updateOrderStatusUseCase,
                                OrderRepository orderRepository,
                                InventoryService inventoryService) {
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onPaymentConfirmed(PaymentConfirmed event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + event.orderId()));

        // Idempotency / out-of-order guard:
        // if order already progressed past payment, do nothing.
        if (order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.PREPARING_ORDER
                || order.getStatus() == OrderStatus.SHIPPED
                || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }

        if (order.getStatus() == OrderStatus.CREATED) {
            updateOrderStatusUseCase.execute(event.orderId(), OrderStatus.PENDING_PAYMENT);
        }

        updateOrderStatusUseCase.execute(event.orderId(), OrderStatus.PAID);
    }

    @EventListener
    public void onPaymentRejected(PaymentRejected event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + event.orderId()));

        // Guard against duplicate event delivery to avoid releasing stock twice.
        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.PREPARING_ORDER
                || order.getStatus() == OrderStatus.SHIPPED
                || order.getStatus() == OrderStatus.DELIVERED) {
            return;
        }

        updateOrderStatusUseCase.execute(event.orderId(), OrderStatus.CANCELLED);

        for (var item : order.getItems()) {
            inventoryService.release(item.getProductId(), item.getQuantity());
        }
    }
}
