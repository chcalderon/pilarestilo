package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.view.CustomerView;
import com.pilarestilo.notificationservice.domain.view.Money;
import com.pilarestilo.notificationservice.domain.view.OrderView;
import com.pilarestilo.notificationservice.domain.view.OrderView.OrderItemView;
import com.pilarestilo.notificationservice.domain.view.PaymentView;
import com.pilarestilo.notificationservice.domain.view.ReturnView;
import com.pilarestilo.notificationservice.domain.view.SalesDocumentView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ViewFixtures {

    private ViewFixtures() {}

    static Money clp(long amount) {
        return Money.of(BigDecimal.valueOf(amount), "CLP");
    }

    static OrderView order(UUID id, UUID customerId, String reference) {
        return new OrderView(id, reference, customerId, "CREATED",
                clp(45000), clp(0), clp(37815), clp(7185), BigDecimal.valueOf(19), clp(45000),
                "starken", "Starken", "LOCAL",
                List.of(new OrderItemView("Vestido", "Rojo", "M", 1, clp(45000))));
    }

    static CustomerView customer(UUID id) {
        return new CustomerView(id, "cliente@example.com", "+56912345678", "Camila Torres",
                "CUSTOMER", true, "AUTO");
    }

    static CustomerView reviewer(String email) {
        return new CustomerView(UUID.randomUUID(), email, null, "Revisor", "ADMIN", true, "EMAIL");
    }

    static PaymentView transferPayment(UUID id, UUID orderId) {
        return new PaymentView(id, orderId, "TRANSFER", "REGISTERED", null, "comprobante.pdf",
                Instant.now(), "Pilar Estilo SpA", "Banco de Chile", "Cuenta Corriente",
                "00012345678", "pagos@pilarestilo.com");
    }

    static SalesDocumentView boleta(UUID id) {
        return new SalesDocumentView(id, "BOLETA", "12345", clp(37815), clp(7185),
                BigDecimal.valueOf(19), clp(45000));
    }

    static ReturnView returnRequest(UUID id, UUID orderId) {
        return new ReturnView(id, orderId, "RETRACTO", "No me quedó", Instant.now().plusSeconds(86400),
                null, null, null, null);
    }
}
