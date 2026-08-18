package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.returns.domain.enums.RefundMethod;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.domain.events.RefundRegistered;
import com.pilarestilo.returns.domain.events.ReturnApproved;
import com.pilarestilo.returns.domain.events.ReturnRequested;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.data.domain.PageImpl;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A return with no word back is what turns a customer exercising a right into a complaint, so what
 * matters here is that each of the three moments actually reaches her — and that the message says
 * the two things the law obliges: the deadline, and who pays the return trip.
 */
@ExtendWith(MockitoExtension.class)
class ReturnNotificationDispatcherTest {

    @Mock ReturnRequestRepository returnRequestRepository;
    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationSender notificationSender;
    final NotificationComposer composer = new NotificationComposer();

    ReturnNotificationDispatcher dispatcher;

    final UUID customerId = UUID.randomUUID();
    Order order;
    UUID orderId;
    ReturnRequest request;
    UUID returnId;

    @BeforeEach
    void setUp() {
        dispatcher = new ReturnNotificationDispatcher(returnRequestRepository, orderRepository,
                userRepository, notificationSender, composer);

        order = Order.create(
                customerId,
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Blazer",
                        Money.of(BigDecimal.valueOf(45990)), 1)),
                Money.zero(), PaymentMethod.TRANSFER, "NACIONAL", "starken", "Starken",
                "POR_PAGAR", UUID.randomUUID(), "Santa Angela 92", null);
        orderId = order.getId();

        request = ReturnRequest.open(orderId, ReturnKind.RETRACTO, "No me quedó como esperaba",
                customerId);
        returnId = request.getId();

        User user = User.create("cliente@example.com", "Cliente", "+56900000000",
                UserRole.CUSTOMER, "hash");
        user.setId(customerId);
        lenient().when(userRepository.findById(customerId)).thenReturn(Optional.of(user));
        lenient().when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        lenient().when(returnRequestRepository.findById(returnId)).thenReturn(Optional.of(request));

        User handler = User.create("admin@example.com", "Administracion", null,
                UserRole.ADMINISTRACION, "hash");
        handler.setId(UUID.randomUUID());
        lenient().when(userRepository.findByRoleIn(any(), any()))
                .thenReturn(new PageImpl<>(List.of(handler)));
    }

    @Test
    void requested_tellsHerTheDeadlineTheLawSets() {
        dispatcher.onReturnRequested(new ReturnRequested(returnId, orderId,
                ReturnKind.RETRACTO.name(), Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.RETURN_REQUESTED.equals(m.templateKey())
                        && m.data().get("deadlineAt") != null
                        && m.bodyText().contains("el envío de vuelta lo pagamos nosotros")),
                any());
    }

    /** The customer hearing back is half of it; a return nobody in the shop sees is the other. */
    @Test
    void requested_alsoAlertsWhoeverHandlesReturns() {
        dispatcher.onReturnRequested(new ReturnRequested(returnId, orderId,
                ReturnKind.RETRACTO.name(), Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.RETURN_REQUESTED_STAFF.equals(m.templateKey())
                        && m.bodyText().contains("no se rechaza")),
                argThat(r -> "admin@example.com".equals(r.email())));
    }

    @Test
    void approved_saysWhoPaysTheTripBack() {
        request.approve();

        dispatcher.onReturnApproved(new ReturnApproved(returnId, orderId, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.RETURN_APPROVED.equals(m.templateKey())
                        && m.bodyText().contains("nuestro, no tuyo")),
                any());
    }

    @Test
    void refunded_carriesTheAmountAndTheReferenceSheWillLookFor() {
        request.approve();
        request.receive();
        request.registerRefund(Money.of(BigDecimal.valueOf(45990)), RefundMethod.TRANSFERENCIA,
                "OP-99881", null);

        dispatcher.onRefundRegistered(new RefundRegistered(returnId, orderId, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.REFUND_REGISTERED.equals(m.templateKey())
                        && m.bodyText().contains("45990")
                        && m.bodyText().contains("OP-99881")),
                any());
    }

    /** Nothing to say about a return that cannot be read back; saying it wrong is worse. */
    @Test
    void staysQuietWhenTheReturnCannotBeReadBack() {
        when(returnRequestRepository.findById(returnId)).thenReturn(Optional.empty());

        dispatcher.onReturnRequested(new ReturnRequested(returnId, orderId,
                ReturnKind.RETRACTO.name(), Instant.now()));

        verify(notificationSender, never()).send(any(), any());
    }

    /** The return is real even when the user row is gone, so the channel still hears about it. */
    @Test
    void sendsWithoutARecipientWhenTheUserIsMissing() {
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        dispatcher.onReturnRequested(new ReturnRequested(returnId, orderId,
                ReturnKind.RETRACTO.name(), Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.RETURN_REQUESTED.equals(m.templateKey())),
                argThat(r -> r.email() == null && r.phone() == null));
    }
}
