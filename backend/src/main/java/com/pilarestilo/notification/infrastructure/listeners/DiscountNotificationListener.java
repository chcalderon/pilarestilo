package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.discount.domain.events.DiscountCodeAssigned;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DiscountNotificationListener {

    private final InAppNotificationPort inAppNotificationPort;
    private final NotificationSender notificationSender;
    private final UserRepository userRepository;

    public DiscountNotificationListener(InAppNotificationPort inAppNotificationPort,
                                         NotificationSender notificationSender,
                                         UserRepository userRepository) {
        this.inAppNotificationPort = inAppNotificationPort;
        this.notificationSender = notificationSender;
        this.userRepository = userRepository;
    }

    @EventListener
    public void onDiscountCodeAssigned(DiscountCodeAssigned event) {
        inAppNotificationPort.notifyDiscountCodeAssigned(event.assignedUserId(), event.code());

        userRepository.findById(event.assignedUserId()).ifPresent(user ->
            notificationSender.sendDiscountCodeAssigned(
                event.code(),
                NotificationRecipient.of(user.getPhone(), user.getEmail(),
                    user.getNotificationChannelPreference().name())
            )
        );
    }
}
