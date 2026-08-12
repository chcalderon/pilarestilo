package com.pilarestilo.notification.application;

import com.pilarestilo.discount.domain.events.DiscountCodeAssigned;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

/**
 * What a discount assignment means for notifications.
 *
 * <p>Third of its kind, for the same reason as the other two: the in-process listener was the
 * only subscriber, and every {@code @EventListener} is dead whenever Kafka is enabled, because
 * {@code KafkaDomainEventPublisher} is {@code @Primary}. There was no Kafka twin at all here, so
 * in production {@code pe.domain.discount-code-assigned} had no consumer — a customer given an
 * exclusive code was never told about it.
 *
 * <p>Holding the behaviour here means the two transports cannot diverge, which is what happened
 * to the order listeners while nobody was looking.
 */
@Service
public class DiscountNotificationDispatcher {

    private final InAppNotificationPort inAppNotificationPort;
    private final NotificationSender notificationSender;
    private final NotificationComposer notificationComposer;
    private final UserRepository userRepository;

    public DiscountNotificationDispatcher(InAppNotificationPort inAppNotificationPort,
                                          NotificationSender notificationSender,
                                          NotificationComposer notificationComposer,
                                          UserRepository userRepository) {
        this.inAppNotificationPort = inAppNotificationPort;
        this.notificationSender = notificationSender;
        this.notificationComposer = notificationComposer;
        this.userRepository = userRepository;
    }

    public void onDiscountCodeAssigned(DiscountCodeAssigned event) {
        inAppNotificationPort.notifyDiscountCodeAssigned(event.assignedUserId(), event.code());

        userRepository.findById(event.assignedUserId()).ifPresent(user ->
                notificationSender.send(
                        notificationComposer.discountCodeAssigned(event.code()),
                        NotificationRecipient.of(
                                user.getPhone(),
                                user.getEmail(),
                                user.getNotificationChannelPreference().name())
                )
        );
    }
}
