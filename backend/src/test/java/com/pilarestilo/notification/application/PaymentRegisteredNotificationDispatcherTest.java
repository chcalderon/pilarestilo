package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentRegistered;
import com.pilarestilo.payment.domain.model.Payment;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PaymentRegistered → the customer gets what they need to pay by transfer: amount, account,
 * reference, and how long they have. The behaviour used to live in the listener; it now lives here
 * so the in-process and Kafka transports cannot drift.
 */
@ExtendWith(MockitoExtension.class)
class PaymentRegisteredNotificationDispatcherTest {

    @Mock NotificationSender notificationSender;
    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock SystemSettingsRepository systemSettingsRepository;
    final NotificationComposer composer = new NotificationComposer();

    PaymentRegisteredNotificationDispatcher dispatcher;

    final UUID paymentId = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();
    final UUID customerId = UUID.randomUUID();
    Order order;

    @BeforeEach
    void setUp() {
        dispatcher = new PaymentRegisteredNotificationDispatcher(notificationSender, composer,
                paymentRepository, orderRepository, userRepository, systemSettingsRepository);

        order = Order.create(
                customerId,
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Blazer",
                        Money.of(BigDecimal.valueOf(175000)), 1)),
                Money.zero(), PaymentMethod.TRANSFER, "NACIONAL", "chilexpress", "Chilexpress",
                "POR_PAGAR", UUID.randomUUID(), "Santa Angela 92", null);

        SystemSettings settings = mock(SystemSettings.class);
        lenient().when(settings.isBankTransferAutoCancelEnabled()).thenReturn(false);
        lenient().when(systemSettingsRepository.get()).thenReturn(settings);
    }

    private Payment transferPayment() {
        Payment payment = mock(Payment.class);
        lenient().when(payment.getMethod()).thenReturn(PaymentMethod.TRANSFER);
        lenient().when(payment.getCreatedAt()).thenReturn(Instant.now());
        lenient().when(payment.getTransferAccountHolderName()).thenReturn("Pilar Perez");
        lenient().when(payment.getTransferBankName()).thenReturn("Banco Estado");
        lenient().when(payment.getTransferAccountType()).thenReturn("Cuenta Corriente");
        lenient().when(payment.getTransferAccountNumber()).thenReturn("00012345678");
        lenient().when(payment.getTransferAccountEmail()).thenReturn("pagos@pilarestilo.com");
        return payment;
    }

    private User customer() {
        User user = User.create("cliente@example.com", "Cliente", "+56912345678",
                UserRole.CUSTOMER, "hash");
        user.setId(customerId);
        return user;
    }

    @Test
    void sends_transfer_instructions_for_a_transfer_payment() {
        Payment payment = transferPayment();
        User user = customer();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(user));

        dispatcher.onPaymentRegistered(new PaymentRegistered(paymentId, orderId, Instant.now()));

        verify(notificationSender).send(
                argThat(m -> NotificationMessage.TRANSFER_INSTRUCTIONS.equals(m.templateKey())),
                argThat(r -> "cliente@example.com".equals(r.email())));
    }

    @Test
    void stays_quiet_for_a_non_transfer_payment() {
        Payment payment = mock(Payment.class);
        when(payment.getMethod()).thenReturn(PaymentMethod.WEBPAY);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        dispatcher.onPaymentRegistered(new PaymentRegistered(paymentId, orderId, Instant.now()));

        verifyNoInteractions(notificationSender);
    }

    @Test
    void does_nothing_for_an_unknown_payment() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        dispatcher.onPaymentRegistered(new PaymentRegistered(paymentId, orderId, Instant.now()));

        verifyNoInteractions(notificationSender);
    }

    @Test
    void sends_to_an_unknown_recipient_when_the_customer_row_is_missing() {
        Payment payment = transferPayment();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        dispatcher.onPaymentRegistered(new PaymentRegistered(paymentId, orderId, Instant.now()));

        verify(notificationSender).send(any(NotificationMessage.class),
                argThat(r -> r.email() == null && r.phone() == null));
    }
}
