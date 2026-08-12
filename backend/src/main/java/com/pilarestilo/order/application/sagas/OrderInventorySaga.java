package com.pilarestilo.order.application.sagas;

import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class OrderInventorySaga {

    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final OrderRepository orderRepository;

    public OrderInventorySaga(UpdateOrderStatusUseCase updateOrderStatusUseCase,
                              OrderRepository orderRepository) {
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
        this.orderRepository = orderRepository;
    }

    public void onPaymentConfirmed(PaymentConfirmed event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + event.orderId()));

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

        // Converting the reservation into a sale is UpdateOrderStatusUseCase's job, reached by
        // every route to PAID rather than by this event alone.
        updateOrderStatusUseCase.execute(event.orderId(), OrderStatus.PAID);
    }

    public void onPaymentRejected(PaymentRejected event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + event.orderId()));

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.PAID
                || order.getStatus() == OrderStatus.PREPARING_ORDER
                || order.getStatus() == OrderStatus.SHIPPED
                || order.getStatus() == OrderStatus.DELIVERED) {
            return;
        }

        // Releasing the reservation is UpdateOrderStatusUseCase's job now, not this saga's.
        // It used to happen here, which meant a cancellation arriving any other way -- the admin
        // PATCH endpoint above all -- left the stock reserved with nothing to free it.
        updateOrderStatusUseCase.execute(event.orderId(), OrderStatus.CANCELLED);
    }
}
