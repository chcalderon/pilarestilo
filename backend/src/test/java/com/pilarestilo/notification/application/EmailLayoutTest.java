package com.pilarestilo.notification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailLayoutTest {

    @Test
    @DisplayName("a product name with an ampersand cannot alter the markup around it")
    void escapesContent() {
        String html = EmailLayout.titled("Pedido")
                .paragraph("Blazer <b>Negro</b> & Crema")
                .build();

        assertThat(html)
                .contains("Blazer &lt;b&gt;Negro&lt;/b&gt; &amp; Crema")
                .doesNotContain("<b>Negro</b>");
    }

    @Test
    @DisplayName("the same escaping applies to labels, values and details")
    void escapesEveryBlock() {
        String html = EmailLayout.titled("<script>")
                .highlight("Titular \"x\"", "Ana & Co")
                .details(List.<String[]>of(new String[]{"Banco", "<i>Estado</i>"}))
                .note("5 > 3")
                .build();

        assertThat(html)
                .doesNotContain("<script>")
                .doesNotContain("<i>Estado</i>")
                .contains("Ana &amp; Co")
                .contains("5 &gt; 3");
    }

    @Test
    @DisplayName("layout is table-based and inline-styled, because Outlook and Gmail require it")
    void usesTheMarkupEmailClientsAgreeOn() {
        String html = EmailLayout.titled("Pedido").paragraph("Hola").build();

        assertThat(html)
                .contains("role=\"presentation\"")
                .contains("style=")
                // A <style> block would be stripped by Gmail in several contexts, taking the design with it.
                .doesNotContain("<style")
                // A web font would be blocked by most clients; the families carry real fallbacks instead.
                .doesNotContain("@import")
                .doesNotContain("fonts.googleapis.com");
    }

    @Test
    @DisplayName("the logo is attached, not linked: remote images are blocked by default")
    void referencesTheLogoByContentId() {
        String html = EmailLayout.titled("Pedido").build();

        assertThat(html)
                .contains("src=\"cid:" + EmailLayout.LOGO_CONTENT_ID + "\"")
                .doesNotContain("src=\"http");
    }

    @Test
    @DisplayName("with images off the header still says who sent it")
    void logoCarriesTheShopNameAsAltText() {
        String html = EmailLayout.titled("Pedido").build();

        assertThat(html)
                .contains("alt=\"Pilar Estilo\"")
                // Dimensions on the tag, so the layout does not jump when the image arrives.
                .contains("width=\"220\"")
                .contains("height=\"74\"");
    }

    @Test
    @DisplayName("declares both colour schemes, so a dark client does not invert half the design")
    void declaresColorSchemes() {
        String html = EmailLayout.titled("Pedido").build();

        assertThat(html)
                .contains("name=\"color-scheme\"")
                .contains("name=\"supported-color-schemes\"");
    }

    @Test
    @DisplayName("blocks appear in the order they were added")
    void keepsTheOrderOfBlocks() {
        String html = EmailLayout.titled("Pedido")
                .paragraph("primero")
                .highlight("etiqueta", "segundo")
                .note("tercero")
                .build();

        assertThat(html.indexOf("primero")).isLessThan(html.indexOf("segundo"));
        assertThat(html.indexOf("segundo")).isLessThan(html.indexOf("tercero"));
    }

    @Test
    @DisplayName("the title is both the heading and the inbox preview line")
    void titleDoublesAsPreheader() {
        String html = EmailLayout.titled("Datos para tu transferencia").build();

        assertThat(html).contains("<title>Datos para tu transferencia</title>");
        // Once hidden for the preview, once visible as the heading.
        assertThat(html.split("Datos para tu transferencia", -1)).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("a null value renders as nothing rather than the word null")
    void handlesNulls() {
        String html = EmailLayout.titled("Pedido")
                .details(List.<String[]>of(new String[]{"Banco", null}))
                .build();

        assertThat(html).doesNotContain(">null<");
    }
}
