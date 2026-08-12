package com.pilarestilo.notification.domain.ports;

import com.pilarestilo.notification.domain.model.NotificationMessage;
import com.pilarestilo.notification.domain.model.NotificationRecipient;

/**
 * One method. Every message is composed by {@link
 * com.pilarestilo.notification.application.NotificationComposer} and rendered by the adapter.
 *
 * <p>There used to be five typed methods beside it, each reimplemented in all eight adapters.
 * The copy drifted per channel as a result: SMTP carried real Spanish prose while the WhatsApp
 * and log senders emitted only a template key and an id, and adding a field meant eight edits.
 */
public interface NotificationSender {

    /**
     * Renders a composed message.
     *
     * <p>Abstract on purpose. This replaced {@code default void sendOrderCancelled(...) {}}, whose
     * empty body was a compile-time licence for an adapter to do nothing: only the LOG sender ever
     * implemented it, so under EMAIL_SMTP, SendGrid, WhatsApp or n8n a customer whose order was
     * auto-cancelled was never told. With no default, a new channel cannot be added without
     * deciding what every message does.
     */
    void send(NotificationMessage message, NotificationRecipient recipient);
}
