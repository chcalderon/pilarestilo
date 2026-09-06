# Transactional Email Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One refined brand look for every customer transactional email, zero clickable links or buttons in them, password reset by 6-digit code, all copy in Chilean Spanish.

**Architecture:** Keep the Java string-builder approach — `EmailLayout` in `notification-service`, a deliberately minimal copy (`AuthEmailLayout`) in the monolith for the self-contained password-reset mailer. Add layout primitives (`eyebrow`, `route`, `code`, `orderSummary`); every "go here" becomes plain-text navigation. Password reset gains an `attempt_count` column and an `email + code + newPassword` contract; the reset page becomes a 3-field form.

**Tech Stack:** Spring Boot 4.0.7 / Java 25, Flyway (new migration **V105**), Testcontainers + MockMvc ITs, JUnit 5 + Mockito, `tools.jackson`. Astro 5 SSR + React islands, Vitest + happy-dom.

**Spec:** `docs/superpowers/specs/2026-09-06-transactional-email-redesign-design.md`

## Global Constraints

- **No clickable elements in customer emails.** No `<a href>`, no `<button>`, no `mailto:`, no bare URL rendered as the primary action. Where the customer must act, state the site + menu path as text. Enforced by a test that greps every rendered HTML body for `<a ` and `href=`.
- **Chilean Spanish, standard `tú`.** Never voseo (`tenés`, `podés`, `revisá`, `mandá`). Imperatives: `revisa`, `escribe`, `sube`, `entra`, `gestiona`.
- **Email HTML rules:** table layout not flexbox, inline styles, 600px max width, no web fonts (font stacks with fallbacks), logo via `cid:` attachment, explicit colours on every element, `role="presentation"` on layout tables.
- **Password reset stays self-contained in the monolith** — no call to `notification-service`, Kafka, or `system_settings.notification_providers`. Its own SMTP path, its own minimal layout class.
- **Enumeration-safe.** `POST /auth/forgot-password` returns the same 200 body regardless of match. Every reset failure returns the one message `El enlace no es válido o ya expiró` — keep the wording verbatim.
- **Palette:** parchment `#F5F1EB`, surface `#FFFFFF`, ink `#1A1A1A`, body `#3A3A3A`, rose `#B76E79`, rose-deep `#8E4F58`, rose-soft/dashed `#C9A9AC`, border `#E7DED0`, footer rule `#EFE7DC`, muted `#8A8078`, inset `#FBF8F3`. Serif `'Cormorant Garamond', Georgia, 'Times New Roman', serif`; sans `Montserrat, 'Helvetica Neue', Helvetica, Arial, sans-serif`.
- **Branch:** work on `develop`; `master` only on the owner's explicit request. Commit after every task.
- **`notification-service` does not map `password_reset_tokens`** — V105 needs no `*RoEntity` change; `ReadOnlyMappingIT` is unaffected.

---

## File Structure

**notification-service:**
- `application/EmailLayout.java` — MODIFY: palette constants, new primitives, shell rework.
- `application/NotificationComposer.java` — MODIFY: `paymentReceived` signature + HTML, `orderConfirmationHtml` to `orderSummary`, HTML for `discountCodeAssigned`/`welcome`, `note(label,text)` sweep, `methodLabel` helper, delete `orderPreparing`.
- `application/PaymentNotificationDispatcher.java` — MODIFY: `onPaymentConfirmed` loads order + payment.
- `application/OrderNotificationDispatcher.java` — MODIFY: `compose` returns `Optional`, `PREPARING_ORDER` skips the email send.
- `test/.../application/EmailLayoutTest.java` — CREATE.
- `test/.../application/EmailPreviewTest.java` — CREATE.
- `test/.../application/NotificationComposerTest.java` — MODIFY.
- `test/.../application/DispatchersTest.java` — MODIFY.

**monolith (backend):**
- `resources/db/migration/V105__password_reset_attempt_count.sql` — CREATE.
- `shared/auth/application/PasswordResetTokens.java` — MODIFY: add `newCode()`.
- `shared/auth/domain/model/PasswordResetToken.java` — MODIFY: `attemptCount`, `MAX_ATTEMPTS`, `isUsable`, `recordFailedAttempt`, `reconstruct` arg.
- `shared/auth/domain/ports/PasswordResetTokenRepository.java` — MODIFY: add `findActiveByUserId`.
- `shared/auth/domain/ports/PasswordResetMailer.java` — MODIFY: `sendResetLink` → `sendResetCode`.
- `shared/auth/infrastructure/persistence/entities/PasswordResetTokenEntity.java` — MODIFY: `attemptCount`.
- `shared/auth/infrastructure/persistence/repositories/PasswordResetTokenJpaRepository.java` — MODIFY: `findActiveByUserId` query.
- `shared/auth/infrastructure/persistence/repositories/PasswordResetTokenRepositoryAdapter.java` — MODIFY: map `attemptCount`, implement `findActiveByUserId`.
- `shared/auth/application/usecases/RequestPasswordResetUseCase.java` — MODIFY: generate code.
- `shared/auth/application/usecases/ResetPasswordUseCase.java` — MODIFY: `execute(email, code, newPassword)`.
- `shared/auth/infrastructure/email/AuthEmailLayout.java` — CREATE.
- `shared/auth/infrastructure/email/SmtpPasswordResetMailer.java` — MODIFY: `sendResetCode`, `AuthEmailLayout` render, config cleanup.
- `shared/auth/infrastructure/web/AuthController.java` — MODIFY: pass `email, code, newPassword`.
- `shared/auth/infrastructure/web/requests/ResetPasswordRequest.java` — MODIFY: `email, code, newPassword`.
- `resources/application.yml`, `resources/META-INF/additional-spring-configuration-metadata.json`, `infra/.env.example` (+ note the other two `.env`) — MODIFY: drop `app.password-reset.link-base-url`, add `code-ttl-minutes` / `max-attempts`.
- `test/.../shared/auth/...` — MODIFY: `ResetPasswordUseCaseTest`, `RequestPasswordResetUseCaseTest`, `SmtpPasswordResetMailerTest`, `PasswordResetControllerIT`, `PasswordResetTokenRepositoryAdapterIT`. CREATE: `AuthEmailPreviewTest`.

**frontend:**
- `src/lib/api.ts` — MODIFY: `resetPassword(email, code, newPassword)`.
- `src/islands/auth/ResetPasswordForm.tsx` — MODIFY: drop `?token`, 3-field form.
- `src/islands/auth/ForgotPasswordForm.tsx` — MODIFY: success copy `enlace` → `código`.
- `src/islands/auth/__tests__/ResetPasswordForm.test.tsx` — MODIFY.
- `src/islands/auth/__tests__/ForgotPasswordForm.test.tsx` — MODIFY.

---

## Commands

- notification-service one test: `cd services/notification-service && mvn -q -o test -Dtest=EmailLayoutTest`
- notification-service all: `cd services/notification-service && mvn -q -o test`
- monolith one test: `cd backend && mvn -q -o test -Dtest=ResetPasswordUseCaseTest`
- monolith verify (Testcontainers — **SonarQube and the app compose stack must be stopped**): `cd backend && mvn -o clean verify`
- frontend: `cd frontend && ./node_modules/.bin/tsc --noEmit && npx vitest run <path> && npm run build`

---

## Task 1: `EmailLayout` v2 — palette, primitives, shell

**Files:**
- Modify: `services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/EmailLayout.java`
- Test: `services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/EmailLayoutTest.java` (create)

**Interfaces produced:**
- `EmailLayout.titled(String) → Builder` (unchanged entry)
- `Builder.eyebrow(String text) → Builder`
- `Builder.paragraph(String text) → Builder` (unchanged)
- `Builder.code(String value, String caption) → Builder` (caption nullable)
- `Builder.route(String label, String leadText, String site, String crumbPath) → Builder` (site may be `""`)
- `Builder.orderSummary(String reference, String dateText, java.util.List<EmailLayout.Line> lines, java.util.List<String[]> totals) → Builder`
- `Builder.note(String label, String text) → Builder` (was `note(String text)`)
- `Builder.details(List<String[]>) → Builder` (unchanged)
- record `EmailLayout.Line(String name, String variantAndQty, String price)`
- `Builder.build() → String`
- constants `LOGO_CONTENT_ID`, `LOGO_RESOURCE` (unchanged values)

- [ ] **Step 1: Write `EmailLayoutTest`**

```java
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
        String html = EmailLayout.titled("x").route("Cómo verlo", "Entra a", "pilarestilo.com", "Mi cuenta › Pedidos").build();
        assertThat(html).contains("Entra a").contains("pilarestilo.com").contains("Mi cuenta › Pedidos");
    }

    @Test
    void routeWithoutASiteJustRendersTheCrumb() {
        String html = EmailLayout.titled("x").route("Cómo usarlo", "Lo escribes en el carrito, en", "", "Código de descuento").build();
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
```

- [ ] **Step 2: Run it, expect compile failure** (`eyebrow`, `code`, `route`, `orderSummary`, `Line`, `note(label,text)` do not exist).

Run: `cd services/notification-service && mvn -q -o test -Dtest=EmailLayoutTest`
Expected: compilation error.

- [ ] **Step 3: Rewrite `EmailLayout.java`**

Replace the constants block and add primitives. Full new file body:

```java
package com.pilarestilo.notificationservice.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a message in the shop's own look, in the subset of HTML that email clients agree on.
 *
 * <p>Cormorant Garamond over Montserrat, rose on parchment — the storefront palette, so an email
 * and the site read as one place. Tables not flexbox (Outlook renders through Word), inline styles
 * (Gmail strips {@code <style>}), no web fonts, 600px, explicit colours everywhere. No clickable
 * elements: where the reader must act, the copy names the site and the menu path as plain text.
 */
public final class EmailLayout {

    private static final String PARCHMENT = "#F5F1EB";
    private static final String SURFACE = "#FFFFFF";
    private static final String INK = "#1A1A1A";
    private static final String BODY_INK = "#3A3A3A";
    private static final String ROSE = "#B76E79";
    private static final String ROSE_DEEP = "#8E4F58";
    private static final String DASH = "#C9A9AC";
    private static final String BORDER = "#E7DED0";
    private static final String FOOT_RULE = "#EFE7DC";
    private static final String MUTED = "#8A8078";
    private static final String INSET = "#FBF8F3";

    private static final String SERIF = "'Cormorant Garamond', Georgia, 'Times New Roman', serif";
    private static final String SANS = "Montserrat, 'Helvetica Neue', Helvetica, Arial, sans-serif";

    public static final String LOGO_CONTENT_ID = "pilar-estilo-logo";
    public static final String LOGO_RESOURCE = "email/pilar-estilo-logo.png";

    private EmailLayout() {
    }

    public static Builder titled(String title) {
        return new Builder(title);
    }

    /** One order line in {@link Builder#orderSummary}. */
    public record Line(String name, String variantAndQty, String price) {
    }

    public static final class Builder {

        private final String title;
        private final List<String> blocks = new ArrayList<>();

        private Builder(String title) {
            this.title = title;
        }

        /** The small uppercase line above the heading that says what this message is. */
        public Builder eyebrow(String text) {
            blocks.add("<div style=\"font-family:" + SANS + ";font-size:10px;letter-spacing:2.5px;"
                    + "text-transform:uppercase;color:" + ROSE + ";margin:0 0 10px;\">"
                    + escape(text) + "</div>");
            return this;
        }

        public Builder paragraph(String text) {
            blocks.add("<p style=\"margin:0 0 18px;font-family:" + SANS + ";font-size:15px;"
                    + "line-height:1.7;color:" + BODY_INK + ";\">" + escape(text) + "</p>");
            return this;
        }

        /** A code the reader types elsewhere — a reset code, a coupon. {@code caption} may be null. */
        public Builder code(String value, String caption) {
            StringBuilder b = new StringBuilder(
                    "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                            + "width=\"100%\" style=\"margin:4px 0 18px;\"><tr><td align=\"center\" "
                            + "style=\"border:1px dashed " + DASH + ";background-color:" + INSET + ";"
                            + "padding:22px 20px;\">"
                            + "<div style=\"font-family:" + SERIF + ";font-size:32px;letter-spacing:8px;"
                            + "color:" + ROSE_DEEP + ";\">" + escape(value) + "</div>");
            if (caption != null && !caption.isBlank()) {
                b.append("<div style=\"font-family:").append(SANS).append(";font-size:12px;color:")
                        .append(MUTED).append(";margin-top:8px;\">").append(escape(caption)).append("</div>");
            }
            b.append("</td></tr></table>");
            blocks.add(b.toString());
            return this;
        }

        /**
         * "How to get there", as text — never a link. {@code site} (e.g. {@code pilarestilo.com})
         * is bolded when present; {@code crumbPath} is the menu trail (already contains its own
         * {@code ›} separators).
         */
        public Builder route(String label, String leadText, String site, String crumbPath) {
            String siteSpan = site == null || site.isBlank()
                    ? ""
                    : " <span style=\"font-weight:600;\">" + escape(site) + "</span>,";
            blocks.add("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" style=\"margin:4px 0 18px;\"><tr>"
                    + "<td style=\"border:1px solid " + BORDER + ";background-color:" + INSET + ";"
                    + "padding:15px 20px;\">"
                    + "<div style=\"font-family:" + SANS + ";font-size:10px;letter-spacing:1.5px;"
                    + "text-transform:uppercase;color:" + MUTED + ";margin-bottom:5px;\">"
                    + escape(label) + "</div>"
                    + "<p style=\"margin:0;font-family:" + SANS + ";font-size:13px;line-height:1.6;"
                    + "color:" + BODY_INK + ";\">" + escape(leadText) + siteSpan
                    + " <span style=\"color:" + ROSE_DEEP + ";font-weight:600;\">"
                    + escape(crumbPath) + "</span></p>"
                    + "</td></tr></table>");
            return this;
        }

        /** Order reference + date, the item lines, then the totals with a bold Total row. */
        public Builder orderSummary(String reference, String dateText, List<Line> lines,
                                    List<String[]> totals) {
            StringBuilder b = new StringBuilder(
                    "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                            + "width=\"100%\" style=\"margin:0 0 18px;border:1px solid " + BORDER + ";\">");
            b.append("<tr><td style=\"padding:13px 20px;background-color:").append(INSET)
                    .append(";border-bottom:1px solid ").append(BORDER)
                    .append(";font-family:").append(SANS).append(";\">")
                    .append("<span style=\"font-size:13px;font-weight:600;color:").append(INK)
                    .append(";letter-spacing:.5px;\">Pedido ").append(escape(reference)).append("</span>")
                    .append("<span style=\"float:right;font-size:12px;color:").append(MUTED).append(";\">")
                    .append(escape(dateText)).append("</span></td></tr>");
            for (Line line : lines) {
                b.append("<tr><td style=\"padding:12px 20px;border-bottom:1px solid ").append(BORDER)
                        .append(";font-family:").append(SANS).append(";font-size:13px;color:").append(INK)
                        .append(";\">").append(escape(line.name()))
                        .append("<br><span style=\"color:").append(MUTED).append(";font-size:12px;\">")
                        .append(escape(line.variantAndQty())).append("</span>")
                        .append("<span style=\"float:right;white-space:nowrap;\">")
                        .append(escape(line.price())).append("</span></td></tr>");
            }
            for (String[] row : totals) {
                boolean isTotal = "Total".equalsIgnoreCase(row[0]);
                b.append("<tr><td style=\"padding:").append(isTotal ? "10px 20px" : "6px 20px")
                        .append(";font-family:").append(SANS)
                        .append(isTotal
                                ? ";font-size:15px;font-weight:600;color:" + INK + ";border-top:1px solid " + BORDER
                                : ";font-size:12px;color:" + MUTED)
                        .append(";\">").append(escape(row[0]))
                        .append("<span style=\"float:right;\">").append(escape(row[1]))
                        .append("</span></td></tr>");
            }
            b.append("</table>");
            blocks.add(b.toString());
            return this;
        }

        public Builder details(List<String[]> rows) {
            StringBuilder table = new StringBuilder(
                    "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                            + "width=\"100%\" style=\"margin:0 0 18px;border:1px solid " + BORDER + ";\">");
            for (String[] row : rows) {
                table.append("<tr>")
                        .append("<td style=\"padding:10px 16px;border-bottom:1px solid ").append(BORDER)
                        .append(";font-family:").append(SANS).append(";font-size:13px;color:").append(MUTED)
                        .append(";white-space:nowrap;\">").append(escape(row[0])).append("</td>")
                        .append("<td style=\"padding:10px 16px;border-bottom:1px solid ").append(BORDER)
                        .append(";font-family:").append(SANS).append(";font-size:14px;color:").append(INK)
                        .append(";font-weight:500;\">").append(escape(row[1])).append("</td>")
                        .append("</tr>");
            }
            table.append("</table>");
            blocks.add(table.toString());
            return this;
        }

        /** A bordered note for a caveat — carries its own words, no colour-only signal. */
        public Builder note(String label, String text) {
            blocks.add("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" style=\"margin:0 0 18px;\"><tr>"
                    + "<td style=\"border:1px solid " + BORDER + ";background-color:" + INSET + ";"
                    + "padding:16px 20px;\">"
                    + "<div style=\"font-family:" + SANS + ";font-size:10px;letter-spacing:1.5px;"
                    + "text-transform:uppercase;color:" + MUTED + ";margin-bottom:6px;\">"
                    + escape(label) + "</div>"
                    + "<p style=\"margin:0;font-family:" + SANS + ";font-size:13px;line-height:1.6;"
                    + "color:" + BODY_INK + ";\">" + escape(text) + "</p>"
                    + "</td></tr></table>");
            return this;
        }

        public String build() {
            return "<!DOCTYPE html><html lang=\"es\"><head>"
                    + "<meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                    + "<meta name=\"color-scheme\" content=\"light dark\">"
                    + "<meta name=\"supported-color-schemes\" content=\"light dark\">"
                    + "<style>@media (prefers-color-scheme: dark){"
                    + ".pe-bg{background-color:#211E1B!important}"
                    + ".pe-card{background-color:#2A2622!important;border-color:#3A352F!important}"
                    + ".pe-ink{color:#F2ECE3!important}.pe-body{color:#D9D1C5!important}}"
                    + "</style>"
                    + "<title>" + escape(title) + "</title>"
                    + "</head>"
                    + "<body class=\"pe-bg\" style=\"margin:0;padding:0;background-color:" + PARCHMENT + ";\">"
                    + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">"
                    + escape(title) + "</div>"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"100%\" class=\"pe-bg\" style=\"background-color:" + PARCHMENT + ";\"><tr>"
                    + "<td align=\"center\" style=\"padding:32px 16px;\">"
                    + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                    + "width=\"600\" class=\"pe-card\" style=\"width:100%;max-width:600px;"
                    + "background-color:" + SURFACE + ";border:1px solid " + BORDER + ";\">"
                    + header()
                    + "<tr><td style=\"padding:12px 40px 10px;\">"
                    + "<h1 class=\"pe-ink\" style=\"margin:0 0 14px;font-family:" + SERIF + ";font-size:26px;"
                    + "font-weight:300;line-height:1.25;color:" + INK + ";\">" + escape(title) + "</h1>"
                    + String.join("", blocks)
                    + "</td></tr>"
                    + footer()
                    + "</table></td></tr></table></body></html>";
        }

        private String header() {
            return "<tr><td align=\"center\" style=\"padding:34px 40px 20px;\">"
                    + "<img src=\"cid:" + LOGO_CONTENT_ID + "\" alt=\"Pilar Estilo\" "
                    + "width=\"200\" height=\"67\" "
                    + "style=\"display:block;width:200px;max-width:56%;height:auto;border:0;"
                    + "font-family:" + SERIF + ";font-size:20px;letter-spacing:3px;color:" + INK + ";\">"
                    + "<div style=\"height:1px;width:44px;background-color:" + DASH + ";margin:15px auto 0;\"></div>"
                    + "</td></tr>";
        }

        private String footer() {
            return "<tr><td align=\"center\" style=\"padding:22px 40px 28px;border-top:1px solid "
                    + FOOT_RULE + ";\">"
                    + "<div style=\"font-family:" + SERIF + ";font-size:14px;letter-spacing:3px;color:"
                    + MUTED + ";\">PILAR ESTILO</div>"
                    + "<p style=\"margin:8px 0 0;font-family:" + SANS + ";font-size:11px;line-height:1.7;"
                    + "color:" + MUTED + ";\">Valle de Aconcagua, Chile &nbsp;&middot;&nbsp; "
                    + "¿Dudas? Responde este correo y te contestamos.</p></td></tr>";
        }

        private static String escape(String value) {
            if (value == null) {
                return "";
            }
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }
}
```

- [ ] **Step 4: Fix the four `note(text)` call sites in `NotificationComposer.java`** so the module compiles (full copy pass is Task 3, this is just the compile fix):
  - `transferInstructionsHtml`: `.note("Antes de la fecha límite", "Sube tu comprobante desde Mi cuenta antes de las " + formatDeadline(deadline) + ". Sin comprobante, el pedido puede cancelarse y el stock quedará liberado.")`
  - `orderCancelledHtml`: `email.note("Motivo", r)`
  - `orderConfirmationHtml`: `.note("Si cambias de opinión", "Tienes 10 días desde que recibes el pedido para pedir la devolución, sin dar motivo, según la Ley del Consumidor.")`
  - `orderDelivered`: `.note("Si aún no llega", "Si aún no lo recibiste, responde este correo y lo revisamos.")`
  - `refundRegistered`: `.note("Cuándo lo verás", "Según tu banco puede tardar unos días en aparecer en tu cartola.")`

- [ ] **Step 5: Run `EmailLayoutTest` + the full module test, expect green**

Run: `cd services/notification-service && mvn -q -o test -Dtest=EmailLayoutTest && mvn -q -o test`
Expected: PASS (NotificationComposerTest may have stale assertions about the old `note` markup — if so, note the failures; Task 3 fixes them. If only `EmailLayoutTest` is asked, it passes.)

- [ ] **Step 6: Commit**

```bash
git add services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/EmailLayout.java \
        services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/NotificationComposer.java \
        services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/EmailLayoutTest.java
git commit -m "feat(notification-service): EmailLayout v2 primitives (eyebrow, route, code, orderSummary)"
```

---

## Task 2: Email preview harness

**Files:**
- Create: `services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/EmailPreviewTest.java`

**Interfaces consumed:** every `NotificationComposer` method that returns a `NotificationMessage` with a non-null `bodyHtml`.

- [ ] **Step 1: Write the harness test**

```java
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
        return new OrderView(UUID.randomUUID(), "PE-1042", UUID.randomUUID(), "PAID",
                total, Money.of(BigDecimal.ZERO, "CLP"), total, Money.of(BigDecimal.ZERO, "CLP"),
                BigDecimal.ZERO, total, "starken", "Starken", "REGIONAL",
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

    /** +1 per opening tag, -1 per closing; void/self-closing ignored. Zero means balanced enough. */
    private static int tagBalance(String html) {
        int depth = 0;
        var matcher = java.util.regex.Pattern.compile("<(/?)([a-zA-Z0-9]+)([^>]*?)(/?)>").matcher(html);
        java.util.Set<String> voidTags = java.util.Set.of("br", "img", "meta", "hr", "input", "!doctype");
        while (matcher.find()) {
            String name = matcher.group(2).toLowerCase();
            if (voidTags.contains(name) || !matcher.group(4).isEmpty()) continue;
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
        // paymentReceived + salesDocumentIssued + returns added as their tasks land
    }
}
```

Note: `WelcomeDiscount` and `OrderItemView` constructor arg order — verify against the records; adjust the fixture if the compiler complains. `orderConfirmation` currently produces HTML that references the old `note` markup — this test will surface that; Task 3 fixes it.

- [ ] **Step 2: Run, expect green (or a precise failure list to hand to Task 3)**

Run: `cd services/notification-service && mvn -q -o test -Dtest=EmailPreviewTest`

- [ ] **Step 3: Open `target/email-preview/*.html` in a browser, eyeball each.**

- [ ] **Step 4: Commit**

```bash
git add services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/EmailPreviewTest.java
git commit -m "test(notification-service): email preview harness + no-link invariant"
```

---

## Task 3: `NotificationComposer` — orderConfirmation to `orderSummary`, Chilean copy audit

**Files:**
- Modify: `services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/NotificationComposer.java`
- Test: `services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/NotificationComposerTest.java`

- [ ] **Step 1: Update `NotificationComposerTest.orderConfirmationListsItemsAndTheRetractoRight`**

```java
@Test
void orderConfirmationListsItemsAndTheRetractoRight() {
    var message = composer.orderConfirmation(order);
    assertThat(message.templateKey()).isEqualTo(NotificationMessage.ORDER_CONFIRMATION);
    assertThat(message.subject()).contains(REFERENCE);
    assertThat(message.bodyText()).contains("Vestido").contains("10 días");
    assertThat(message.bodyHtml())
            .contains("Pedido " + REFERENCE)
            .contains("Vestido")
            .contains("Mi cuenta").doesNotContain("<a ").doesNotContain("href=");
}
```

- [ ] **Step 2: Run, expect fail** (`bodyHtml` still has old markup / a link-ish route may be missing).

- [ ] **Step 3: Rewrite `orderConfirmationHtml`**

```java
private String orderConfirmationHtml(OrderView order, String reference, String total) {
    List<EmailLayout.Line> lines = order.items().stream()
            .map(item -> new EmailLayout.Line(
                    item.productName(),
                    lineVariantAndQty(item),
                    formatAmount(item.unitPrice().amount().toPlainString(), item.unitPrice().currency())))
            .toList();

    List<String[]> totals = new java.util.ArrayList<>();
    totals.add(new String[]{"Subtotal", formatAmount(
            order.subtotal().amount().toPlainString(), order.subtotal().currency())});
    if (order.discount().amount().signum() > 0) {
        totals.add(new String[]{"Descuento", "-" + formatAmount(
                order.discount().amount().toPlainString(), order.discount().currency())});
    }
    totals.add(new String[]{"Envío", shippingLine(order)});
    totals.add(new String[]{"Total", total});

    return EmailLayout.titled("Recibimos tu pedido")
            .eyebrow("Confirmación de pedido")
            .paragraph("Gracias por comprar en Pilar Estilo. Esto es lo que pediste; te escribimos "
                    + "por aquí en cada paso, desde la preparación hasta la entrega.")
            .orderSummary(reference, formatDate(Instant.now()), lines, totals)
            .route("Cómo ver el estado", "Entra a", "pilarestilo.com", "Mi cuenta › Pedidos")
            .note("Si cambias de opinión", "Tienes 10 días desde que recibes el pedido para pedir "
                    + "la devolución, sin dar motivo, según la Ley del Consumidor. La solicitas desde "
                    + "Mi cuenta › Pedidos.")
            .build();
}
```

Add the helper (used here and by `paymentReceived` in Task 5):

```java
/** "Crudo / M · x2", or just "x2" when the item has no real variant. */
private String lineVariantAndQty(OrderView.OrderItemView item) {
    String variant = java.util.stream.Stream.of(item.variantColor(), item.variantSize())
            .filter(v -> v != null && !v.isBlank())
            .reduce((a, b) -> a + " / " + b)
            .orElse(null);
    return (variant == null ? "" : variant + " · ") + "x" + item.quantity();
}
```

`formatDate(Instant)` already exists (used by `returnRequested`). If it renders `dd/MM/yyyy`, that is fine here.

- [ ] **Step 4: Chilean-Spanish audit.** Read every string literal in `NotificationComposer.java`. Confirm `tú` throughout; there must be no `tenés` / `podés` / `revisá` / `subí` / `escribí`. The file is already mostly correct — the known-good phrases (`Tienes 10 días`, `puedes hacer un nuevo pedido`, `Sube tu comprobante`, `Escribe ... en el mensaje`) stay. Fix any drift. Change nothing that is already correct.

- [ ] **Step 5: `note(text)` → `note(label, text)` sweep** for every remaining call site not touched in Task 1 Step 4 (`salesDocumentIssued` has none; `returnRequested`/`returnApproved` have none; verify with a grep `grep -n "\.note(" NotificationComposer.java` — every call must now pass two args).

- [ ] **Step 6: Run `NotificationComposerTest` + `EmailPreviewTest`, expect green.** Eyeball `target/email-preview/ORDER_CONFIRMATION.html`.

- [ ] **Step 7: Commit**

```bash
git add services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/NotificationComposer.java \
        services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/NotificationComposerTest.java
git commit -m "feat(notification-service): order confirmation uses orderSummary, Chilean copy audit"
```

---

## Task 4: `NotificationComposer` — HTML for `discountCodeAssigned` and `welcome`

**Files:**
- Modify: `services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/NotificationComposer.java`
- Test: `NotificationComposerTest.java`

- [ ] **Step 1: Add / update tests**

```java
@Test
void discountCodeAssignedNamesTheCodeInHtml() {
    var message = composer.discountCodeAssigned("VUELVE15");
    assertThat(message.bodyText()).contains("VUELVE15");
    assertThat(message.bodyHtml())
            .isNotNull()
            .contains("VUELVE15")
            .contains("Código de descuento")
            .doesNotContain("<a ").doesNotContain("href=");
}

@Test
void welcomeWithACouponNamesTheCodeAndItsConditions() {
    WelcomeDiscount coupon = new WelcomeDiscount(
            "BIENVENIDA20", "PERCENTAGE", java.math.BigDecimal.valueOf(20), null, LocalDate.of(2026, 9, 30));
    var message = composer.welcome("Camila Torres", coupon);
    assertThat(message.bodyText()).contains("BIENVENIDA20");
    assertThat(message.bodyHtml())
            .contains("BIENVENIDA20")
            .contains("Catálogo")
            .doesNotContain("<a ").doesNotContain("href=");
}
```

- [ ] **Step 2: Run, expect fail.**

- [ ] **Step 3: Give `discountCodeAssigned` HTML**

```java
public NotificationMessage discountCodeAssigned(String code) {
    return new NotificationMessage(
            NotificationMessage.DISCOUNT_CODE_ASSIGNED,
            "Tienes un código de descuento",
            "Guardamos un código de descuento para tu próxima compra en Pilar Estilo: " + code + "\n\n"
                    + "Lo escribes en el carrito, en Código de descuento, antes de pagar.\n",
            EmailLayout.titled("Tienes un código de descuento")
                    .eyebrow("Solo para ti")
                    .paragraph("Guardamos este código para tu próxima compra en Pilar Estilo.")
                    .code(code, "Escríbelo en el carrito, en “Código de descuento”, antes de pagar.")
                    .build(),
            Map.of("code", code),
            null);
}
```

- [ ] **Step 4: Rework the coupon branch of `welcome`**

Replace `email.highlight("Tu código de bienvenida", coupon.code()).note(condition + ". Válido hasta " + coupon.validUntil() + ".");` with:

```java
email.code(coupon.code(), condition + " · válido hasta " + coupon.validUntil())
     .route("Cómo usarlo", "Lo escribes en el carrito, en", "", "Código de descuento");
```

And after the `if (coupon != null)` block, for both paths, add a closing route to the catalogue:

```java
email.route("Empieza aquí", "Entra a", "pilarestilo.com", "Catálogo");
```

Keep the two `email.paragraph(...)` welcome lines. Keep `bodyText` as is (it is linkless `tú`).

- [ ] **Step 5: Run tests + `EmailPreviewTest` (add `discountCodeAssigned` + `welcome` dumps if not there), expect green. Eyeball.**

- [ ] **Step 6: Commit**

```bash
git add services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/NotificationComposer.java \
        services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/NotificationComposerTest.java
git commit -m "feat(notification-service): HTML for discount-code and welcome-coupon emails"
```

---

## Task 5: `paymentReceived(OrderView, PaymentView)`

**Files:**
- Modify: `services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/NotificationComposer.java`
- Test: `NotificationComposerTest.java`

**Interfaces produced:** `NotificationComposer.paymentReceived(OrderView order, PaymentView payment) → NotificationMessage`

- [ ] **Step 1: Test**

```java
@Test
void paymentReceivedReferencesTheOrderNotThePaymentUuid() {
    var message = composer.paymentReceived(order, payment);
    assertThat(message.templateKey()).isEqualTo(NotificationMessage.PAYMENT_RECEIVED);
    assertThat(message.subject()).isEqualTo("Pago confirmado — pedido " + REFERENCE);
    assertThat(message.subject()).doesNotContain(payment.id().toString());
    assertThat(message.bodyText())
            .contains(REFERENCE)
            .contains("preparando")
            .doesNotContain(payment.id().toString());
    assertThat(message.bodyHtml())
            .contains("Pago confirmado")
            .contains("Pedido " + REFERENCE)
            .contains("por transferencia")
            .doesNotContain("<a ").doesNotContain("href=");
    assertThat(message.referenceId()).isEqualTo(order.id());
}
```

- [ ] **Step 2: Run, expect compile fail** (signature is `(UUID)`).

- [ ] **Step 3: Replace `paymentReceived`**

```java
public NotificationMessage paymentReceived(OrderView order, PaymentView payment) {
    String reference = order.publicReference();
    String total = formatAmount(order.total().amount().toPlainString(), order.total().currency());
    int itemCount = order.items().stream().mapToInt(OrderView.OrderItemView::quantity).sum();
    String methodLabel = methodLabel(payment.method());

    String body = "Recibimos el pago de tu pedido " + reference + " " + methodLabel + ".\n\n"
            + "Ya estamos preparando el pedido; te avisamos por aquí apenas salga a despacho.\n\n"
            + "Puedes seguirlo en pilarestilo.com, en Mi cuenta > Pedidos.\n";

    Map<String, Object> data = new LinkedHashMap<>();
    data.put(KEY_ORDER_ID, order.id());
    data.put(KEY_ORDER_REFERENCE, reference);
    data.put("paymentId", payment.id());
    data.put("method", payment.method());
    data.put(KEY_TOTAL_AMOUNT, order.total().amount());
    data.put(KEY_CURRENCY, order.total().currency());

    return new NotificationMessage(
            NotificationMessage.PAYMENT_RECEIVED,
            "Pago confirmado — pedido " + reference,
            body,
            EmailLayout.titled("Estamos preparando tu pedido")
                    .eyebrow("Pago confirmado")
                    .paragraph("Recibimos tu pago. Ya estamos armando el paquete y te avisamos por "
                            + "aquí apenas salga a despacho.")
                    .orderSummary(reference, formatDate(payment.createdAt()), List.of(),
                            List.of(new String[]{"Productos", itemCount + " · " + total},
                                    new String[]{"Pago", capitalize(methodLabel)}))
                    .route("Cómo seguirlo", "Entra a", "pilarestilo.com", "Mi cuenta › Pedidos")
                    .build(),
            data,
            order.id());
}

private static String methodLabel(String method) {
    if (method == null) return "con tarjeta o transferencia";
    return switch (method) {
        case "TRANSFER" -> "por transferencia";
        case "MERCADO_PAGO" -> "con Mercado Pago";
        default -> "con tarjeta";
    };
}

private static String capitalize(String s) {
    return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
}
```

Delete the old `paymentReceived(UUID paymentId)` method entirely.

- [ ] **Step 4: Add the dump to `EmailPreviewTest.rendersEveryCustomerEmail`:** `dump(composer.paymentReceived(order, payment));`

- [ ] **Step 5: Run `NotificationComposerTest` + `EmailPreviewTest`, expect green.** (`DispatchersTest` and the module build now fail to compile at the old call site — Task 6 fixes that; if running the whole module, note it.) Eyeball `PAYMENT_RECEIVED.html`.

- [ ] **Step 6: Commit**

```bash
git add services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/NotificationComposer.java \
        services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/NotificationComposerTest.java \
        services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/EmailPreviewTest.java
git commit -m "feat(notification-service): paymentReceived is a real email about the order"
```

---

## Task 6: Dispatcher rewiring — load order + payment for `paymentReceived`; drop the `orderPreparing` email

**Files:**
- Modify: `services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/PaymentNotificationDispatcher.java`
- Modify: `services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/OrderNotificationDispatcher.java`
- Modify: `services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/NotificationComposer.java` (delete `orderPreparing`)
- Test: `services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/DispatchersTest.java`

**Interfaces consumed:** `NotificationComposer.paymentReceived(OrderView, PaymentView)` (Task 5).

- [ ] **Step 1: Update `DispatchersTest`** — find the `onPaymentConfirmed` test: it must now stub `orderReadPort.findById(orderId)` and `paymentReadPort.findById(paymentId)` and verify `notificationSender.send` received a message whose `subject` contains the reference. Find the `PREPARING_ORDER` test (in the `OrderNotificationDispatcher` section): assert `notificationSender.send` is **never** called for that status, but `inAppNotificationPort.notifyOrderPreparing` **is**. `SHIPPED` / `DELIVERED` unchanged.

```java
// onPaymentConfirmed
when(orderReadPort.findById(ORDER_ID)).thenReturn(Optional.of(order));
when(paymentReadPort.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
when(customerReadPort.findById(order.customerId())).thenReturn(Optional.of(customer));
dispatcher.onPaymentConfirmed(new Events.PaymentConfirmed(PAYMENT_ID, ORDER_ID, order.customerId()));
verify(notificationSender).send(argThat(m -> m.subject().contains(order.publicReference())), any());
verify(inAppNotificationPort).notifyPaymentReceived(order.customerId(), PAYMENT_ID);

// PREPARING_ORDER
dispatcher.onOrderStatusChanged(new Events.OrderStatusChanged(ORDER_ID, order.customerId(), "PREPARING_ORDER"));
verify(notificationSender, never()).send(any(), any());
verify(inAppNotificationPort).notifyOrderPreparing(order.customerId(), ORDER_ID);
```

Adjust the `Events.*` constructor shapes to the real records.

- [ ] **Step 2: Run, expect fail.**

- [ ] **Step 3: `PaymentNotificationDispatcher.onPaymentConfirmed`**

```java
public void onPaymentConfirmed(Events.PaymentConfirmed event) {
    orderReadPort.findById(event.orderId()).ifPresentOrElse(
        order -> paymentReadPort.findById(event.paymentId()).ifPresentOrElse(
            payment -> {
                NotificationRecipient recipient = customerReadPort.findById(order.customerId())
                        .map(this::recipientFor).orElse(NotificationRecipient.unknown());
                notificationSender.send(composer.paymentReceived(order, payment), recipient);
                inAppNotificationPort.notifyPaymentReceived(order.customerId(), event.paymentId());
            },
            () -> log.warn("Payment {} confirmed but not readable; no message", event.paymentId())),
        () -> log.warn("Order {} for payment {} not readable; no message",
                event.orderId(), event.paymentId()));
}
```

Add a `private static final Logger log = LoggerFactory.getLogger(PaymentNotificationDispatcher.class);` if the class does not already have one.

- [ ] **Step 4: `OrderNotificationDispatcher`** — make the email conditional:

```java
private java.util.Optional<NotificationMessage> compose(OrderView order, String status) {
    return switch (status) {
        case "PREPARING_ORDER" -> java.util.Optional.empty();
        case "SHIPPED" -> java.util.Optional.of(composer.orderShipped(order));
        case "DELIVERED" -> java.util.Optional.of(composer.orderDelivered(order));
        default -> throw new IllegalStateException("No message defined for status " + status);
    };
}
```

In `onOrderStatusChanged`, guard the send:

```java
java.util.Optional<NotificationMessage> message = compose(order.get(), event.newStatus());
customerReadPort.findById(event.customerId()).ifPresentOrElse(
        user -> {
            message.ifPresent(m -> notificationSender.send(m, recipientFor(user)));
            notifyInApp(user.id(), event);
        },
        () -> message.ifPresent(m -> notificationSender.send(m, NotificationRecipient.unknown())));
```

- [ ] **Step 5: Delete `NotificationComposer.orderPreparing(OrderView)`** (method only — the `NotificationMessage.ORDER_PREPARING` constant stays).

- [ ] **Step 6: Run `DispatchersTest` + full `mvn -q -o test` for the module, expect green.**

- [ ] **Step 7: Commit**

```bash
git add services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/PaymentNotificationDispatcher.java \
        services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/OrderNotificationDispatcher.java \
        services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/NotificationComposer.java \
        services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/DispatchersTest.java
git commit -m "refactor(notification-service): wire paymentReceived to the order; drop the redundant preparing email"
```

---

## Task 7: V105 migration + `PasswordResetToken` domain + persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V105__password_reset_attempt_count.sql`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/domain/model/PasswordResetToken.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/domain/ports/PasswordResetTokenRepository.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/persistence/entities/PasswordResetTokenEntity.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/persistence/repositories/PasswordResetTokenJpaRepository.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/persistence/repositories/PasswordResetTokenRepositoryAdapter.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/persistence/repositories/PasswordResetTokenRepositoryAdapterIT.java`

**Interfaces produced:**
- `PasswordResetToken.MAX_ATTEMPTS` (int, `5`)
- `PasswordResetToken.getAttemptCount() → int`
- `PasswordResetToken.recordFailedAttempt() → void`
- `PasswordResetToken.isUsable(Instant) → boolean` (now also `attemptCount < MAX_ATTEMPTS`)
- `PasswordResetToken.reconstruct(UUID, UUID, String, Instant, Instant, Instant, int)` — extra trailing `int attemptCount`
- `PasswordResetTokenRepository.findActiveByUserId(UUID) → Optional<PasswordResetToken>`

- [ ] **Step 1: Migration**

`backend/src/main/resources/db/migration/V105__password_reset_attempt_count.sql`:

```sql
ALTER TABLE password_reset_tokens ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
```

- [ ] **Step 2: Extend `PasswordResetTokenRepositoryAdapterIT`** — add:

```java
@Test
void findActiveByUserId_returns_the_newest_unused_unexpired_row() {
    repository.save(PasswordResetToken.issue(SEEDED_USER, "old-" + UUID.randomUUID(), Duration.ofMinutes(30)));
    PasswordResetToken newest = repository.save(
            PasswordResetToken.issue(SEEDED_USER, "new-" + UUID.randomUUID(), Duration.ofMinutes(30)));

    Optional<PasswordResetToken> found = repository.findActiveByUserId(SEEDED_USER);

    assertThat(found).isPresent();
    assertThat(found.get().getTokenHash()).isEqualTo(newest.getTokenHash());
}

@Test
void attempt_count_survives_a_round_trip() {
    PasswordResetToken saved = repository.save(
            PasswordResetToken.issue(SEEDED_USER, "h-" + UUID.randomUUID(), Duration.ofMinutes(30)));
    saved.recordFailedAttempt();
    saved.recordFailedAttempt();
    repository.save(saved);

    Optional<PasswordResetToken> reloaded = repository.findActiveByUserId(SEEDED_USER);
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getAttemptCount()).isEqualTo(2);
}
```

- [ ] **Step 3: Run the IT, expect compile fail.** (`SonarQube + app compose stack must be down.`)

- [ ] **Step 4: `PasswordResetToken.java`** — add fields + behaviour:

```java
public static final int MAX_ATTEMPTS = 5;
private int attemptCount;

// in issue(...): token.attemptCount = 0;

public boolean isUsable(Instant now) {
    return usedAt == null && now.isBefore(expiresAt) && attemptCount < MAX_ATTEMPTS;
}

public void recordFailedAttempt() {
    this.attemptCount++;
}

public int getAttemptCount() {
    return attemptCount;
}

// reconstruct gains a trailing int:
public static PasswordResetToken reconstruct(UUID id, UUID userId, String tokenHash,
        Instant expiresAt, Instant usedAt, Instant createdAt, int attemptCount) {
    PasswordResetToken token = new PasswordResetToken();
    token.id = id; token.userId = userId; token.tokenHash = tokenHash;
    token.expiresAt = expiresAt; token.usedAt = usedAt; token.createdAt = createdAt;
    token.attemptCount = attemptCount;
    return token;
}
```

- [ ] **Step 5: Entity** — add:

```java
@Column(name = "attempt_count", nullable = false)
private int attemptCount;

public int getAttemptCount() { return attemptCount; }
public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
```

- [ ] **Step 6: Port** — add to `PasswordResetTokenRepository`:

```java
/** The newest unused, unexpired token for the user, if any. Attempt-count lock is judged by the caller. */
Optional<PasswordResetToken> findActiveByUserId(UUID userId);
```

- [ ] **Step 7: JPA repo** — add:

```java
@Query("SELECT t FROM PasswordResetTokenEntity t WHERE t.userId = :userId AND t.usedAt IS NULL "
        + "AND t.expiresAt > :now ORDER BY t.createdAt DESC")
java.util.List<PasswordResetTokenEntity> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
```

- [ ] **Step 8: Adapter** — map `attemptCount` in `toEntity`/`toDomain`, implement `findActiveByUserId`:

```java
@Override
public Optional<PasswordResetToken> findActiveByUserId(UUID userId) {
    return jpaRepository.findActiveByUserId(userId, Instant.now()).stream().findFirst().map(this::toDomain);
}
// toEntity: entity.setAttemptCount(token.getAttemptCount());
// toDomain: ... entity.getCreatedAt(), entity.getAttemptCount());
```

- [ ] **Step 9: Run the IT, expect green. Run the auth-package unit tests (`mvn -q -o test -Dtest='com.pilarestilo.shared.auth.**'`) — `PasswordResetToken` / adapter callers may need the extra `reconstruct` arg; fix any compile error (pass `0`).**

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/db/migration/V105__password_reset_attempt_count.sql \
        backend/src/main/java/com/pilarestilo/shared/auth/domain/model/PasswordResetToken.java \
        backend/src/main/java/com/pilarestilo/shared/auth/domain/ports/PasswordResetTokenRepository.java \
        backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/persistence/
git commit -m "feat(auth): password_reset_tokens.attempt_count + findActiveByUserId (V105)"
```

---

## Task 8: `PasswordResetTokens.newCode` + `RequestPasswordResetUseCase` + mailer port

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/application/PasswordResetTokens.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/domain/ports/PasswordResetMailer.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/application/usecases/RequestPasswordResetUseCase.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/application/usecases/RequestPasswordResetUseCaseTest.java`

**Interfaces produced:**
- `PasswordResetTokens.newCode() → String` (6 digits, zero-padded)
- `PasswordResetMailer.sendResetCode(String email, String name, String code) → void`

- [ ] **Step 1: Update `RequestPasswordResetUseCaseTest`** — every `sendResetLink` → `sendResetCode`; the success test asserts the code arg matches `\d{6}`:

```java
verify(mailer).sendResetCode(eq("camila@example.com"), eq("Camila"),
        argThat((String c) -> c != null && c.matches("\\d{6}")));
```

`a_dead_smtp_host_does_not_blow_up_the_request`: `doThrow(...).when(mailer).sendResetCode(any(), any(), any());`

- [ ] **Step 2: Run, expect fail.**

- [ ] **Step 3: `PasswordResetTokens`** — add:

```java
/** A 6-digit numeric code, zero-padded. Low entropy on purpose — paired with a 30-minute TTL,
 *  single use, and a MAX_ATTEMPTS lock on the token row. */
public static String newCode() {
    return String.format("%06d", RANDOM.nextInt(1_000_000));
}
```

- [ ] **Step 4: Port** — `PasswordResetMailer`:

```java
void sendResetCode(String email, String name, String code);
```

(rename from `sendResetLink`).

- [ ] **Step 5: `RequestPasswordResetUseCase`** — change the token-issue + send:

```java
tokenRepository.invalidateUnusedForUser(user.getId());
String code = PasswordResetTokens.newCode();
tokenRepository.save(PasswordResetToken.issue(user.getId(), PasswordResetTokens.hash(code), TOKEN_TTL));

try {
    mailer.sendResetCode(user.getEmail(), user.getFullName(), code);
} catch (RuntimeException e) {
    log.warn("Could not send the password reset email for user {}", user.getId(), e);
}
```

If `TOKEN_TTL` should become configurable now, add a `@Value("${app.password-reset.code-ttl-minutes:30}") int` ctor arg and `Duration.ofMinutes(...)`; otherwise leave the constant and defer the config key to Task 10. **Decision: add the config arg here** so Task 10 only touches the mailer + yaml.

- [ ] **Step 6: Run `RequestPasswordResetUseCaseTest` + auth unit tests, expect green.** `SmtpPasswordResetMailer` will not compile (still `implements PasswordResetMailer` with `sendResetLink`) — Task 10 fixes it; if the whole module is compiled, note it.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/auth/application/PasswordResetTokens.java \
        backend/src/main/java/com/pilarestilo/shared/auth/domain/ports/PasswordResetMailer.java \
        backend/src/main/java/com/pilarestilo/shared/auth/application/usecases/RequestPasswordResetUseCase.java \
        backend/src/test/java/com/pilarestilo/shared/auth/application/usecases/RequestPasswordResetUseCaseTest.java
git commit -m "feat(auth): password reset issues a 6-digit code instead of a link"
```

---

## Task 9: `ResetPasswordUseCase.execute(email, code, newPassword)`

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/application/usecases/ResetPasswordUseCase.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/application/usecases/ResetPasswordUseCaseTest.java`

**Interfaces produced:** `ResetPasswordUseCase.execute(String email, String code, String newPassword) → void`

**Interfaces consumed:** `PasswordResetTokenRepository.findActiveByUserId`, `PasswordResetToken.recordFailedAttempt` / `isUsable` / `getTokenHash` (Task 7); `PasswordResetTokens.hash` (existing).

- [ ] **Step 1: Rewrite `ResetPasswordUseCaseTest`**

```java
package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.PasswordResetTokens;
import com.pilarestilo.shared.auth.domain.model.PasswordResetToken;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.domain.ports.PasswordResetTokenRepository;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseTest {

    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    ResetPasswordUseCase useCase;
    private final UUID userId = UUID.randomUUID();
    private User user;
    private static final String EMAIL = "camila@example.com";
    private static final String CODE = "418302";

    @BeforeEach
    void setUp() {
        useCase = new ResetPasswordUseCase(tokenRepository, userRepository, passwordEncoder);
        user = User.reconstruct(userId, EMAIL, "Camila", UserRole.CUSTOMER, true, "old-hash", Instant.now());
        lenient().when(passwordEncoder.encode("BrandNew123")).thenReturn("new-hash");
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private PasswordResetToken activeToken(String code, Duration ttl) {
        return PasswordResetToken.issue(userId, PasswordResetTokens.hash(code), ttl);
    }

    @Test
    void the_right_code_changes_the_password_bumps_the_session_version_and_marks_the_token_used() {
        PasswordResetToken token = activeToken(CODE, Duration.ofMinutes(30));
        when(tokenRepository.findActiveByUserId(userId)).thenReturn(Optional.of(token));

        useCase.execute(EMAIL, CODE, "BrandNew123");

        verify(userRepository).save(argThat(u -> u.getPasswordHash().equals("new-hash")));
        verify(tokenRepository).save(token);
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void a_wrong_code_records_a_failed_attempt_and_fails_with_the_generic_error() {
        PasswordResetToken token = activeToken(CODE, Duration.ofMinutes(30));
        when(tokenRepository.findActiveByUserId(userId)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> useCase.execute(EMAIL, "000000", "BrandNew123"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no es válido");
        assertThat(token.getAttemptCount()).isEqualTo(1);
        verify(tokenRepository).save(token);
    }

    @Test
    void the_fifth_wrong_code_leaves_the_token_locked() {
        PasswordResetToken token = PasswordResetToken.reconstruct(UUID.randomUUID(), userId,
                PasswordResetTokens.hash(CODE), Instant.now().plusSeconds(1800), null, Instant.now(), 4);
        when(tokenRepository.findActiveByUserId(userId)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> useCase.execute(EMAIL, "999999", "BrandNew123"))
                .isInstanceOf(DomainException.class);
        assertThat(token.getAttemptCount()).isEqualTo(5);
        assertThat(token.isUsable(Instant.now())).isFalse();
    }

    @Test
    void an_unknown_email_fails_with_the_generic_error() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute("nobody@example.com", CODE, "BrandNew123"))
                .isInstanceOf(DomainException.class).hasMessageContaining("no es válido");
    }

    @Test
    void no_active_token_fails_with_the_generic_error() {
        when(tokenRepository.findActiveByUserId(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(EMAIL, CODE, "BrandNew123"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void a_password_under_8_chars_is_rejected_before_anything_is_looked_up() {
        assertThatThrownBy(() -> useCase.execute(EMAIL, CODE, "short"))
                .isInstanceOf(DomainException.class).hasMessageContaining("8 caracteres");
    }
}
```

(If the `argThat` for `getUsedAt` reads awkwardly, simplify to `verify(tokenRepository).save(token); assertThat(token.getUsedAt()).isNotNull();`.)

- [ ] **Step 2: Run, expect fail.**

- [ ] **Step 3: Rewrite `ResetPasswordUseCase.execute`**

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Transactional
public void execute(String email, String code, String newPassword) {
    if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
        throw new DomainException("La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
    }
    if (email == null || email.isBlank() || code == null || code.isBlank()) {
        throw new DomainException(INVALID_LINK);
    }

    User user = userRepository.findByEmail(User.normalizeEmail(email)).orElse(null);
    if (user == null) {
        throw new DomainException(INVALID_LINK);
    }

    PasswordResetToken token = tokenRepository.findActiveByUserId(user.getId()).orElse(null);
    if (token == null || !token.isUsable(Instant.now())) {
        throw new DomainException(INVALID_LINK);
    }

    boolean matches = MessageDigest.isEqual(
            token.getTokenHash().getBytes(StandardCharsets.UTF_8),
            PasswordResetTokens.hash(code).getBytes(StandardCharsets.UTF_8));
    if (!matches) {
        token.recordFailedAttempt();
        tokenRepository.save(token);
        throw new DomainException(INVALID_LINK);
    }

    user.changePasswordHash(passwordEncoder.encode(newPassword));
    user.incrementSessionVersion();
    userRepository.save(user);
    token.markUsed(Instant.now());
    tokenRepository.save(token);
}
```

`INVALID_LINK` and `MIN_PASSWORD_LENGTH` constants already exist. Remove the now-unused
`PasswordResetTokens.hash(rawToken)` / `findByTokenHash` import path.

- [ ] **Step 4: Run `ResetPasswordUseCaseTest`, expect green.**

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/auth/application/usecases/ResetPasswordUseCase.java \
        backend/src/test/java/com/pilarestilo/shared/auth/application/usecases/ResetPasswordUseCaseTest.java
git commit -m "feat(auth): reset password by email + 6-digit code with attempt lock"
```

---

## Task 10: `AuthEmailLayout` + `SmtpPasswordResetMailer` + config

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/email/AuthEmailLayout.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/email/SmtpPasswordResetMailer.java`
- Modify: `backend/src/main/resources/application.yml`, `backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json`, `infra/.env.example`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/email/SmtpPasswordResetMailerTest.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/email/AuthEmailPreviewTest.java` (create)

**Interfaces produced:**
- `AuthEmailLayout.titled(String) → Builder` with `eyebrow`, `paragraph`, `code`, `route`, `note`, `build`
- `AuthEmailLayout.LOGO_CONTENT_ID`, `AuthEmailLayout.LOGO_RESOURCE`

- [ ] **Step 1: Create `AuthEmailLayout.java`** — a minimal copy of the notification-service `EmailLayout` (Task 1), keeping only `titled`/`Builder`/`eyebrow`/`paragraph`/`code`/`route`/`note`/`build`/`header`/`footer`/`escape` and the colour + font constants + logo constants (`LOGO_RESOURCE = "email/pilar-estilo-logo.png"`). No `orderSummary`, no `details`, no `Line`. Class javadoc:

```java
/**
 * The email look, duplicated small on purpose. The password-reset mailer must not depend on
 * {@code notification-service} — recovery has to work when the notification pipeline is down —
 * so it cannot reach the richer {@code EmailLayout} that lives there. This is the subset the
 * reset email needs; a change to the shared look is a two-file edit (here and there).
 */
```

Copy the method bodies verbatim from Task 1's `EmailLayout` for the kept methods.

- [ ] **Step 2: Update `SmtpPasswordResetMailerTest`**

The `RecordingMailer` test-seam subclass and the two send tests: `sendResetLink` → `sendResetCode`;
assert the body contains the 6-char code, no `http`, no `<a `:

```java
@Test
void the_email_carries_the_code_and_no_link() throws Exception {
    smtpConfigured();
    RecordingMailer mailer = new RecordingMailer(systemSettingsRepository, "unused");
    mailer.sendResetCode("cliente@example.com", "Camila", "418302");

    String body = textOf(mailer.sent);
    assertThat(mailer.sent.getSubject()).isEqualTo("Restablece tu contraseña — Pilar Estilo");
    assertThat(body).contains("418302").doesNotContain("http").doesNotContain("<a ");
}
```

Keep `is_a_no_op_when_smtp_is_not_configured` (rename the method call to `sendResetCode`).

- [ ] **Step 3: Run, expect fail.**

- [ ] **Step 4: `SmtpPasswordResetMailer`**

- Implements `PasswordResetMailer.sendResetCode(String toEmail, String greetingName, String code)`.
- Drop the `linkBaseUrl` ctor param and `@Value("${app.password-reset.link-base-url…}")`. Drop
  `tokenTtlMinutes` too if only used for copy — the email says "30 minutos" as literal text
  matching the default, OR keep a `@Value("${app.password-reset.code-ttl-minutes:30}") int
  codeTtlMinutes` and interpolate. **Decision: keep `codeTtlMinutes`, interpolate.**
- Body:

```java
String greeting = greetingName == null || greetingName.isBlank() ? "Hola" : "Hola " + greetingName;
String html = AuthEmailLayout.titled("Código para cambiar tu contraseña")
        .eyebrow("Seguridad")
        .paragraph(greeting + ". Recibimos una solicitud para cambiar la contraseña de tu cuenta. "
                + "Si fuiste tú, usa este código:")
        .code(code, null)
        .route("Cómo usarlo", "Entra a", "pilarestilo.com",
                "Iniciar sesión › ¿Olvidaste tu contraseña?")
        .paragraph("Escribe tu correo y el código, y elige tu nueva contraseña.")
        .note("Importante", "El código vence en " + codeTtlMinutes + " minutos y se usa una sola "
                + "vez. Si no fuiste tú, ignora este correo: tu contraseña actual sigue válida.")
        .build();

String text = greeting + ".\n\n"
        + "Recibimos una solicitud para cambiar la contraseña de tu cuenta en Pilar Estilo.\n\n"
        + "Tu código: " + code + "\n\n"
        + "Entra a pilarestilo.com, abre \"¿Olvidaste tu contraseña?\", escribe tu correo y el "
        + "código, y elige una nueva contraseña.\n\n"
        + "El código vence en " + codeTtlMinutes + " minutos y se usa una sola vez. "
        + "Si no fuiste tú, ignora este correo.\n";
```

- The logo attachment part (the `helper.addInline(...)` / `cid:` wiring) stays; the resource is
  `email/pilar-estilo-logo.png` on the monolith classpath (already there).

- [ ] **Step 5: Config cleanup**
  - `application.yml`: remove `app.password-reset.link-base-url`, add `app.password-reset.code-ttl-minutes: 30` and `app.password-reset.max-attempts: 5` under the existing `app.password-reset` group.
  - `additional-spring-configuration-metadata.json`: remove the `link-base-url` entry, add the two new keys with descriptions.
  - `infra/.env.example`: remove any `APP_PASSWORD_RESET_LINK_BASE_URL` line. Add a comment that the VPS `.env` files can drop it (harmless if left).
  - `PasswordResetToken.MAX_ATTEMPTS` stays a constant (5); `max-attempts` config is metadata-only for now unless `RequestPasswordResetUseCase`/`ResetPasswordUseCase` are wired to read it — **YAGNI: keep the constant, add the metadata key so the number is documented, do not wire it.** Actually — drop `app.password-reset.max-attempts` entirely to avoid an unused key. Only add `code-ttl-minutes`.

- [ ] **Step 6: Create `AuthEmailPreviewTest`**

```java
package com.pilarestilo.shared.auth.infrastructure.email;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEmailPreviewTest {

    @Test
    void rendersTheResetEmailWithoutALink() throws Exception {
        String html = AuthEmailLayout.titled("Código para cambiar tu contraseña")
                .eyebrow("Seguridad")
                .paragraph("Hola Camila. Usa este código:")
                .code("418302", null)
                .route("Cómo usarlo", "Entra a", "pilarestilo.com", "Iniciar sesión › ¿Olvidaste tu contraseña?")
                .note("Importante", "El código vence en 30 minutos.")
                .build();

        assertThat(html).contains("Seguridad").contains("418302")
                .doesNotContain("<a ").doesNotContain("href=").doesNotContain("http");

        Path out = Path.of("target", "email-preview");
        Files.createDirectories(out);
        Files.writeString(out.resolve("PASSWORD_RESET.html"), html);
    }
}
```

- [ ] **Step 7: Run both tests, expect green. Open `target/email-preview/PASSWORD_RESET.html`.**

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/email/ \
        backend/src/main/resources/application.yml \
        backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json \
        infra/.env.example \
        backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/email/
git commit -m "feat(auth): brand-look reset email with a code, no link (AuthEmailLayout)"
```

---

## Task 11: `AuthController` + `ResetPasswordRequest`

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/web/requests/ResetPasswordRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/web/AuthController.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/web/PasswordResetControllerIT.java`

- [ ] **Step 1: Update `PasswordResetControllerIT`** — the `POST /auth/reset-password` body is now
`{"email": "...", "code": "418302", "newPassword": "..."}`. Add:

```java
@Test
void reset_with_the_right_code_changes_the_password() throws Exception {
    String email = register();                 // existing helper that returns the seeded email
    mvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("email", email)))).andExpect(status().isOk());
    org.mockito.ArgumentCaptor<String> codeCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(passwordResetMailer).sendResetCode(eq(email), any(), codeCaptor.capture());
    String code = codeCaptor.getValue();
    assertThat(code).matches("\\d{6}");

    mvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("email", email, "code", code, "newPassword", "BrandNew1"))))
            .andExpect(status().isOk());

    mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("email", email, "password", "BrandNew1"))))
            .andExpect(status().isOk());
}

@Test
void a_wrong_code_is_a_generic_400_and_the_fifth_locks_the_code() throws Exception {
    String email = register();
    mvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("email", email)))).andExpect(status().isOk());

    for (int i = 0; i < 5; i++) {
        mvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", email, "code", "000000", "newPassword", "BrandNew1"))))
                .andExpect(status().isBadRequest());
    }
    // even the right code no longer works — the row is locked
    org.mockito.ArgumentCaptor<String> codeCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(passwordResetMailer).sendResetCode(eq(email), any(), codeCaptor.capture());
    mvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON)
            .content(om.writeValueAsString(Map.of("email", email, "code", codeCaptor.getValue(), "newPassword", "BrandNew1"))))
            .andExpect(status().isBadRequest());
}
```

The mailer is a `@MockitoBean PasswordResetMailer` in this IT (the current file mocks it — keep
whatever field name it uses and adjust the two captor lines). Match the existing IT's helper names
(`register` / `registerAndReturnEmail`, `om`, `mvc`) — read the file first. `@TestPropertySource` on
the class already raises the login rate limit (standing IT lesson in the repo) — keep it.

- [ ] **Step 2: Run, expect fail.**

- [ ] **Step 3: `ResetPasswordRequest`**

```java
package com.pilarestilo.shared.auth.infrastructure.web.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos") String code,
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must have at least 8 characters") String newPassword
) {}
```

- [ ] **Step 4: `AuthController.resetPassword`**

```java
@PostMapping("/reset-password")
@ResponseStatus(HttpStatus.OK)
public void resetPassword(@RequestBody @Valid ResetPasswordRequest req) {
    resetPasswordUseCase.execute(req.email(), req.code(), req.newPassword());
}
```

- [ ] **Step 5: Run `PasswordResetControllerIT`, expect green** (SonarQube + app stack down).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/web/ \
        backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/web/PasswordResetControllerIT.java
git commit -m "feat(auth): /auth/reset-password takes email + code + newPassword"
```

---

## Task 12: Frontend — reset form + api

**Files:**
- Modify: `frontend/src/lib/api.ts:1350-1356` (the `resetPassword` function)
- Modify: `frontend/src/islands/auth/ForgotPasswordForm.tsx`
- Modify: `frontend/src/islands/auth/ResetPasswordForm.tsx`
- Test: `frontend/src/islands/auth/__tests__/ResetPasswordForm.test.tsx`
- Test: `frontend/src/islands/auth/__tests__/ForgotPasswordForm.test.tsx`

**Interfaces produced:** `resetPassword(email: string, code: string, newPassword: string): Promise<void>`

- [ ] **Step 1: Rewrite `ResetPasswordForm.test.tsx`**

```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ResetPasswordForm from '../ResetPasswordForm';
import { ApiError } from '@/lib/api';

const resetPassword = vi.fn();
vi.mock('@/lib/api', async () => {
  const actual = await vi.importActual<typeof import('@/lib/api')>('@/lib/api');
  return { ...actual, resetPassword: (...a: unknown[]) => resetPassword(...a) };
});

beforeEach(() => vi.clearAllMocks());

async function fill(user: ReturnType<typeof userEvent.setup>, over: Partial<Record<'email'|'code'|'pass'|'confirm', string>> = {}) {
  await user.type(screen.getByLabelText(/correo/i), over.email ?? 'camila@example.com');
  await user.type(screen.getByLabelText(/código/i), over.code ?? '418302');
  await user.type(screen.getByLabelText(/nueva contraseña/i), over.pass ?? 'BrandNew123');
  await user.type(screen.getByLabelText(/repite la contraseña/i), over.confirm ?? 'BrandNew123');
}

describe('ResetPasswordForm', () => {
  it('submits email, code and password', async () => {
    resetPassword.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);
    await fill(user);
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));
    await waitFor(() => expect(resetPassword).toHaveBeenCalledWith('camila@example.com', '418302', 'BrandNew123'));
    expect(await screen.findByText(/contraseña actualizada/i)).toBeInTheDocument();
  });

  it('blocks submit when the code is not 6 digits', async () => {
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);
    await fill(user, { code: '123' });
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));
    expect(await screen.findByText(/6 dígitos/i)).toBeInTheDocument();
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it('shows a generic inline error on a 400', async () => {
    resetPassword.mockRejectedValue(new ApiError('x', 400));
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);
    await fill(user);
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));
    expect(await screen.findByText(/no es válido o ya expiró/i)).toBeInTheDocument();
  });

  it('rejects mismatched passwords before calling the API', async () => {
    const user = userEvent.setup();
    render(<ResetPasswordForm locale="es" />);
    await fill(user, { confirm: 'Different123' });
    await user.click(screen.getByRole('button', { name: /guardar contraseña/i }));
    expect(await screen.findByText(/no coinciden/i)).toBeInTheDocument();
    expect(resetPassword).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run, expect fail.**

- [ ] **Step 3: `api.ts`**

```ts
export async function resetPassword(email: string, code: string, newPassword: string): Promise<void> {
  return apiFetch<void>('/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify({ email, code, newPassword }),
  });
}
```

- [ ] **Step 4: Rewrite `ResetPasswordForm.tsx`**

- Remove: the `token` state, the `linkDead` state, the `useEffect` that reads `?token`, and the
  `if (linkDead) return <OutcomeCard .../>` branch.
- State: `email`, `code`, `password`, `confirm`, `showPass`, `loading`, `done`, `error`.
- `COPY[locale]` adds `emailLabel` (`'Correo electrónico'` / `'Email'`), `codeLabel`
  (`'Código de 6 dígitos'` / `'6-digit code'`), `codePlaceholder` (`'000000'`).
- `passwordValidationError` gains a first check: `if (!/^\d{6}$/.test(code)) return es ? 'El código
  tiene 6 dígitos.' : 'The code is 6 digits.';` — actually keep code validation separate:
  add `function codeError(code, es)` returning the message or null; `handleSubmit` checks it
  before `passwordValidationError`.
- `submitErrorOutcome`: drop the `linkDead` return; a 400 returns
  `{ message: es ? 'El código no es válido o ya expiró. Pídelo de nuevo desde "¿Olvidaste tu
  contraseña?".' : 'The code is invalid or expired. Request a new one.' }`; 429 unchanged.
  Return type is now just `string` (no `linkDead`).
- `handleSubmit`:

```tsx
async function handleSubmit(e: React.FormEvent) {
  e.preventDefault();
  setError('');
  const ce = codeError(code, es);
  if (ce) { setError(ce); return; }
  const ve = passwordValidationError(password, confirm, es);
  if (ve) { setError(ve); return; }
  setLoading(true);
  try {
    await resetPassword(email.trim(), code.trim(), password);
    setDone(true);
  } catch (err) {
    setError(submitErrorMessage(err, es));
  } finally {
    setLoading(false);
  }
}
```

- Form fields, in order: email (`type="email"`, `autoComplete="email"`), code
  (`inputMode="numeric"`, `pattern="\\d*"`, `maxLength={6}`, `autoComplete="one-time-code"`),
  new password + confirm (keep the existing show/hide toggle block).
- The `done` `OutcomeCard` success screen stays exactly as is.

- [ ] **Step 5: `ForgotPasswordForm.tsx`** — success screen copy only. Find the `sent` branch text
and change `te enviamos un enlace para restablecer tu contraseña` →
`te enviamos un código para cambiar tu contraseña`, and English `we sent a link` → `we sent a code`.
Update `ForgotPasswordForm.test.tsx`: the assertion `/enlace/i` → `/código/i`.

- [ ] **Step 6: Run**

```bash
cd frontend && ./node_modules/.bin/tsc --noEmit \
  && npx vitest run src/islands/auth/__tests__/ResetPasswordForm.test.tsx src/islands/auth/__tests__/ForgotPasswordForm.test.tsx \
  && npm run build
```

Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/islands/auth/
git commit -m "feat(auth): reset-password page takes email + 6-digit code"
```

---

## Task 13: Full verification + preview review

**Files:** none (verification only).

- [ ] **Step 1: notification-service** — `cd services/notification-service && mvn -q -o test`. Expect green. Open every `services/notification-service/target/email-preview/*.html`.

- [ ] **Step 2: monolith** — stop SonarQube and any app compose stack, then `cd backend && mvn -o clean verify`. Expect 0 failures. Open `backend/target/email-preview/PASSWORD_RESET.html`.

- [ ] **Step 3: frontend** — `cd frontend && ./node_modules/.bin/tsc --noEmit && npx vitest run && npm run build`. Expect green.

- [ ] **Step 4: `ReadOnlyMappingIT`** (part of `mvn verify`) green — confirms V105 did not break the notification-service RO mapping (it should not; that service does not map `password_reset_tokens`).

- [ ] **Step 5: Local Docker smoke (optional but recommended before master):** bring up the stack per CLAUDE.md, register a user → check the WELCOME email HTML in the notification-service logs / mail catcher; run a checkout to `PAID` → check `PAYMENT_RECEIVED`; hit `/auth/forgot-password` → confirm the email carries a 6-digit code and no link, then `/auth/reset-password` with it.

- [ ] **Step 6: Report to the owner** — summary + the preview screenshots. Do **not** push to master without explicit approval.

---

## Self-review notes

- **Spec coverage:** Part A → Task 1; preview harness → Tasks 2, 10(step 6); Part B `orderConfirmation`/copy → Task 3; `discountCodeAssigned`/`welcome` → Task 4; `paymentReceived` → Task 5; Part C → Task 6; `orderPreparing` email drop → Task 6; Part D-1..D-4 → Task 7; D-2/D-5 + mailer port → Task 8; D-6 → Task 9; D-8 → Task 10; D-7 → Task 11; Part E → Task 12; Part F tests → folded into each task + Task 13. Deferred product thumbnails: explicitly out (spec says so).
- **Rollout risk** (in-flight reset links break): no task needed — it is a deploy-note, covered in Task 13 step 6 / the spec's Rollout section.
- **`app.password-reset.max-attempts`**: resolved to "constant only, no config key" in Task 10 step 5 to avoid an unused key — matches the domain constant `PasswordResetToken.MAX_ATTEMPTS`.
