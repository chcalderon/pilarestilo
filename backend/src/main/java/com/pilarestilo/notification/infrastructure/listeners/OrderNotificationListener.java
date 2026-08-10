package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
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
    private final InAppNotificationPort inAppNotificationPort;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public OrderNotificationListener(NotificationSender notificationSender,
                                      InAppNotificationPort inAppNotificationPort,
                                      UserRepository userRepository,
                                      OrderRepository orderRepository) {
        this.notificationSender = notificationSender;
        this.inAppNotificationPort = inAppNotificationPort;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @EventListener
    public void onOrderCreated(OrderCreated event) {
        // A TRANSFER order gets the instructions message from PaymentRegisteredNotificationListener,
        // which carries the amount, bank details and deadline. Sending the generic confirmation too
        // would mean two emails, one of them useless.
        boolean isTransfer = orderRepository.findById(event.orderId())
                .map(order -> order.getPaymentMethod() == PaymentMethod.TRANSFER)
                .orElse(false);

        userRepository.findById(event.customerId()).ifPresent(user -> {
            if (!isTransfer) {
                notificationSender.sendOrderConfirmation(
                event.orderId(),
                NotificationRecipient.of(user.getPhone(), user.getEmail(),
                    user.getNotificationChannelPreference().name())
                );
            }
            inAppNotificationPort.notifyOrderConfirmed(user.getId(), event.orderId());
        });
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.newStatus() == OrderStatus.PREPARING_ORDER) {
            userRepository.findById(event.customerId())
                    .ifPresentOrElse(
                            user -> {
                                notificationSender.sendOrderPreparing(
                                        event.orderId(),
                                        NotificationRecipient.of(
                                                user.getPhone(),
                                                user.getEmail(),
                                                user.getNotificationChannelPreference().name()
                                        )
                                );
                                inAppNotificationPort.notifyOrderPreparing(user.getId(), event.orderId());
                            },
                            () -> notificationSender.sendOrderPreparing(event.orderId(), NotificationRecipient.unknown())
                    );
            return;
        }
        if (event.newStatus() != OrderStatus.SHIPPED) return;

        userRepository.findById(event.customerId())
                .ifPresentOrElse(
                        user -> {
                            notificationSender.sendOrderShipped(
                                    event.orderId(),
                                    NotificationRecipient.of(
                                            user.getPhone(),
                                            user.getEmail(),
                                            user.getNotificationChannelPreference().name()
                                    )
                            );
                            inAppNotificationPort.notifyOrderShipped(user.getId(), event.orderId());
                        },
                        () -> notificationSender.sendOrderShipped(event.orderId(), NotificationRecipient.unknown())
                );
    }
}
