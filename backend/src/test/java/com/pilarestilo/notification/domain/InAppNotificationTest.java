package com.pilarestilo.notification.domain;

import com.pilarestilo.notification.domain.enums.NotificationType;
import com.pilarestilo.notification.domain.model.InAppNotification;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InAppNotificationTest {

    @Test
    void create_setsFieldsAndUnread() {
        UUID userId = UUID.randomUUID();
        InAppNotification n = InAppNotification.create(
            userId, NotificationType.ORDER_CONFIRMED,
            "Pedido confirmado", "Tu pedido fue creado.",
            Map.of("orderId", "abc")
        );
        assertEquals(userId, n.getUserId());
        assertEquals(NotificationType.ORDER_CONFIRMED, n.getType());
        assertEquals("Pedido confirmado", n.getTitle());
        assertFalse(n.isRead());
        assertNotNull(n.getCreatedAt());
        assertNull(n.getReadAt());
    }
}
