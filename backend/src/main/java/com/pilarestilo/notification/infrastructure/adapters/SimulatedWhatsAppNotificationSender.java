package com.pilarestilo.notification.infrastructure.adapters;

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
    public void sendOrderConfirmation(UUID orderId, String customerEmail) {
        logSimulated("ORDER_CONFIRMATION", simulatedTo, orderId, customerEmail);
    }

    @Override
    public void sendPaymentReceived(UUID paymentId, String customerEmail) {
        logSimulated("PAYMENT_RECEIVED", simulatedTo, paymentId, customerEmail);
    }

    @Override
    public void sendOrderShipped(UUID orderId, String customerEmail) {
        logSimulated("ORDER_SHIPPED", simulatedTo, orderId, customerEmail);
    }

    private void logSimulated(String template, String to, UUID referenceId, String customerEmail) {
        String recipientLabel = customerEmail == null || customerEmail.isBlank() ? "unknown" : customerEmail.trim();
        log.info(
                "[WHATSAPP:SIMULATED] sender={} to={} template={} referenceId={} recipient={}",
                senderAlias,
                to,
                template,
                referenceId,
                recipientLabel
        );
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
