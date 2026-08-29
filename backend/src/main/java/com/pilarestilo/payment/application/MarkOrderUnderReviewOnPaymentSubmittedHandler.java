package com.pilarestilo.payment.application;

import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A customer uploaded their transfer receipt: the order moves to PAYMENT_UNDER_REVIEW.
 *
 * <p>This used to live inside {@code PaymentNotificationDispatcher.onPaymentSubmitted}, next to the
 * reviewer email. It is an order-status transition, not a notification, and it cannot travel with
 * the notification module when that becomes its own read-only service — so it moves here, on
 * {@code PaymentSubmitted}, with the notification side keeping only the email.
 *
 * <p>Both transports (the in-process {@code @EventListener} and its Kafka twin) delegate here and
 * hold nothing of their own — the same shape every other event handler in this codebase uses,
 * because {@code KafkaDomainEventPublisher} is {@code @Primary} when Kafka is on and a twin that
 * drifts is silently dead.
 */
@Service
public class MarkOrderUnderReviewOnPaymentSubmittedHandler {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;

    public MarkOrderUnderReviewOnPaymentSubmittedHandler(PaymentRepository paymentRepository,
                                                        OrderRepository orderRepository,
                                                        UpdateOrderStatusUseCase updateOrderStatusUseCase) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
    }

    /**
     * {@code @Transactional} because a Kafka listener runs outside any request and open-in-view is
     * off, so the two reads below need an ambient session; {@code UpdateOrderStatusUseCase.execute}
     * joins the same transaction.
     */
    @Transactional
    public void handle(UUID paymentId) {
        paymentRepository.findById(paymentId).ifPresent(payment ->
                orderRepository.findById(payment.getOrderId()).ifPresent(order -> {
                    if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                            || order.getStatus() == OrderStatus.CREATED) {
                        updateOrderStatusUseCase.execute(order.getId(), OrderStatus.PAYMENT_UNDER_REVIEW);
                    }
                }));
    }
}
