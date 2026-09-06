package com.pilarestilo.order.infrastructure.web.controllers;

import com.pilarestilo.order.application.commands.RegisterExternalSaleCommand;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.RegisterExternalSaleResult;
import com.pilarestilo.order.application.usecases.RegisterExternalSaleUseCase;
import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.infrastructure.web.requests.RegisterExternalSaleRequest;
import com.pilarestilo.shared.domain.DomainException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;

/**
 * Records a sale made off-platform (Instagram / Facebook / WhatsApp) as a real paid order.
 * See {@code docs/superpowers/specs/2026-08-31-external-sale-intake-design.md}.
 */
@RestController
@RequestMapping("/api/admin/sales")
public class ExternalSaleController {

    private static final Set<String> CHANNELS = Set.of("INSTAGRAM", "FACEBOOK", "WHATSAPP", "MANUAL");
    private static final Set<String> PAYMENTS = Set.of("TRANSFER", "OTHER");

    private final RegisterExternalSaleUseCase useCase;

    public ExternalSaleController(RegisterExternalSaleUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/external")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).ORDERS_CREATE)")
    public ResponseEntity<OrderDto> registerExternalSale(@Valid @RequestBody RegisterExternalSaleRequest req) {
        RegisterExternalSaleCommand cmd = new RegisterExternalSaleCommand(
                req.idempotencyKey(), req.buyerName(), req.buyerContact(),
                channel(req.salesChannel()), payment(req.paymentMethod()), delivery(req.deliveryMethod()),
                req.shippingAddress(), req.notes(),
                req.items().stream().map(l -> new RegisterExternalSaleCommand.Line(
                        l.productId(), l.variantColor(), l.variantSize(), l.quantity(), l.unitPrice())).toList());

        RegisterExternalSaleResult result = useCase.execute(cmd);
        return ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.dto());
    }

    private static SalesChannel channel(String raw) {
        String v = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(v)) {
            throw new DomainException("Canal invalido: " + raw);
        }
        return SalesChannel.valueOf(v);
    }

    private static PaymentMethod payment(String raw) {
        String v = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        if (!PAYMENTS.contains(v)) {
            throw new DomainException("Metodo de pago invalido: " + raw);
        }
        return PaymentMethod.valueOf(v);
    }

    private static DeliveryMethod delivery(String raw) {
        try {
            return DeliveryMethod.valueOf(raw == null ? "" : raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new DomainException("Metodo de entrega invalido: " + raw);
        }
    }
}
