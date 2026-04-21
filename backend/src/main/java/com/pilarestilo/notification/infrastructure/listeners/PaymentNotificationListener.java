package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentNotificationListener {

    private final NotificationSender notificationSender;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public PaymentNotificationListener(NotificationSender notificationSender,
                                       OrderRepository orderRepository,
                                       UserRepository userRepository) {
        this.notificationSender = notificationSender;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @EventListener
    public void onPaymentConfirmed(PaymentConfirmed event) {
        String contact = orderRepository.findById(event.orderId())
                .flatMap(order -> userRepository.findById(order.getCustomerId()))
                .map(user -> user.getEmail())
                .orElse("unknown");
        notificationSender.sendPaymentReceived(event.paymentId(), contact);
    }
}
