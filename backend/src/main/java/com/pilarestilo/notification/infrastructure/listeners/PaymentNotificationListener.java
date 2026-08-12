package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.application.NotificationComposer;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentNotificationListener {

    private final NotificationSender notificationSender;
    private final NotificationComposer notificationComposer;
    private final InAppNotificationPort inAppNotificationPort;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    public PaymentNotificationListener(NotificationSender notificationSender,
                                       NotificationComposer notificationComposer,
                                       InAppNotificationPort inAppNotificationPort,
                                       OrderRepository orderRepository,
                                       UserRepository userRepository,
                                       PaymentRepository paymentRepository) {
        this.notificationSender = notificationSender;
        this.notificationComposer = notificationComposer;
        this.inAppNotificationPort = inAppNotificationPort;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
    }

    @EventListener
    public void onPaymentConfirmed(PaymentConfirmed event) {
        NotificationRecipient recipient = orderRepository.findById(event.orderId())
            .flatMap(order -> userRepository.findById(order.getCustomerId()))
            .map(user -> NotificationRecipient.of(user.getPhone(), user.getEmail(),
                user.getNotificationChannelPreference().name()))
            .orElse(NotificationRecipient.unknown());
        notificationSender.send(notificationComposer.paymentReceived(event.paymentId()), recipient);

        orderRepository.findById(event.orderId())
            .map(order -> order.getCustomerId())
            .ifPresent(userId -> inAppNotificationPort.notifyPaymentReceived(userId, event.paymentId()));
    }

    @EventListener
    public void onPaymentRejected(PaymentRejected event) {
        if (!Payment.SYSTEM_REVIEWER_ID.equals(event.reviewerId())) {
            return;
        }
        paymentRepository.findById(event.paymentId()).ifPresent(payment ->
            orderRepository.findById(event.orderId()).ifPresent(order -> {
                NotificationRecipient recipient = userRepository.findById(order.getCustomerId())
                    .map(user -> NotificationRecipient.of(user.getPhone(), user.getEmail(),
                        user.getNotificationChannelPreference().name()))
                    .orElse(NotificationRecipient.unknown());
                // Composed centrally now. The old sendOrderCancelled had an empty default on the
                // port, so only the LOG adapter did anything -- every other provider silently
                // dropped this, and the customer was never told their order had been cancelled.
                notificationSender.send(
                    notificationComposer.orderCancelled(order, payment.getRejectionReason()),
                    recipient);
            }));
    }
}
