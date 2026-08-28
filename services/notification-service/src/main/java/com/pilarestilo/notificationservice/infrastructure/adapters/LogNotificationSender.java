package com.pilarestilo.notificationservice.infrastructure.adapters;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;
import com.pilarestilo.notificationservice.domain.ports.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(NotificationMessage message, NotificationRecipient recipient) {
        log.info("[NOTIFY] template={} referenceId={} subject={} data={}",
                message.templateKey(), message.referenceId(), message.subject(), message.data());
    }
}
