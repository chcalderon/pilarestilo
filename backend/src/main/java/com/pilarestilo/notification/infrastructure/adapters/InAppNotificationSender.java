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
    private static final String METADATA_ORDER_ID = "orderId";
    private final InAppNotificationRepository repository;

    public InAppNotificationSender(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void notifyDiscountCodeAssigned(UUID userId, String code) {
        save(userId, NotificationType.DISCOUNT_CODE_ASSIGNED,
            "Codigo de descuento exclusivo",
            "Tienes un codigo de descuento exclusivo: " + code + ". Usalo en tu proxima compra.",
            Map.of("code", code));
    }

    @Override
    public void notifyOrderConfirmed(UUID userId, UUID orderId) {
        save(userId, NotificationType.ORDER_CONFIRMED,
            "Pedido confirmado",
            "Tu pedido fue creado correctamente. Te notificaremos cuando avance.",
            Map.of(METADATA_ORDER_ID, orderId.toString()));
    }

    @Override
    public void notifyPaymentReceived(UUID userId, UUID paymentId) {
        save(userId, NotificationType.PAYMENT_RECEIVED,
            "Pago recibido",
            "Confirmamos tu pago. Gracias por tu compra en Pilar Estilo.",
            Map.of("paymentId", paymentId.toString()));
    }

    @Override
    public void notifyOrderPreparing(UUID userId, UUID orderId) {
        save(userId, NotificationType.ORDER_PREPARING,
            "Pedido en preparacion",
            "Tu pedido esta en preparacion. Te avisaremos cuando sea despachado.",
            Map.of(METADATA_ORDER_ID, orderId.toString()));
    }

    @Override
    public void notifyOrderShipped(UUID userId, UUID orderId) {
        save(userId, NotificationType.ORDER_SHIPPED,
            "Pedido enviado",
            "Tu pedido ya fue enviado. Pronto llegara a destino.",
            Map.of(METADATA_ORDER_ID, orderId.toString()));
    }

    @Override
    public void notifyOrderDelivered(UUID userId, UUID orderId) {
        save(userId, NotificationType.ORDER_DELIVERED,
            "Pedido entregado",
            "Tu pedido quedo como entregado. Si aun no lo recibiste, avisanos.",
            Map.of(METADATA_ORDER_ID, orderId.toString()));
    }

    @Override
    public void notifyWelcome(UUID userId, String couponCode) {
        String body = couponCode == null
                ? "Gracias por crear tu cuenta. Ya puedes explorar el catalogo y hacer tu primera compra."
                : "Gracias por crear tu cuenta. Tienes un codigo de bienvenida: " + couponCode + ".";
        save(userId, NotificationType.WELCOME,
            "Bienvenida a Pilar Estilo",
            body,
            couponCode == null ? Map.of() : Map.of("code", couponCode));
    }

    private void save(UUID userId, NotificationType type, String title, String body, Map<String, Object> metadata) {
        try {
            repository.save(InAppNotification.create(userId, type, title, body, metadata));
        } catch (Exception ex) {
            log.warn("[IN_APP] failed to save notification type={} userId={} reason={}", type, userId, ex.getMessage());
        }
    }
}
