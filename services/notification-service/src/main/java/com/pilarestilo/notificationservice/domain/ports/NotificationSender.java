package com.pilarestilo.notificationservice.domain.ports;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.model.NotificationRecipient;

/**
 * One method. Every message is composed by {@code NotificationComposer} and rendered by the adapter.
 *
 * <p>Abstract on purpose: with no default, a new channel cannot be added without deciding what
 * every message does.
 */
public interface NotificationSender {

    void send(NotificationMessage message, NotificationRecipient recipient);
}
