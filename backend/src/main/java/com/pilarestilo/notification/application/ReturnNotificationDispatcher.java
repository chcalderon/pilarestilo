package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.returns.domain.events.RefundRegistered;
import com.pilarestilo.returns.domain.events.ReturnApproved;
import com.pilarestilo.returns.domain.events.ReturnRequested;
import com.pilarestilo.returns.domain.model.ReturnRequest;
import com.pilarestilo.returns.domain.ports.ReturnRequestRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * What the customer hears while her sale is being undone: it is on record, it was approved, the
 * money went back.
 *
 * <p>The behaviour lives here rather than in either listener: {@code KafkaDomainEventPublisher} is
 * {@code @Primary} when Kafka is on, so only the Kafka transport runs in production, and four
 * defects in this codebase came from an in-process listener drifting from its twin. Both transports
 * call this and hold nothing of their own.
 *
 * <p>Silence is the failure mode that matters here. A return with no word back is what turns a
 * customer exercising a right into a complaint, so the three moments the law cares about are all
 * written to her.
 */
@Service
public class ReturnNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReturnNotificationDispatcher.class);

    /** Whoever may resolve a return. Kept beside the send so the two cannot fall out of step. */
    private static final List<UserRole> RETURN_HANDLERS =
            List.of(UserRole.ADMIN, UserRole.ADMINISTRACION);

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationSender notificationSender;
    private final NotificationComposer composer;

    public ReturnNotificationDispatcher(ReturnRequestRepository returnRequestRepository,
                                        OrderRepository orderRepository,
                                        UserRepository userRepository,
                                        NotificationSender notificationSender,
                                        NotificationComposer composer) {
        this.returnRequestRepository = returnRequestRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.notificationSender = notificationSender;
        this.composer = composer;
    }

    public void onReturnRequested(ReturnRequested event) {
        send(event.returnId(), event.orderId(), composer::returnRequested);
        notifyHandlers(event.returnId(), event.orderId());
    }

    public void onReturnApproved(ReturnApproved event) {
        send(event.returnId(), event.orderId(), composer::returnApproved);
    }

    public void onRefundRegistered(RefundRegistered event) {
        send(event.returnId(), event.orderId(), composer::refundRegistered);
    }

    /**
     * The shop side of the same event. Their channel preference is deliberately ignored: this is a
     * staff alert about a legal deadline, and a handler who set themselves to WhatsApp would
     * otherwise silently stop being told.
     */
    private void notifyHandlers(UUID returnId, UUID orderId) {
        Optional<ReturnRequest> request = returnRequestRepository.findById(returnId);
        Optional<Order> order = orderRepository.findById(orderId);
        if (request.isEmpty() || order.isEmpty()) {
            return;
        }
        String buyerName = userRepository.findById(order.get().getCustomerId())
                .map(User::getFullName)
                .orElse("Cliente");
        NotificationMessage message =
                composer.returnRequestedForStaff(order.get(), request.get(), buyerName);
        userRepository.findByRoleIn(RETURN_HANDLERS, PageRequest.of(0, 50))
                .getContent().stream()
                .filter(User::isActive)
                .filter(handler -> handler.getEmail() != null && !handler.getEmail().isBlank())
                .forEach(handler -> notificationSender.send(
                        message, NotificationRecipient.of(null, handler.getEmail(), "EMAIL")));
    }

    private void send(UUID returnId, UUID orderId,
                      BiFunction<Order, ReturnRequest, NotificationMessage> compose) {
        Optional<ReturnRequest> request = returnRequestRepository.findById(returnId);
        Optional<Order> order = orderRepository.findById(orderId);
        if (request.isEmpty() || order.isEmpty()) {
            log.warn("Return {} moved but could not be read back; no message sent", returnId);
            return;
        }

        NotificationMessage message = compose.apply(order.get(), request.get());
        userRepository.findById(order.get().getCustomerId()).ifPresentOrElse(
                user -> notificationSender.send(message, NotificationRecipient.of(
                        user.getPhone(),
                        user.getEmail(),
                        user.getNotificationChannelPreference().name())),
                /* No user row: the return is real and the channel should still hear about it. */
                () -> notificationSender.send(message, NotificationRecipient.unknown()));
    }
}
