package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.application.NotificationComposer;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.application.BankTransferDeadline;
import com.pilarestilo.payment.domain.events.PaymentRegistered;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Sends the customer what they need in order to pay by bank transfer: the amount, the account, the
 * order reference to quote, and how long they have.
 *
 * <p>Listens to {@code PaymentRegistered} rather than {@code OrderCreated}. At OrderCreated time
 * the payment row does not exist yet — it is written by a sibling listener on the same event, with
 * no ordering guarantee — and both the bank details and the deadline come from that row.
 */
@Component
public class PaymentRegisteredNotificationListener {

    private final NotificationSender notificationSender;
    private final NotificationComposer notificationComposer;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SystemSettingsRepository systemSettingsRepository;

    public PaymentRegisteredNotificationListener(NotificationSender notificationSender,
                                                 NotificationComposer notificationComposer,
                                                 PaymentRepository paymentRepository,
                                                 OrderRepository orderRepository,
                                                 UserRepository userRepository,
                                                 SystemSettingsRepository systemSettingsRepository) {
        this.notificationSender = notificationSender;
        this.notificationComposer = notificationComposer;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.systemSettingsRepository = systemSettingsRepository;
    }

    @EventListener
    @Transactional(readOnly = true)
    public void onPaymentRegistered(PaymentRegistered event) {
        paymentRepository.findById(event.paymentId()).ifPresent(payment -> {
            if (payment.getMethod() != PaymentMethod.TRANSFER) {
                return;
            }
            orderRepository.findById(event.orderId()).ifPresent(order -> {
                NotificationRecipient recipient = userRepository.findById(order.getCustomerId())
                        .map(user -> NotificationRecipient.of(user.getPhone(), user.getEmail(),
                                user.getNotificationChannelPreference().name()))
                        .orElse(NotificationRecipient.unknown());

                Instant deadline = BankTransferDeadline
                        .forPayment(payment, systemSettingsRepository.get())
                        .orElse(null);

                // Bank details come from the payment's own snapshot, never from live settings: if
                // an admin changes the account tomorrow, this customer must still see the one they
                // were told to pay into.
                notificationSender.send(
                        notificationComposer.transferInstructions(order, payment, deadline),
                        recipient);
            });
        });
    }
}
