package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.enums.NotificationType;
import com.pilarestilo.notification.domain.model.InAppNotification;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class InAppNotificationSender implements InAppNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationSender.class);
    private final InAppNotificationRepository repository;

    public InAppNotificationSender(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void notifyDiscountCodeAssigned(UUID userId, String code) {
        save(userId, NotificationType.DISCOUNT_CODE_ASSIGNED,
            "Código de descuento exclusivo",
            "Tienes un código de descuento exclusivo: " + code + ". Úsalo en tu próxima compra.",
            Map.of("code", code));
    }

    @Override
    public void notifyOrderConfirmed(UUID userId, UUID orderId) {
        save(userId, NotificationType.ORDER_CONFIRMED,
            "Pedido confirmado",
            "Tu pedido fue creado correctamente. Te notificaremos cuando avance.",
            Map.of("orderId", orderId.toString()));
    }

    @Override
    public void notifyPaymentReceived(UUID userId, UUID paymentId) {
        save(userId, NotificationType.PAYMENT_RECEIVED,
            "Pago recibido",
            "Confirmamos tu pago. Gracias por tu compra en Pilar Estilo.",
            Map.of("paymentId", paymentId.toString()));
    }

    @Override
    public void notifyOrderShipped(UUID userId, UUID orderId) {
        save(userId, NotificationType.ORDER_SHIPPED,
            "Pedido enviado",
            "Tu pedido ya fue enviado. Pronto llegará a destino.",
            Map.of("orderId", orderId.toString()));
    }

    private void save(UUID userId, NotificationType type, String title, String body, Map<String, Object> metadata) {
        try {
            repository.save(InAppNotification.create(userId, type, title, body, metadata));
        } catch (Exception ex) {
            log.warn("[IN_APP] failed to save notification type={} userId={} reason={}", type, userId, ex.getMessage());
        }
    }
}
