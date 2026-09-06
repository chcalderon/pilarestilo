package com.pilarestilo.shared.auth.infrastructure.email;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders the reset email to target/email-preview/PASSWORD_RESET.html and pins the no-link rule. */
class AuthEmailPreviewTest {

    @Test
    void rendersTheResetEmailWithoutALink() throws Exception {
        String html = AuthEmailLayout.titled("Código para cambiar tu contraseña")
                .eyebrow("Seguridad")
                .paragraph("Hola Camila. Recibimos una solicitud para cambiar tu contraseña. "
                        + "Si fuiste tú, usa este código:")
                .code("418302", null)
                .route("Cómo usarlo", "Entra a", "pilarestilo.com",
                        "Iniciar sesión › ¿Olvidaste tu contraseña?")
                .paragraph("Escribe tu correo y el código, y elige tu nueva contraseña.")
                .note("Importante", "El código vence en 30 minutos y se usa una sola vez.")
                .build();

        assertThat(html)
                .contains("Seguridad")
                .contains("418302")
                .contains("cid:" + AuthEmailLayout.LOGO_CONTENT_ID)
                .doesNotContain("<a ").doesNotContain("href=").doesNotContain("http://").doesNotContain("https://");

        Path out = Path.of("target", "email-preview");
        Files.createDirectories(out);
        Files.writeString(out.resolve("PASSWORD_RESET.html"), html);
    }
}
