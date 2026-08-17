package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationDispatcher.class);

    private final NotificationSender notificationSender;
    private final NotificationComposer composer;
    private final InAppNotificationPort inAppNotificationPort;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public OrderNotificationDispatcher(NotificationSender notificationSender,
                                       NotificationComposer composer,
                                       InAppNotificationPort inAppNotificationPort,
                                       UserRepository userRepository,
                                       OrderRepository orderRepository) {
        this.notificationSender = notificationSender;
        this.composer = composer;
        this.inAppNotificationPort = inAppNotificationPort;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    /** The statuses a customer hears about. Anything else is internal bookkeeping. */
    private static final Set<OrderStatus> NOTIFIED_STATUSES =
            Set.of(OrderStatus.PREPARING_ORDER, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    public void onOrderCreated(OrderCreated event) {
        /*
         * Sent for every payment method, TRANSFER included. It used to be skipped there on the
         * grounds that the transfer-instructions email already covered it — but that message carries
         * only bank details and a deadline, never what was bought or for how much. The Ley 21.398
         * requires written confirmation of the conditions of the offer, and without it the right of
         * withdrawal runs ninety days instead of ten. A transfer buyer now gets both: this one says
         * what they agreed to, the other says how to pay for it.
         */
        Optional<Order> order = orderRepository.findById(event.orderId());

        userRepository.findById(event.customerId()).ifPresent(user -> {
            // No order row means no reference to quote, and a confirmation naming an order the
            // customer cannot identify is worse than none.
            if (order.isPresent()) {
                notificationSender.send(composer.orderConfirmation(order.get()), recipientFor(user));
            }
            inAppNotificationPort.notifyOrderConfirmed(user.getId(), event.orderId());
        });
    }

    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (!NOTIFIED_STATUSES.contains(event.newStatus())) {
            return;
        }
        // The composers quote the order's public reference, so the order has to be read. Without it
        // there is nothing worth sending: a message naming an order the customer cannot identify.
        Optional<Order> order = orderRepository.findById(event.orderId());
        if (order.isEmpty()) {
            log.warn("Order {} reached {} but could not be read; no message sent",
                    event.orderId(), event.newStatus());
            return;
        }
        NotificationMessage message = compose(order.get(), event.newStatus());

        userRepository.findById(event.customerId()).ifPresentOrElse(
                user -> {
                    notificationSender.send(message, recipientFor(user));
                    notifyInApp(user.getId(), event);
                },
                /* No user row: still tell the channel, since the order is real. */
                () -> notificationSender.send(message, NotificationRecipient.unknown())
        );
    }

    private NotificationMessage compose(Order order, OrderStatus status) {
        return switch (status) {
            case PREPARING_ORDER -> composer.orderPreparing(order);
            case SHIPPED -> composer.orderShipped(order);
            case DELIVERED -> composer.orderDelivered(order);
            default -> throw new IllegalStateException("No message defined for status " + status);
        };
    }

    private void notifyInApp(UUID userId, OrderStatusChanged event) {
        switch (event.newStatus()) {
            case PREPARING_ORDER -> inAppNotificationPort.notifyOrderPreparing(userId, event.orderId());
            case SHIPPED -> inAppNotificationPort.notifyOrderShipped(userId, event.orderId());
            case DELIVERED -> inAppNotificationPort.notifyOrderDelivered(userId, event.orderId());
            default -> { }
        }
    }

    private NotificationRecipient recipientFor(com.pilarestilo.user.domain.model.User user) {
        return NotificationRecipient.of(
                user.getPhone(),
                user.getEmail(),
                user.getNotificationChannelPreference().name()
        );
    }
}
