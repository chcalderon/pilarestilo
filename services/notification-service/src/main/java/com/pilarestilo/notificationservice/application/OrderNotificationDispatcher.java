package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.CustomerReadPort;
import com.pilarestilo.notificationservice.domain.ports.InAppNotificationPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.domain.ports.OrderReadPort;
import com.pilarestilo.notificationservice.domain.view.CustomerView;
import com.pilarestilo.notificationservice.domain.view.OrderView;
import com.pilarestilo.notificationservice.events.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What an order event means for notifications, in one place. Ported from the monolith — the
 * transports (Kafka listeners) hold nothing of their own.
 */
@Service
public class OrderNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationDispatcher.class);

    /** The statuses a customer hears about. Anything else is internal bookkeeping. */
    private static final Set<String> NOTIFIED_STATUSES =
            Set.of("PREPARING_ORDER", "SHIPPED", "DELIVERED");

    private final NotificationSender notificationSender;
    private final NotificationComposer composer;
    private final InAppNotificationPort inAppNotificationPort;
    private final CustomerReadPort customerReadPort;
    private final OrderReadPort orderReadPort;

    public OrderNotificationDispatcher(NotificationSender notificationSender,
                                       NotificationComposer composer,
                                       InAppNotificationPort inAppNotificationPort,
                                       CustomerReadPort customerReadPort,
                                       OrderReadPort orderReadPort) {
        this.notificationSender = notificationSender;
        this.composer = composer;
        this.inAppNotificationPort = inAppNotificationPort;
        this.customerReadPort = customerReadPort;
        this.orderReadPort = orderReadPort;
    }

    public void onOrderCreated(Events.OrderCreated event) {
        Optional<OrderView> order = orderReadPort.findById(event.orderId());

        customerReadPort.findById(event.customerId()).ifPresent(user -> {
            if (order.isPresent()) {
                notificationSender.send(composer.orderConfirmation(order.get()), recipientFor(user));
            }
            inAppNotificationPort.notifyOrderConfirmed(user.id(), event.orderId());
        });
    }

    public void onOrderStatusChanged(Events.OrderStatusChanged event) {
        if (!NOTIFIED_STATUSES.contains(event.newStatus())) {
            return;
        }
        Optional<OrderView> order = orderReadPort.findById(event.orderId());
        if (order.isEmpty()) {
            log.warn("Order {} reached {} but could not be read; no message sent",
                    event.orderId(), event.newStatus());
            return;
        }
        Optional<NotificationMessage> message = compose(order.get(), event.newStatus());

        customerReadPort.findById(event.customerId()).ifPresentOrElse(
                user -> {
                    message.ifPresent(m -> notificationSender.send(m, recipientFor(user)));
                    notifyInApp(user.id(), event);
                },
                () -> message.ifPresent(m -> notificationSender.send(m, NotificationRecipient.unknown())));
    }

    /**
     * Empty for {@code PREPARING_ORDER} — that beat has no email of its own; "pago confirmado /
     * estamos preparando tu pedido" already went out from {@link PaymentNotificationDispatcher}
     * on {@code PaymentConfirmed}, the same moment. The in-app bell entry still fires (see
     * {@link #notifyInApp}).
     */
    private Optional<NotificationMessage> compose(OrderView order, String status) {
        return switch (status) {
            case "PREPARING_ORDER" -> Optional.empty();
            case "SHIPPED" -> Optional.of(composer.orderShipped(order));
            case "DELIVERED" -> Optional.of(composer.orderDelivered(order));
            default -> throw new IllegalStateException("No message defined for status " + status);
        };
    }

    private void notifyInApp(UUID userId, Events.OrderStatusChanged event) {
        switch (event.newStatus()) {
            case "PREPARING_ORDER" -> inAppNotificationPort.notifyOrderPreparing(userId, event.orderId());
            case "SHIPPED" -> inAppNotificationPort.notifyOrderShipped(userId, event.orderId());
            case "DELIVERED" -> inAppNotificationPort.notifyOrderDelivered(userId, event.orderId());
            default -> {
                // Every other status carries no in-app notification.
            }
        }
    }

    private NotificationRecipient recipientFor(CustomerView user) {
        return NotificationRecipient.of(user.phone(), user.email(), user.notificationChannelPreference());
    }
}
