package com.pilarestilo.notificationservice.application;

import com.pilarestilo.notificationservice.domain.model.NotificationMessage;
import com.pilarestilo.notificationservice.domain.view.Money;
import com.pilarestilo.notificationservice.domain.view.OrderView;
import com.pilarestilo.notificationservice.domain.view.OrderView.OrderItemView;
import com.pilarestilo.notificationservice.domain.view.PaymentView;
import com.pilarestilo.notificationservice.domain.view.WelcomeDiscount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders one representative email per HTML template to target/email-preview/*.html for a human to
 * open, and pins the invariant that a customer email carries no clickable element.
 */
class EmailPreviewTest {

    private final NotificationComposer composer = new NotificationComposer();
    private static final Path OUT = Path.of("target", "email-preview");

    private OrderView order() {
        Money p1 = Money.of(BigDecimal.valueOf(24_990), "CLP");
        Money p2 = Money.of(BigDecimal.valueOf(12_990), "CLP");
        Money total = Money.of(BigDecimal.valueOf(37_980), "CLP");
        Money zero = Money.of(BigDecimal.ZERO, "CLP");
        return new OrderView(UUID.randomUUID(), "PE-1042", UUID.randomUUID(), "PAID",
                total, zero, total, zero, BigDecimal.ZERO, total,
                "starken", "Starken", "REGIONAL",
                List.of(new OrderItemView("Blusa de lino", "Crudo", "M", 1, p1),
                        new OrderItemView("Pañuelo de seda", "Rosa", null, 1, p2)));
    }

    private PaymentView payment() {
        return new PaymentView(UUID.randomUUID(), UUID.randomUUID(), "TRANSFER", "REGISTERED",
                null, null, Instant.now(), "Pilar Estilo SpA", "Banco de Chile",
                "Cuenta Corriente", "00012345678", "pagos@pilarestilo.com");
    }

    private void dump(NotificationMessage m) throws Exception {
        Files.createDirectories(OUT);
        assertThat(m.bodyHtml()).as(m.templateKey() + " has HTML").isNotNull();
        assertThat(m.bodyHtml())
                .as(m.templateKey() + " has no link/button")
                .doesNotContain("<a ").doesNotContain("href=").doesNotContain("<button");
        assertThat(tagBalance(m.bodyHtml())).as(m.templateKey() + " tag balance").isZero();
        Files.writeString(OUT.resolve(m.templateKey() + ".html"), m.bodyHtml());
    }

    /** +1 per opening tag, -1 per closing; void / self-closing ignored. Zero means balanced. */
    private static int tagBalance(String html) {
        int depth = 0;
        Matcher matcher = Pattern.compile("<(/?)([a-zA-Z0-9]+)([^>]*?)(/?)>").matcher(html);
        Set<String> voidTags = Set.of("br", "img", "meta", "hr", "input", "!doctype");
        while (matcher.find()) {
            String name = matcher.group(2).toLowerCase();
            if (voidTags.contains(name) || !matcher.group(4).isEmpty()) {
                continue;
            }
            depth += matcher.group(1).isEmpty() ? 1 : -1;
        }
        return depth;
    }

    @Test
    void rendersEveryCustomerEmail() throws Exception {
        OrderView order = order();
        PaymentView payment = payment();
        dump(composer.transferInstructions(order, payment, Instant.now().plusSeconds(1800)));
        dump(composer.orderConfirmation(order));
        dump(composer.orderCancelled(order, "Pago rechazado por el banco"));
        dump(composer.orderShipped(order));
        dump(composer.orderDelivered(order));
        dump(composer.welcome("Camila Torres", new WelcomeDiscount(
                "BIENVENIDA20", "PERCENTAGE", BigDecimal.valueOf(20), null, LocalDate.now().plusDays(24))));
        dump(composer.discountCodeAssigned("VUELVE15"));
        dump(composer.paymentReceived(order, payment));
        // sales-document + returns are out of scope for this plan.
    }
}
