package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.CustomerReadPort;
import com.pilarestilo.notificationservice.domain.ports.MessagingSettingsPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.domain.ports.OrderReadPort;
import com.pilarestilo.notificationservice.domain.ports.PaymentReadPort;
import com.pilarestilo.notificationservice.events.Events;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * {@code PaymentRegistered} → a customer paying by bank transfer gets the amount, the account, the
 * reference to quote, and how long they have. Ported from the monolith dispatcher.
 */
@Service
public class PaymentRegisteredNotificationDispatcher {

    private final NotificationSender notificationSender;
    private final NotificationComposer composer;
    private final PaymentReadPort paymentReadPort;
    private final OrderReadPort orderReadPort;
    private final CustomerReadPort customerReadPort;
    private final MessagingSettingsPort messagingSettings;

    public PaymentRegisteredNotificationDispatcher(NotificationSender notificationSender,
                                                   NotificationComposer composer,
                                                   PaymentReadPort paymentReadPort,
                                                   OrderReadPort orderReadPort,
                                                   CustomerReadPort customerReadPort,
                                                   MessagingSettingsPort messagingSettings) {
        this.notificationSender = notificationSender;
        this.composer = composer;
        this.paymentReadPort = paymentReadPort;
        this.orderReadPort = orderReadPort;
        this.customerReadPort = customerReadPort;
        this.messagingSettings = messagingSettings;
    }

    public void onPaymentRegistered(Events.PaymentRegistered event) {
        paymentReadPort.findById(event.paymentId()).ifPresent(payment -> {
            if (!payment.isTransfer()) {
                return;
            }
            orderReadPort.findById(event.orderId()).ifPresent(order -> {
                NotificationRecipient recipient = customerReadPort.findById(order.customerId())
                        .map(user -> NotificationRecipient.of(user.phone(), user.email(),
                                user.notificationChannelPreference()))
                        .orElse(NotificationRecipient.unknown());

                // Bank details come from the payment's own snapshot, never from live settings.
                Instant deadline = BankTransferDeadline
                        .forPayment(payment, messagingSettings.current())
                        .orElse(null);

                notificationSender.send(
                        composer.transferInstructions(order, payment, deadline), recipient);
            });
        });
    }
}
