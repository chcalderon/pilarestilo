package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.payment.domain.events.PaymentRejected;
import com.pilarestilo.payment.domain.events.PaymentSubmitted;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The payment twins had drifted in both directions, and Kafka — the live path — held the worse
 * half of each: it never wrote the in-app record, and it had lost the guard that keeps an
 * automated cancellation notice from firing on a manual or gateway rejection.
 */
@ExtendWith(MockitoExtension.class)
class PaymentNotificationDispatcherTest {

    @Mock NotificationSender notificationSender;
    @Mock InAppNotificationPort inAppNotificationPort;
    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock UpdateOrderStatusUseCase updateOrderStatusUseCase;
    final NotificationComposer composer = new NotificationComposer();

    PaymentNotificationDispatcher dispatcher;

    final UUID paymentId = UUID.randomUUID();
    final UUID customerId = UUID.randomUUID();
    UUID orderId;
    Order order;

    @BeforeEach
    void setUp() {
        dispatcher = new PaymentNotificationDispatcher(notificationSender, composer,
                inAppNotificationPort, orderRepository, userRepository, paymentRepository,
                updateOrderStatusUseCase);

        order = Order.create(
                customerId,
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Blazer",
                        Money.of(BigDecimal.valueOf(175000)), 1)),
                Money.zero(), PaymentMethod.TRANSFER, "NACIONAL", "chilexpress", "Chilexpress",
                "POR_PAGAR", UUID.randomUUID(), "Santa Angela 92", null);
        orderId = order.getId();

        User user = User.create("cliente@example.com", "Cliente", "+56900000000",
                UserRole.CUSTOMER, "hash");
        user.setId(customerId);
        lenient().when(userRepository.findById(customerId)).thenReturn(Optional.of(user));
    }

    /** Never written on the Kafka path, which is the one production runs. */
    @Test
    void confirmed_writesTheInAppRecord() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        dispatcher.onPaymentConfirmed(new PaymentConfirmed(paymentId, orderId, Instant.now()));

        verify(inAppNotificationPort).notifyPaymentReceived(customerId, paymentId);
    }

    @Test
    void confirmed_sendsTheReceipt() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        dispatcher.onPaymentConfirmed(new PaymentConfirmed(paymentId, orderId, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.PAYMENT_RECEIVED.equals(m.templateKey())
                        && paymentId.equals(m.referenceId())),
                any());
    }

    /** A payment without its order is still real; the channel is told, the in-app row is not. */
    @Test
    void confirmed_stillNotifiesWhenTheOrderIsMissing() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        dispatcher.onPaymentConfirmed(new PaymentConfirmed(paymentId, orderId, Instant.now()));

        verify(notificationSender).send(any(NotificationMessage.class), any());
        verifyNoInteractions(inAppNotificationPort);
    }

    /**
     * Payment.systemCancel stamps SYSTEM_REVIEWER_ID so this listener can tell the timeout job
     * apart from a human. Only the job notifies.
     */
    @Test
    void rejected_notifiesWhenTheAutoCancelJobDidIt() {
        Payment payment = org.mockito.Mockito.mock(Payment.class);
        when(payment.getRejectionReason()).thenReturn("Sin comprobante");
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        dispatcher.onPaymentRejected(new PaymentRejected(
                paymentId, orderId, Payment.SYSTEM_REVIEWER_ID, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.ORDER_CANCELLED.equals(m.templateKey())), any());
    }

    /** An admin rejecting by hand is already talking to the customer. */
    @Test
    void rejected_staysQuietForAManualRejection() {
        dispatcher.onPaymentRejected(new PaymentRejected(
                paymentId, orderId, UUID.randomUUID(), Instant.now()));

        verifyNoInteractions(notificationSender);
        verify(paymentRepository, never()).findById(any());
    }

    /** A gateway decline carries no reviewer at all, and is shown to the customer inline. */
    @Test
    void rejected_staysQuietForAGatewayDecline() {
        dispatcher.onPaymentRejected(new PaymentRejected(paymentId, orderId, null, Instant.now()));

        verifyNoInteractions(notificationSender);
    }

    // -----------------------------------------------------------------------------------------
    // Receipt uploaded. PaymentSubmitted had no listener at all, so this whole branch did
    // nothing: the order stayed put and nobody was told a receipt was waiting.
    // -----------------------------------------------------------------------------------------

    private Payment submittedPayment() {
        Payment payment = org.mockito.Mockito.mock(Payment.class);
        lenient().when(payment.getOrderId()).thenReturn(orderId);
        lenient().when(payment.getId()).thenReturn(paymentId);
        lenient().when(payment.getProofReference()).thenReturn("comprobante.pdf");
        return payment;
    }

    private User reviewer(String email, UserRole role) {
        User user = User.create(email, "Revisor", "+56900000000", role, "hash");
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void submitted_movesTheOrderIntoReview() {
        Payment payment = submittedPayment();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        /* No reviewers configured: the order still has to move — that is not their business. */
        when(userRepository.findByRoleIn(any(), any())).thenReturn(new PageImpl<>(List.of()));

        dispatcher.onPaymentSubmitted(new PaymentSubmitted(paymentId, "comprobante.pdf", Instant.now()));

        verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.PAYMENT_UNDER_REVIEW);
    }

    /** Both roles that may approve get told; a receipt nobody looks at is the same as none. */
    @Test
    void submitted_emailsEveryReviewer() {
        Payment payment = submittedPayment();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findByRoleIn(any(), any())).thenReturn(new PageImpl<>(List.of(
                reviewer("admin@pilarestilo.com", UserRole.ADMIN),
                reviewer("finanzas@pilarestilo.com", UserRole.ADMINISTRACION))));

        dispatcher.onPaymentSubmitted(new PaymentSubmitted(paymentId, "comprobante.pdf", Instant.now()));

        verify(notificationSender, org.mockito.Mockito.times(2)).send(
                argThat(m -> NotificationMessage.PAYMENT_PROOF_SUBMITTED.equals(m.templateKey())),
                any());
    }

    @Test
    void submitted_doesNothingForAnUnknownPayment() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        dispatcher.onPaymentSubmitted(new PaymentSubmitted(paymentId, "x.pdf", Instant.now()));

        verifyNoInteractions(updateOrderStatusUseCase);
        verifyNoInteractions(notificationSender);
    }
}
