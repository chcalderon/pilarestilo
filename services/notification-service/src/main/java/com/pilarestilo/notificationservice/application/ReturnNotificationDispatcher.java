package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.CustomerReadPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.domain.ports.OrderReadPort;
import com.pilarestilo.notificationservice.domain.ports.PaymentReviewerReadPort;
import com.pilarestilo.notificationservice.domain.ports.ReturnReadPort;
import com.pilarestilo.notificationservice.domain.view.CustomerView;
import com.pilarestilo.notificationservice.domain.view.OrderView;
import com.pilarestilo.notificationservice.domain.view.ReturnView;
import com.pilarestilo.notificationservice.events.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * What the customer hears while her sale is being undone: it is on record, it was approved, the
 * money went back. Ported from the monolith. Silence is the failure mode that matters here.
 */
@Service
public class ReturnNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReturnNotificationDispatcher.class);

    /** Whoever may resolve a return. */
    private static final List<String> RETURN_HANDLERS = List.of("ADMIN", "ADMINISTRACION");

    private final ReturnReadPort returnReadPort;
    private final OrderReadPort orderReadPort;
    private final CustomerReadPort customerReadPort;
    private final PaymentReviewerReadPort reviewerReadPort;
    private final NotificationSender notificationSender;
    private final NotificationComposer composer;

    public ReturnNotificationDispatcher(ReturnReadPort returnReadPort,
                                        OrderReadPort orderReadPort,
                                        CustomerReadPort customerReadPort,
                                        PaymentReviewerReadPort reviewerReadPort,
                                        NotificationSender notificationSender,
                                        NotificationComposer composer) {
        this.returnReadPort = returnReadPort;
        this.orderReadPort = orderReadPort;
        this.customerReadPort = customerReadPort;
        this.reviewerReadPort = reviewerReadPort;
        this.notificationSender = notificationSender;
        this.composer = composer;
    }

    public void onReturnRequested(Events.ReturnRequested event) {
        send(event.returnId(), event.orderId(), composer::returnRequested);
        notifyHandlers(event.returnId(), event.orderId());
    }

    public void onReturnApproved(Events.ReturnApproved event) {
        send(event.returnId(), event.orderId(), composer::returnApproved);
    }

    public void onRefundRegistered(Events.RefundRegistered event) {
        send(event.returnId(), event.orderId(), composer::refundRegistered);
    }

    private void notifyHandlers(UUID returnId, UUID orderId) {
        Optional<ReturnView> request = returnReadPort.findById(returnId);
        Optional<OrderView> order = orderReadPort.findById(orderId);
        if (request.isEmpty() || order.isEmpty()) {
            return;
        }
        String buyerName = customerReadPort.findById(order.get().customerId())
                .map(CustomerView::fullName)
                .orElse("Cliente");
        NotificationMessage message =
                composer.returnRequestedForStaff(order.get(), request.get(), buyerName);
        reviewerReadPort.findActiveByRoles(RETURN_HANDLERS).stream()
                .filter(handler -> handler.email() != null && !handler.email().isBlank())
                .forEach(handler -> notificationSender.send(
                        message, NotificationRecipient.of(null, handler.email(), "EMAIL")));
    }

    private void send(UUID returnId, UUID orderId,
                      BiFunction<OrderView, ReturnView, NotificationMessage> compose) {
        Optional<ReturnView> request = returnReadPort.findById(returnId);
        Optional<OrderView> order = orderReadPort.findById(orderId);
        if (request.isEmpty() || order.isEmpty()) {
            log.warn("Return {} moved but could not be read back; no message sent", returnId);
            return;
        }

        NotificationMessage message = compose.apply(order.get(), request.get());
        customerReadPort.findById(order.get().customerId()).ifPresentOrElse(
                user -> notificationSender.send(message, NotificationRecipient.of(
                        user.phone(), user.email(), user.notificationChannelPreference())),
                () -> notificationSender.send(message, NotificationRecipient.unknown()));
    }
}
