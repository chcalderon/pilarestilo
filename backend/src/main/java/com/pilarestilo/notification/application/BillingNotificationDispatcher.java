package com.pilarestilo.notification.application;

import com.pilarestilo.billing.domain.events.SalesDocumentIssued;
import com.pilarestilo.billing.domain.model.SalesDocument;
import com.pilarestilo.billing.domain.ports.SalesDocumentRepository;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * What happens when a tax document is registered.
 *
 * <p>The behaviour lives here rather than in either listener: {@code KafkaDomainEventPublisher} is
 * {@code @Primary} when Kafka is on, so only the Kafka transport runs in production, and four
 * defects in this codebase came from an in-process listener drifting from its twin. Both transports
 * call this and hold nothing of their own.
 */
@Service
public class BillingNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(BillingNotificationDispatcher.class);

    private final SalesDocumentRepository salesDocumentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationSender notificationSender;
    private final NotificationComposer composer;

    public BillingNotificationDispatcher(SalesDocumentRepository salesDocumentRepository,
                                         OrderRepository orderRepository,
                                         UserRepository userRepository,
                                         NotificationSender notificationSender,
                                         NotificationComposer composer) {
        this.salesDocumentRepository = salesDocumentRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.notificationSender = notificationSender;
        this.composer = composer;
    }

    public void onSalesDocumentIssued(SalesDocumentIssued event) {
        Optional<SalesDocument> document = salesDocumentRepository.findById(event.documentId());
        Optional<Order> order = orderRepository.findById(event.orderId());
        if (document.isEmpty() || order.isEmpty()) {
            log.warn("Sales document {} was issued but could not be read back; no message sent",
                    event.documentId());
            return;
        }

        var message = composer.salesDocumentIssued(order.get(), document.get());
        userRepository.findById(order.get().getCustomerId()).ifPresentOrElse(
                user -> notificationSender.send(message, NotificationRecipient.of(
                        user.getPhone(),
                        user.getEmail(),
                        user.getNotificationChannelPreference().name())),
                /* No user row: the document is real and the channel should still hear about it. */
                () -> notificationSender.send(message, NotificationRecipient.unknown()));
    }
}
