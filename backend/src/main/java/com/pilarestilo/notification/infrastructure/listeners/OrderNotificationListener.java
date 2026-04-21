package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderNotificationListener {

    private final NotificationSender notificationSender;
    private final UserRepository userRepository;

    public OrderNotificationListener(NotificationSender notificationSender,
                                      UserRepository userRepository) {
        this.notificationSender = notificationSender;
        this.userRepository = userRepository;
    }

    @EventListener
    public void onOrderCreated(OrderCreated event) {
        userRepository.findById(event.customerId())
                .ifPresent(user -> notificationSender.sendOrderConfirmation(
                        event.orderId(),
                        NotificationRecipient.of(user.getPhone(), user.getEmail())
                ));
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.newStatus() == OrderStatus.SHIPPED) {
            userRepository.findById(event.customerId())
                    .ifPresentOrElse(
                            user -> notificationSender.sendOrderShipped(
                                    event.orderId(),
                                    NotificationRecipient.of(user.getPhone(), user.getEmail())
                            ),
                            () -> notificationSender.sendOrderShipped(event.orderId(), NotificationRecipient.unknown())
                    );
        }
    }
}
