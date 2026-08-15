package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentSubmitted;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * What a payment event means for notifications.
 *
 * <p>Fourth of its kind, and the twins had drifted twice over:
 *
 * <ul>
 *   <li>the Kafka listener never wrote the in-app record, so with Kafka on — which production
 *       runs — no PAYMENT_RECEIVED row had been written since the day it was switched on;</li>
 *   <li>it also dropped the system-versus-manual guard, so an admin rejecting a payment by hand,
 *       or a gateway declining one, sent the customer an automated cancellation notice that was
 *       never meant to fire for those.</li>
 * </ul>
 */
@Service
public class PaymentNotificationDispatcher {

    private final NotificationSender notificationSender;
    private final NotificationComposer composer;
    private final InAppNotificationPort inAppNotificationPort;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;

    public PaymentNotificationDispatcher(NotificationSender notificationSender,
                                         NotificationComposer composer,
                                         InAppNotificationPort inAppNotificationPort,
                                         OrderRepository orderRepository,
                                         UserRepository userRepository,
                                         PaymentRepository paymentRepository,
                                         UpdateOrderStatusUseCase updateOrderStatusUseCase) {
        this.notificationSender = notificationSender;
        this.composer = composer;
        this.inAppNotificationPort = inAppNotificationPort;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.updateOrderStatusUseCase = updateOrderStatusUseCase;
    }

    /** Whoever may approve a payment. Kept beside the send so the two cannot fall out of step. */
    private static final List<UserRole> PAYMENT_REVIEWERS =
            List.of(UserRole.ADMIN, UserRole.ADMINISTRACION);

    /**
     * A customer uploaded their transfer receipt.
     *
     * <p>PaymentSubmitted had no listener at all, so this did nothing: the order stayed where it
     * was and nobody was told a receipt was waiting. The event was published into the void.
     *
     * <p>Two things follow from it. The order moves to PAYMENT_UNDER_REVIEW, which is what the
     * customer and the order list should both read. And everyone who can approve a payment is
     * emailed, because a receipt nobody looks at is the same as no receipt.
     */
    public void onPaymentSubmitted(PaymentSubmitted event) {
        paymentRepository.findById(event.paymentId()).ifPresent(payment ->
                orderRepository.findById(payment.getOrderId()).ifPresent(order -> {
                    if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                            || order.getStatus() == OrderStatus.CREATED) {
                        updateOrderStatusUseCase.execute(order.getId(), OrderStatus.PAYMENT_UNDER_REVIEW);
                    }

                    String buyerName = userRepository.findById(order.getCustomerId())
                            .map(User::getFullName)
                            .orElse("Cliente");

                    notifyReviewers(composer.paymentProofSubmitted(order, payment, buyerName));
                }));
    }

    /**
     * Sends to every active reviewer.
     *
     * <p>Their channel preference is deliberately ignored: this is a staff alert about money
     * waiting, not marketing, and a reviewer who set themselves to WhatsApp would otherwise
     * silently stop being told.
     */
    private void notifyReviewers(com.pilarestilo.notification.domain.model.NotificationMessage message) {
        userRepository.findByRoleIn(PAYMENT_REVIEWERS, PageRequest.of(0, 50))
                .getContent().stream()
                .filter(User::isActive)
                .filter(reviewer -> reviewer.getEmail() != null && !reviewer.getEmail().isBlank())
                .forEach(reviewer -> notificationSender.send(
                        message,
                        NotificationRecipient.of(null, reviewer.getEmail(), "EMAIL")));
    }

    public void onPaymentConfirmed(PaymentConfirmed event) {
        orderRepository.findById(event.orderId()).ifPresentOrElse(
                order -> {
                    NotificationRecipient recipient = userRepository.findById(order.getCustomerId())
                            .map(this::recipientFor)
                            .orElse(NotificationRecipient.unknown());
                    notificationSender.send(composer.paymentReceived(event.paymentId()), recipient);
                    inAppNotificationPort.notifyPaymentReceived(order.getCustomerId(), event.paymentId());
                },
                /* No order row: the payment is still real, so the channel is still told. */
                () -> notificationSender.send(
                        composer.paymentReceived(event.paymentId()), NotificationRecipient.unknown())
        );
    }

    public void onPaymentRejected(PaymentRejected event) {
        /*
         * Only the auto-cancel job notifies. Payment.systemCancel stamps SYSTEM_REVIEWER_ID for
         * exactly this reason: a rejection an admin made by hand, or one a gateway returned, is
         * already a conversation the customer is part of, and an automated "your order was
         * cancelled" on top of it was judged wrong when the job was written.
         */
        if (!Payment.SYSTEM_REVIEWER_ID.equals(event.reviewerId())) {
            return;
        }

        paymentRepository.findById(event.paymentId()).ifPresent(payment ->
                orderRepository.findById(event.orderId()).ifPresent(order -> {
                    NotificationRecipient recipient = userRepository.findById(order.getCustomerId())
                            .map(this::recipientFor)
                            .orElse(NotificationRecipient.unknown());
                    notificationSender.send(
                            composer.orderCancelled(order, payment.getRejectionReason()), recipient);
                }));
    }

    private NotificationRecipient recipientFor(com.pilarestilo.user.domain.model.User user) {
        return NotificationRecipient.of(
                user.getPhone(),
                user.getEmail(),
                user.getNotificationChannelPreference().name());
    }
}
