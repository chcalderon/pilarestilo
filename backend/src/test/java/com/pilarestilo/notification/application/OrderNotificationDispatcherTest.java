package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * These cases exist because the Kafka twin of the order listener had drifted from the
 * in-process one, and Kafka is the path production runs. Both listeners are now thin
 * adapters over this class, so covering it covers both transports at once.
 */
@ExtendWith(MockitoExtension.class)
class OrderNotificationDispatcherTest {

    @Mock NotificationSender notificationSender;
    @Mock InAppNotificationPort inAppNotificationPort;
    @Mock UserRepository userRepository;
    @Mock OrderRepository orderRepository;

    OrderNotificationDispatcher dispatcher;

    final UUID customerId = UUID.randomUUID();
    /* Assigned by Order.create, which owns its identity — the domain exposes no setter. */
    UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dispatcher = new OrderNotificationDispatcher(
                notificationSender, inAppNotificationPort, userRepository, orderRepository);
    }

    /* Real domain objects rather than mocks: these classes are plain and constructing them
     * keeps the test honest about what the dispatcher actually reads. */
    private User customer() {
        User user = User.create("cliente@example.com", "Cliente", "+56900000000",
                UserRole.CUSTOMER, "hash");
        user.setId(customerId);
        return user;
    }

    private void givenPaymentMethod(PaymentMethod method) {
        Order order = Order.create(
                customerId,
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Blazer",
                        Money.of(BigDecimal.valueOf(175000)), 1)),
                Money.zero(),
                method,
                "NACIONAL",
                "chilexpress",
                "Chilexpress",
                "POR_PAGAR",
                UUID.randomUUID(),
                "Santa Angela 92",
                null
        );
        orderId = order.getId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
    }

    /**
     * The transfer instructions carry the amount, bank details and deadline. A generic
     * confirmation alongside them is a second email the customer cannot act on.
     */
    @Test
    void doesNotSendTheGenericConfirmationForATransfer() {
        givenPaymentMethod(PaymentMethod.TRANSFER);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        dispatcher.onOrderCreated(new OrderCreated(orderId, customerId, Instant.now()));

        verify(notificationSender, never()).sendOrderConfirmation(any(), any());
    }

    @Test
    void sendsTheConfirmationForEveryOtherMethod() {
        givenPaymentMethod(PaymentMethod.WEBPAY);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        dispatcher.onOrderCreated(new OrderCreated(orderId, customerId, Instant.now()));

        verify(notificationSender).sendOrderConfirmation(eq(orderId), any(NotificationRecipient.class));
    }

    /**
     * The in-app record is written for a transfer too — it is the only trace the customer has
     * inside the site. The Kafka twin wrote none at all, so in production there were none.
     */
    @Test
    void alwaysWritesTheInAppNotification() {
        givenPaymentMethod(PaymentMethod.TRANSFER);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        dispatcher.onOrderCreated(new OrderCreated(orderId, customerId, Instant.now()));

        verify(inAppNotificationPort).notifyOrderConfirmed(customerId, orderId);
    }

    @Test
    void writesTheInAppNotificationWhenAnOrderShips() {
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        dispatcher.onOrderStatusChanged(new OrderStatusChanged(
                orderId, customerId, OrderStatus.PREPARING_ORDER, OrderStatus.SHIPPED, Instant.now()));

        verify(notificationSender).sendOrderShipped(eq(orderId), any(NotificationRecipient.class));
        verify(inAppNotificationPort).notifyOrderShipped(customerId, orderId);
    }

    /** An order whose customer row is gone still has a channel worth telling. */
    @Test
    void stillNotifiesWhenTheCustomerRowIsMissing() {
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        dispatcher.onOrderStatusChanged(new OrderStatusChanged(
                orderId, customerId, OrderStatus.CREATED, OrderStatus.PREPARING_ORDER, Instant.now()));

        verify(notificationSender).sendOrderPreparing(orderId, NotificationRecipient.unknown());
    }

    @Test
    void ignoresStatusesThatCarryNoMessage() {
        dispatcher.onOrderStatusChanged(new OrderStatusChanged(
                orderId, customerId, OrderStatus.CREATED, OrderStatus.PAID, Instant.now()));

        verify(notificationSender, never()).sendOrderShipped(any(), any());
        verify(notificationSender, never()).sendOrderPreparing(any(), any());
    }
}
