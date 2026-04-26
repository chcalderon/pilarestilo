package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentNotificationListener {

    private final NotificationSender notificationSender;
    private final InAppNotificationPort inAppNotificationPort;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public PaymentNotificationListener(NotificationSender notificationSender,
                                       InAppNotificationPort inAppNotificationPort,
                                       OrderRepository orderRepository,
                                       UserRepository userRepository) {
        this.notificationSender = notificationSender;
        this.inAppNotificationPort = inAppNotificationPort;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @EventListener
    public void onPaymentConfirmed(PaymentConfirmed event) {
        NotificationRecipient recipient = orderRepository.findById(event.orderId())
            .flatMap(order -> userRepository.findById(order.getCustomerId()))
            .map(user -> NotificationRecipient.of(user.getPhone(), user.getEmail(),
                user.getNotificationChannelPreference().name()))
            .orElse(NotificationRecipient.unknown());
        notificationSender.sendPaymentReceived(event.paymentId(), recipient);

        orderRepository.findById(event.orderId())
            .map(order -> order.getCustomerId())
            .ifPresent(userId -> inAppNotificationPort.notifyPaymentReceived(userId, event.paymentId()));
    }
}
