package com.pilarestilo.payment.application;

import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The order-status transition that used to hide inside {@code PaymentNotificationDispatcher}: a
 * customer uploading their transfer receipt moves the order to PAYMENT_UNDER_REVIEW. It is
 * order-domain behaviour, not a notification, so it lives here and the notification service only
 * emails the reviewers.
 */
@ExtendWith(MockitoExtension.class)
class MarkOrderUnderReviewOnPaymentSubmittedHandlerTest {

    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock UpdateOrderStatusUseCase updateOrderStatusUseCase;

    @InjectMocks MarkOrderUnderReviewOnPaymentSubmittedHandler handler;

    final UUID paymentId = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();

    private void given(OrderStatus status) {
        Payment payment = mock(Payment.class);
        lenient().when(payment.getOrderId()).thenReturn(orderId);
        Order order = mock(Order.class);
        lenient().when(order.getId()).thenReturn(orderId);
        lenient().when(order.getStatus()).thenReturn(status);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    }

    @Test
    void moves_a_pending_payment_order_to_under_review() {
        given(OrderStatus.PENDING_PAYMENT);

        handler.handle(paymentId);

        verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.PAYMENT_UNDER_REVIEW);
    }

    @Test
    void moves_a_freshly_created_order_to_under_review() {
        given(OrderStatus.CREATED);

        handler.handle(paymentId);

        verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.PAYMENT_UNDER_REVIEW);
    }

    @Test
    void leaves_an_already_advanced_order_alone() {
        given(OrderStatus.PAID);

        handler.handle(paymentId);

        verifyNoInteractions(updateOrderStatusUseCase);
    }

    @Test
    void does_nothing_for_an_unknown_payment() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        handler.handle(paymentId);

        verifyNoInteractions(orderRepository, updateOrderStatusUseCase);
    }
}
