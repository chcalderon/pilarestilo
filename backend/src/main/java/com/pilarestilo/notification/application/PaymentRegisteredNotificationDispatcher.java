package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.application.BankTransferDeadline;
import com.pilarestilo.payment.domain.events.PaymentRegistered;
import com.pilarestilo.payment.domain.ports.PaymentRepository;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * What {@code PaymentRegistered} means for notifications: a customer paying by bank transfer gets
 * the amount, the account, the reference to quote, and how long they have.
 *
 * <p>Listens to {@code PaymentRegistered} rather than {@code OrderCreated} because at OrderCreated
 * time the payment row does not exist yet, and both the bank details and the deadline come from it.
 *
 * <p>The behaviour lives here rather than in either listener: {@code KafkaDomainEventPublisher} is
 * {@code @Primary} when Kafka is on, so only the Kafka transport runs in production, and four
 * defects in this codebase came from an in-process listener drifting from its twin. Both transports
 * call this and hold nothing of their own.
 */
@Service
public class PaymentRegisteredNotificationDispatcher {

    private final NotificationSender notificationSender;
    private final NotificationComposer notificationComposer;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SystemSettingsRepository systemSettingsRepository;

    public PaymentRegisteredNotificationDispatcher(NotificationSender notificationSender,
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

    /**
     * {@code @Transactional(readOnly = true)} because a Kafka listener runs outside any request and
     * open-in-view is disabled, so the repository reads below would otherwise have no session.
     */
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

                // Bank details come from the payment's own snapshot, never from live settings: if
                // an admin changes the account tomorrow, this customer must still see the one they
                // were told to pay into.
                Instant deadline = BankTransferDeadline
                        .forPayment(payment, systemSettingsRepository.get())
                        .orElse(null);

                notificationSender.send(
                        notificationComposer.transferInstructions(order, payment, deadline),
                        recipient);
            });
        });
    }
}
