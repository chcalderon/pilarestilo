package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.CustomerReadPort;
import com.pilarestilo.notificationservice.domain.ports.InAppNotificationPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.domain.ports.OrderReadPort;
import com.pilarestilo.notificationservice.domain.ports.PaymentReadPort;
import com.pilarestilo.notificationservice.domain.ports.PaymentReviewerReadPort;
import com.pilarestilo.notificationservice.domain.view.CustomerView;
import com.pilarestilo.notificationservice.events.Events;
import com.pilarestilo.notificationservice.events.PaymentConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * What a payment event means for notifications. Ported from the monolith. The order's move to
 * PAYMENT_UNDER_REVIEW on {@code PaymentSubmitted} is <b>not</b> here — it is order-domain
 * behaviour and stays in the monolith ({@code MarkOrderUnderReviewOnPaymentSubmittedHandler}).
 */
@Service
public class PaymentNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationDispatcher.class);

    /** Whoever may approve a payment. */
    private static final List<String> PAYMENT_REVIEWERS = List.of("ADMIN", "ADMINISTRACION");

    private final NotificationSender notificationSender;
    private final NotificationComposer composer;
    private final InAppNotificationPort inAppNotificationPort;
    private final OrderReadPort orderReadPort;
    private final CustomerReadPort customerReadPort;
    private final PaymentReadPort paymentReadPort;
    private final PaymentReviewerReadPort paymentReviewerReadPort;

    public PaymentNotificationDispatcher(NotificationSender notificationSender,
                                         NotificationComposer composer,
                                         InAppNotificationPort inAppNotificationPort,
                                         OrderReadPort orderReadPort,
                                         CustomerReadPort customerReadPort,
                                         PaymentReadPort paymentReadPort,
                                         PaymentReviewerReadPort paymentReviewerReadPort) {
        this.notificationSender = notificationSender;
        this.composer = composer;
        this.inAppNotificationPort = inAppNotificationPort;
        this.orderReadPort = orderReadPort;
        this.customerReadPort = customerReadPort;
        this.paymentReadPort = paymentReadPort;
        this.paymentReviewerReadPort = paymentReviewerReadPort;
    }

    /** A customer uploaded their transfer receipt: every active reviewer is emailed. */
    public void onPaymentSubmitted(Events.PaymentSubmitted event) {
        paymentReadPort.findById(event.paymentId()).ifPresent(payment ->
                orderReadPort.findById(payment.orderId()).ifPresent(order -> {
                    String buyerName = customerReadPort.findById(order.customerId())
                            .map(CustomerView::fullName)
                            .orElse("Cliente");
                    notifyReviewers(composer.paymentProofSubmitted(order, payment, buyerName));
                }));
    }

    private void notifyReviewers(NotificationMessage message) {
        paymentReviewerReadPort.findActiveByRoles(PAYMENT_REVIEWERS).stream()
                .filter(reviewer -> reviewer.email() != null && !reviewer.email().isBlank())
                .forEach(reviewer -> notificationSender.send(
                        message, NotificationRecipient.of(null, reviewer.email(), "EMAIL")));
    }

    public void onPaymentConfirmed(Events.PaymentConfirmed event) {
        orderReadPort.findById(event.orderId()).ifPresentOrElse(
                order -> paymentReadPort.findById(event.paymentId()).ifPresentOrElse(
                        payment -> {
                            NotificationRecipient recipient = customerReadPort.findById(order.customerId())
                                    .map(this::recipientFor)
                                    .orElse(NotificationRecipient.unknown());
                            notificationSender.send(composer.paymentReceived(order, payment), recipient);
                            inAppNotificationPort.notifyPaymentReceived(order.customerId(), event.paymentId());
                        },
                        () -> log.warn("Payment {} confirmed but not readable; no message sent",
                                event.paymentId())),
                () -> log.warn("Order {} for payment {} not readable; no message sent",
                        event.orderId(), event.paymentId()));
    }

    public void onPaymentRejected(Events.PaymentRejected event) {
        /*
         * Only the auto-cancel job notifies. A rejection an admin made by hand, or one a gateway
         * returned, is already a conversation the customer is part of.
         */
        if (!PaymentConstants.SYSTEM_REVIEWER_ID.equals(event.reviewerId())) {
            return;
        }

        paymentReadPort.findById(event.paymentId()).ifPresent(payment ->
                orderReadPort.findById(event.orderId()).ifPresent(order -> {
                    NotificationRecipient recipient = customerReadPort.findById(order.customerId())
                            .map(this::recipientFor)
                            .orElse(NotificationRecipient.unknown());
                    notificationSender.send(
                            composer.orderCancelled(order, payment.rejectionReason()), recipient);
                }));
    }

    private NotificationRecipient recipientFor(CustomerView user) {
        return NotificationRecipient.of(user.phone(), user.email(), user.notificationChannelPreference());
    }
}
