package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.CustomerReadPort;
import com.pilarestilo.notificationservice.domain.ports.InAppNotificationPort;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import com.pilarestilo.notificationservice.events.Events;
import org.springframework.stereotype.Service;

/** What a discount assignment means for notifications. Ported from the monolith. */
@Service
public class DiscountNotificationDispatcher {

    private final InAppNotificationPort inAppNotificationPort;
    private final NotificationSender notificationSender;
    private final NotificationComposer notificationComposer;
    private final CustomerReadPort customerReadPort;

    public DiscountNotificationDispatcher(InAppNotificationPort inAppNotificationPort,
                                          NotificationSender notificationSender,
                                          NotificationComposer notificationComposer,
                                          CustomerReadPort customerReadPort) {
        this.inAppNotificationPort = inAppNotificationPort;
        this.notificationSender = notificationSender;
        this.notificationComposer = notificationComposer;
        this.customerReadPort = customerReadPort;
    }

    public void onDiscountCodeAssigned(Events.DiscountCodeAssigned event) {
        inAppNotificationPort.notifyDiscountCodeAssigned(event.assignedUserId(), event.code());

        customerReadPort.findById(event.assignedUserId()).ifPresent(user ->
                notificationSender.send(
                        notificationComposer.discountCodeAssigned(event.code()),
                        NotificationRecipient.of(user.phone(), user.email(),
                                user.notificationChannelPreference())));
    }
}
