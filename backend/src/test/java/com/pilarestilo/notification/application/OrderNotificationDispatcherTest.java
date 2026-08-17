package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationMessage;
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
import static org.mockito.ArgumentMatchers.argThat;
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
    /** Real, not mocked: the copy it produces is the thing under test in the send assertions. */
    final NotificationComposer composer = new NotificationComposer();

    OrderNotificationDispatcher dispatcher;

    final UUID customerId = UUID.randomUUID();
    /* Assigned by Order.create, which owns its identity — the domain exposes no setter. */
    UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dispatcher = new OrderNotificationDispatcher(
                notificationSender, composer, inAppNotificationPort, userRepository, orderRepository);
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
     * A transfer buyer used to receive only the bank details, never a statement of what they had
     * bought. The Ley 21.398 requires written confirmation of the conditions of the offer, and
     * without it the right of withdrawal runs ninety days instead of ten, so the confirmation now
     * goes out for every payment method. The transfer instructions are a separate message.
     */
    @Test
    void sendsTheConfirmationForATransferToo() {
        givenPaymentMethod(PaymentMethod.TRANSFER);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        dispatcher.onOrderCreated(new OrderCreated(orderId, customerId, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.ORDER_CONFIRMATION.equals(m.templateKey())
                        && orderId.equals(m.referenceId())),
                any());
    }

    @Test
    void sendsTheConfirmationForEveryOtherMethod() {
        givenPaymentMethod(PaymentMethod.WEBPAY);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        dispatcher.onOrderCreated(new OrderCreated(orderId, customerId, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.ORDER_CONFIRMATION.equals(m.templateKey())
                        && orderId.equals(m.referenceId())),
                any(NotificationRecipient.class));
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
        givenPaymentMethod(PaymentMethod.TRANSFER);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        dispatcher.onOrderStatusChanged(new OrderStatusChanged(
                orderId, customerId, OrderStatus.PREPARING_ORDER, OrderStatus.SHIPPED, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.ORDER_SHIPPED.equals(m.templateKey())),
                any(NotificationRecipient.class));
        verify(inAppNotificationPort).notifyOrderShipped(customerId, orderId);
    }

    /**
     * The dispatch job confirms a delivery fifteen days after it was sent, without the customer
     * doing anything. Saying nothing leaves somebody whose parcel never arrived with no prompt.
     */
    @Test
    void tellsTheCustomerWhenTheOrderIsMarkedDelivered() {
        givenPaymentMethod(PaymentMethod.TRANSFER);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        dispatcher.onOrderStatusChanged(new OrderStatusChanged(
                orderId, customerId, OrderStatus.SHIPPED, OrderStatus.DELIVERED, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.ORDER_DELIVERED.equals(m.templateKey())),
                any(NotificationRecipient.class));
        verify(inAppNotificationPort).notifyOrderDelivered(customerId, orderId);
    }

    /** The reference is the whole point of reading the order: a UUID is not quotable. */
    @Test
    void quotesThePublicReferenceRatherThanTheOrderUuid() {
        givenPaymentMethod(PaymentMethod.TRANSFER);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer()));

        dispatcher.onOrderStatusChanged(new OrderStatusChanged(
                orderId, customerId, OrderStatus.PREPARING_ORDER, OrderStatus.SHIPPED, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> m.subject().contains("PE-") && !m.bodyText().contains(orderId.toString())),
                any(NotificationRecipient.class));
    }

    /** An order whose customer row is gone still has a channel worth telling. */
    @Test
    void stillNotifiesWhenTheCustomerRowIsMissing() {
        givenPaymentMethod(PaymentMethod.TRANSFER);
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        dispatcher.onOrderStatusChanged(new OrderStatusChanged(
                orderId, customerId, OrderStatus.CREATED, OrderStatus.PREPARING_ORDER, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.ORDER_PREPARING.equals(m.templateKey())),
                eq(NotificationRecipient.unknown()));
    }

    @Test
    void ignoresStatusesThatCarryNoMessage() {
        dispatcher.onOrderStatusChanged(new OrderStatusChanged(
                orderId, customerId, OrderStatus.CREATED, OrderStatus.PAID, Instant.now()));

        verify(notificationSender, never()).send(any(NotificationMessage.class), any());
    }
}
