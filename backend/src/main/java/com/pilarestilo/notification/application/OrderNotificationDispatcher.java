package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

/**
 * What an order event means for notifications, in one place.
 *
 * <p>There are two listeners for these events — the in-process one and its Kafka twin — and
 * only one of them is alive at a time, because {@code KafkaDomainEventPublisher} is
 * {@code @Primary} when Kafka is on. Duplicating the logic across them meant every fix landed
 * on whichever path the author happened to be running, and the other silently kept the old
 * behaviour. Two such divergences shipped that way: the Kafka twin sent a redundant
 * confirmation for bank transfers, and it wrote no in-app notifications at all — which in
 * production, where Kafka is enabled, meant none were ever written.
 *
 * <p>The listeners are now transport adapters with no behaviour of their own.
 */
@Service
public class OrderNotificationDispatcher {

    private final NotificationSender notificationSender;
    private final InAppNotificationPort inAppNotificationPort;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public OrderNotificationDispatcher(NotificationSender notificationSender,
                                       InAppNotificationPort inAppNotificationPort,
                                       UserRepository userRepository,
                                       OrderRepository orderRepository) {
        this.notificationSender = notificationSender;
        this.inAppNotificationPort = inAppNotificationPort;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    public void onOrderCreated(OrderCreated event) {
        /*
         * A TRANSFER order gets its instructions from PaymentRegisteredNotificationListener,
         * carrying the amount, the bank details and the deadline. The generic confirmation on
         * top of that is a second email that tells the customer nothing they can act on.
         */
        boolean isTransfer = orderRepository.findById(event.orderId())
                .map(order -> order.getPaymentMethod() == PaymentMethod.TRANSFER)
                .orElse(false);

        userRepository.findById(event.customerId()).ifPresent(user -> {
            if (!isTransfer) {
                notificationSender.sendOrderConfirmation(event.orderId(), recipientFor(user));
            }
            inAppNotificationPort.notifyOrderConfirmed(user.getId(), event.orderId());
        });
    }

    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.newStatus() == OrderStatus.PREPARING_ORDER) {
            userRepository.findById(event.customerId()).ifPresentOrElse(
                    user -> {
                        notificationSender.sendOrderPreparing(event.orderId(), recipientFor(user));
                        inAppNotificationPort.notifyOrderPreparing(user.getId(), event.orderId());
                    },
                    /* No user row: still tell the channel, since the order is real. */
                    () -> notificationSender.sendOrderPreparing(event.orderId(), NotificationRecipient.unknown())
            );
            return;
        }

        if (event.newStatus() != OrderStatus.SHIPPED) return;

        userRepository.findById(event.customerId()).ifPresentOrElse(
                user -> {
                    notificationSender.sendOrderShipped(event.orderId(), recipientFor(user));
                    inAppNotificationPort.notifyOrderShipped(user.getId(), event.orderId());
                },
                () -> notificationSender.sendOrderShipped(event.orderId(), NotificationRecipient.unknown())
        );
    }

    private NotificationRecipient recipientFor(com.pilarestilo.user.domain.model.User user) {
        return NotificationRecipient.of(
                user.getPhone(),
                user.getEmail(),
                user.getNotificationChannelPreference().name()
        );
    }
}
