package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.CustomerReadPort;
import com.pilarestilo.notificationservice.domain.ports.InAppNotificationPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.domain.view.CustomerView;
import com.pilarestilo.notificationservice.events.Events;
import org.springframework.stereotype.Service;

/** What a new account means for notifications, in one place. Ported from the monolith. */
@Service
public class UserNotificationDispatcher {

    private final NotificationSender notificationSender;
    private final NotificationComposer composer;
    private final InAppNotificationPort inAppNotificationPort;
    private final CustomerReadPort customerReadPort;

    public UserNotificationDispatcher(NotificationSender notificationSender,
                                      NotificationComposer composer,
                                      InAppNotificationPort inAppNotificationPort,
                                      CustomerReadPort customerReadPort) {
        this.notificationSender = notificationSender;
        this.composer = composer;
        this.inAppNotificationPort = inAppNotificationPort;
        this.customerReadPort = customerReadPort;
    }

    public void onUserRegistered(Events.UserRegistered event) {
        customerReadPort.findById(event.userId()).ifPresent(user -> {
            notificationSender.send(
                    composer.welcome(user.fullName(), event.welcomeDiscount()), recipientFor(user));
            String couponCode = event.welcomeDiscount() == null ? null : event.welcomeDiscount().code();
            inAppNotificationPort.notifyWelcome(user.id(), couponCode);
        });
    }

    private NotificationRecipient recipientFor(CustomerView user) {
        return NotificationRecipient.of(user.phone(), user.email(), user.notificationChannelPreference());
    }
}
