package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.notification.provider", havingValue = "WHATSAPP_SIMULATED")
public class SimulatedWhatsAppNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedWhatsAppNotificationSender.class);

    private final String simulatedTo;
    private final String senderAlias;

    public SimulatedWhatsAppNotificationSender(
            @Value("${app.notification.whatsapp.simulated-to:+56900000000}") String simulatedTo,
            @Value("${app.notification.whatsapp.simulated-sender:Pilar Estilo}") String senderAlias
    ) {
        this.simulatedTo = normalize(simulatedTo, "+56900000000");
        this.senderAlias = normalize(senderAlias, "Pilar Estilo");
    }

    @Override
    public void sendOrderConfirmation(UUID orderId, NotificationRecipient recipient) {
        logSimulated("ORDER_CONFIRMATION", simulatedTo, orderId, recipient);
    }

    @Override
    public void sendPaymentReceived(UUID paymentId, NotificationRecipient recipient) {
        logSimulated("PAYMENT_RECEIVED", simulatedTo, paymentId, recipient);
    }

    @Override
    public void sendOrderShipped(UUID orderId, NotificationRecipient recipient) {
        logSimulated("ORDER_SHIPPED", simulatedTo, orderId, recipient);
    }

    private void logSimulated(String template, String to, UUID referenceId, NotificationRecipient recipient) {
        log.info(
                "[WHATSAPP:SIMULATED] sender={} to={} template={} referenceId={} recipient={}",
                senderAlias,
                to,
                template,
                referenceId,
                recipient.preferredPhoneThenEmail()
        );
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
