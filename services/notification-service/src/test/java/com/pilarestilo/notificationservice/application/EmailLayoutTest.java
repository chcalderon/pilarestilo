package com.pilarestilo.notificationservice.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailLayoutTest {

    @Test
    void buildsAWholeDocumentWithTheHeaderTitleAndFooter() {
        String html = EmailLayout.titled("Recibimos tu pedido")
                .eyebrow("Confirmación de pedido")
                .paragraph("Gracias por comprar en Pilar Estilo.")
                .build();

        assertThat(html)
                .startsWith("<!DOCTYPE html>")
                .contains("<title>Recibimos tu pedido</title>")
                .contains("cid:" + EmailLayout.LOGO_CONTENT_ID)
                .contains("Confirmación de pedido")
                .contains("<h1")
                .contains("Recibimos tu pedido")
                .contains("Valle de Aconcagua, Chile")
                .endsWith("</html>");
    }

    @Test
    void hasNoClickableElements() {
        String html = EmailLayout.titled("x")
                .eyebrow("y")
                .paragraph("z")
                .route("Cómo verlo", "Entra a", "pilarestilo.com", "Mi cuenta › Pedidos")
                .note("Importante", "Un aviso.")
                .code("418302", "Vence en 30 minutos.")
                .build();

        assertThat(html).doesNotContain("<a ").doesNotContain("href=").doesNotContain("<button");
    }

    @Test
    void routeRendersSiteAndCrumbAsText() {
        String html = EmailLayout.titled("x")
                .route("Cómo verlo", "Entra a", "pilarestilo.com", "Mi cuenta › Pedidos")
                .build();
        assertThat(html).contains("Entra a").contains("pilarestilo.com").contains("Mi cuenta › Pedidos");
    }

    @Test
    void routeWithoutASiteJustRendersTheCrumb() {
        String html = EmailLayout.titled("x")
                .route("Cómo usarlo", "Lo escribes en el carrito, en", "", "Código de descuento")
                .build();
        assertThat(html).contains("Código de descuento").doesNotContain("pilarestilo.com");
    }

    @Test
    void orderSummaryShowsReferenceLinesAndBoldTotal() {
        String html = EmailLayout.titled("x")
                .orderSummary("PE-1042", "4 sept 2026",
                        List.of(new EmailLayout.Line("Blusa de lino", "Crudo / M · x1", "CLP 24.990")),
                        List.of(new String[]{"Subtotal", "CLP 24.990"}, new String[]{"Total", "CLP 24.990"}))
                .build();

        assertThat(html)
                .contains("Pedido PE-1042").contains("4 sept 2026")
                .contains("Blusa de lino").contains("Crudo / M · x1").contains("CLP 24.990")
                .contains("Total");
    }

    @Test
    void codeBlockShowsTheValueAndCaption() {
        String html = EmailLayout.titled("x").code("BIENVENIDA20", "20% de descuento").build();
        assertThat(html).contains("BIENVENIDA20").contains("20% de descuento");
    }

    @Test
    void escapesUserText() {
        String html = EmailLayout.titled("<b>x</b>").paragraph("a & b < c").build();
        assertThat(html).contains("&lt;b&gt;x&lt;/b&gt;").contains("a &amp; b &lt; c");
    }
}
