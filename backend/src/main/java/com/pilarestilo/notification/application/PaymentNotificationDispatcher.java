package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

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

    public PaymentNotificationDispatcher(NotificationSender notificationSender,
                                         NotificationComposer composer,
                                         InAppNotificationPort inAppNotificationPort,
                                         OrderRepository orderRepository,
                                         UserRepository userRepository,
                                         PaymentRepository paymentRepository) {
        this.notificationSender = notificationSender;
        this.composer = composer;
        this.inAppNotificationPort = inAppNotificationPort;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
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
