package com.pilarestilo.notification.application;

import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.user.domain.events.UserRegistered;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

import com.pilarestilo.notification.domain.model.NotificationRecipient;

/**
 * What a new account means for notifications, in one place — same shape as
 * {@link OrderNotificationDispatcher}: the listeners are transport adapters with no
 * behaviour of their own.
 */
@Service
public class UserNotificationDispatcher {

    private final NotificationSender notificationSender;
    private final NotificationComposer composer;
    private final InAppNotificationPort inAppNotificationPort;
    private final UserRepository userRepository;

    public UserNotificationDispatcher(NotificationSender notificationSender,
                                      NotificationComposer composer,
                                      InAppNotificationPort inAppNotificationPort,
                                      UserRepository userRepository) {
        this.notificationSender = notificationSender;
        this.composer = composer;
        this.inAppNotificationPort = inAppNotificationPort;
        this.userRepository = userRepository;
    }

    public void onUserRegistered(UserRegistered event) {
        userRepository.findById(event.userId()).ifPresent(user -> {
            notificationSender.send(
                    composer.welcome(user.getFullName(), event.welcomeDiscount()), recipientFor(user));
            String couponCode = event.welcomeDiscount() == null ? null : event.welcomeDiscount().code();
            inAppNotificationPort.notifyWelcome(user.getId(), couponCode);
        });
    }

    private NotificationRecipient recipientFor(User user) {
        return NotificationRecipient.of(
                user.getPhone(),
                user.getEmail(),
                user.getNotificationChannelPreference().name()
        );
    }
}
