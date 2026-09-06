# Transactional Email Redesign — Design

**Goal:** Give every customer-facing transactional email one refined visual identity, remove all
clickable links and buttons from them, and convert password reset from a tokenised link to a
6-digit code — all copy in Chilean Spanish.

**Architecture:** Keep the Java string-builder approach (`EmailLayout` in `notification-service`,
a slim copy in the monolith for the self-contained password-reset mailer). No template engine.
Add layout primitives (`eyebrow`, `route`, `code`, `orderSummary`), drop the CTA button idea
entirely, and replace every "go here" with a plain-text navigation path. Password reset gains an
`attempt_count` column and a new `email + code + newPassword` contract; the reset page becomes a
3-field form instead of a URL consumer.

**Tech Stack:** Spring Boot 4.0.7 / Java 25, Flyway (new migration **V105**), Testcontainers +
MockMvc ITs, JUnit 5 + Mockito, `tools.jackson`. Astro 5 SSR + React islands, Vitest + happy-dom
(`./node_modules/.bin/tsc --noEmit`, `npx vitest run <path>`). `notification-service` is Kafka-only
and read-only on the shared DB under `ddl-auto: validate`.

**Spec:** this document (brainstorm 2026-09-06, visual direction approved via three companion
screens under `.superpowers/brainstorm/890-1788725519/content/`).

## Global Constraints

- **No clickable elements in customer emails.** No `<a href>`, no `<button>`, no `mailto:`, no
  bare URL that a client would auto-link as the primary action. Where the customer must act, the
  email states the site plus the menu path as text (`En pilarestilo.com, entra a Mi cuenta ›
  Pedidos`). Enforced by a test that greps every rendered HTML body for `<a ` and `href=`.
- **Chilean Spanish, standard `tú`.** Never voseo (`tenés`, `podés`, `revisá`, `mandá`). Imperatives
  are `revisa`, `escribe`, `sube`, `entra`, `gestiona`. Match the register already in
  `NotificationComposer`.
- **Email HTML rules (unchanged, carry from current `EmailLayout`):** table layout not flexbox,
  inline styles, 600px max width, no web fonts (font stacks with real fallbacks), logo via
  `cid:` attachment, explicit colours on every element, `role="presentation"` on layout tables.
- **Password reset stays self-contained in the monolith.** It must not call `notification-service`,
  Kafka, or `system_settings.notification_providers`. It keeps its own SMTP path and its own
  (duplicated, minimal) layout class. Account recovery has to work when the notification pipeline
  is down.
- **Enumeration-safe.** `POST /auth/forgot-password` returns the same 200 body whether or not the
  address matches an account. Every reset failure (unknown email, wrong code, expired, used,
  too many attempts) returns the one message `El enlace no es válido o ya expiró` — keep the
  wording so existing copy/tests do not churn, even though there is no longer a link.
- **Palette / type (from `DESIGN.md`):** parchment `#F5F1EB`, surface `#FFFFFF`, ink `#1A1A1A`,
  body `#3A3A3A`, rose `#B76E79`, rose-deep `#8E4F58`, rose-soft `#E8C9CC`, border `#E7DED0`,
  muted `#8A8078`, cream inset `#FBF8F3`. Serif `'Cormorant Garamond', Georgia, 'Times New Roman',
  serif`; sans `Montserrat, 'Helvetica Neue', Helvetica, Arial, sans-serif`.

---

## Part A — `EmailLayout` v2 (notification-service)

**File:** `services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/EmailLayout.java`

Keep the `titled(String) → Builder → build()` shape and the `LOGO_CONTENT_ID` / `LOGO_RESOURCE`
constants. Rework the palette constants to the values above (`CREAM` → parchment `#F5F1EB`,
`BORDER` → `#E7DED0`, add `INSET = #FBF8F3`, `ROSE_DEEP = #8E4F58`).

### New / changed builder methods

| Method | Renders |
|---|---|
| `eyebrow(String text)` | 10px Montserrat, `letter-spacing:2.5px`, uppercase, colour `ROSE`, `margin-bottom:10px`. Sits directly above the `<h1>`. One per message. |
| `paragraph(String)` | unchanged (15px, `line-height:1.7`, `BODY_INK`) |
| `code(String value, String caption)` | centred block, `1px dashed #C9A9AC` border, `INSET` background, `padding:22px 20px`. `value` in serif, large, `letter-spacing` wide, colour `ROSE_DEEP`. `caption` (nullable) below in 12px `MUTED`. Used for the reset code and the welcome coupon. |
| `route(String label, String pathText)` | `1px solid BORDER` box, `INSET` background, `padding:15px 20px`, `margin:4px 0 18px`. `label` = small uppercase `MUTED` tag; `pathText` = 13px body. The caller passes already-composed text; menu crumbs are wrapped by the caller in `<strong>` — `route` does **not** parse markup, it takes two escaped strings and one may contain a single pre-approved `<strong>…›…</strong>` span, so `route` needs a variant that does not escape, or a `crumb(site, path)` helper that builds the safe HTML. **Decision:** `route(String label, String leadText, String site, String crumbPath)` — all four escaped, assembled as `{leadText} <strong>{site}</strong>, {crumbPath}` with the crumb separator `›` inserted by the builder. |
| `orderSummary(String reference, String dateText, List<Line> lines, List<String[]> totals)` | the unified block. Header row: `Pedido {reference}` left (13px 600), `dateText` right (12px MUTED), `INSET` background, bottom border. Then one row per `Line` (name 13px ink, `variant · x{qty}` 12px MUTED on the next line, price right-aligned 13px, `white-space:nowrap`). Then a divider and the `totals` rows (label left MUTED, value right); the row whose label is `Total` renders 15px ink 600 with a top border. `Line` is a new nested record `EmailLayout.Line(String name, String variantAndQty, String price)`. **No product image** in this iteration (see Deferred). |
| `details(List<String[]>)` | keep for now (transfer instructions, sales document, refund still use it). |
| `note(String text)` | **drop the `border-left:3px`.** Full `1px solid BORDER`, `INSET` background, `padding:16px 20px`, a small uppercase `MUTED` label passed as a new first arg → `note(String label, String text)`. Update the four call sites. |

### Header / footer / shell

- `header()`: logo centred (`text-align:center`), `padding:34px 40px 20px`, a `1px` rose rule
  (`#E8C9CC`, `width:44px`, centred) under the logo, no bottom border on the header cell.
- `build()` shell: body background `PARCHMENT`; card `1px solid BORDER` + `box-shadow:0 1px 3px
  rgba(26,26,26,.06)` (many clients drop the shadow, acceptable); `<h1>` `font-weight:300`,
  `font-size:26px`; content cell `padding:12px 40px 10px`.
- `footer()`: `padding:22px 40px 28px`, `margin-top:14px`, `border-top:1px solid #EFE7DC`,
  `text-align:center`. Wordmark `PILAR ESTILO` in serif 14px `letter-spacing:3px` `MUTED`, then
  one 11px line: `Valle de Aconcagua, Chile · ¿Dudas? Responde este correo y te contestamos.`
  **No "gestiona tus notificaciones" link** — if that guidance is wanted it goes in a `route`
  block in the body of the relevant emails, not the footer, and not as a link.
- Dark mode: add a `<style>` block in `<head>` with `@media (prefers-color-scheme: dark)` that
  swaps background/text/border tokens. Gmail strips `<style>` and shows the light (bulletproof)
  version; Apple Mail / Outlook.com honour it. Keep it to ~8 token overrides, no layout changes.

### `escape()` — keep, it already covers `& < > " '`.

### Deferred (not in this spec)

Product thumbnails in `orderSummary`. `OrderView.OrderItemView` has no image URL; adding one means
extending the read projection (`OrderItemRoEntity` join to `products.image_url`, the read adapter
query, the `OrderItemView` record, the mapper). Tracked as a follow-up increment.

---

## Part B — `NotificationComposer` copy + gaps

**File:** `services/notification-service/src/main/java/com/pilarestilo/notificationservice/application/NotificationComposer.java`

### `paymentReceived` — real HTML, references the order

Change the signature from `paymentReceived(UUID paymentId)` to
`paymentReceived(OrderView order, PaymentView payment)`.

```
subject  = "Pago confirmado — pedido " + order.publicReference()
bodyText = "Recibimos el pago de tu pedido " + reference + ".\n\n"
         + "Ya estamos preparando el pedido; te avisamos por aquí apenas salga a despacho.\n\n"
         + "Puedes seguirlo en pilarestilo.com, en Mi cuenta > Pedidos.\n"
bodyHtml = EmailLayout.titled("Estamos preparando tu pedido")
             .eyebrow("Pago confirmado")
             .paragraph("Recibimos tu pago. Ya estamos armando el paquete y te avisamos "
                      + "por aquí apenas salga a despacho.")
             .orderSummary(reference, formatDate(payment.createdAt()),
                           List.of(),  // compact: no line items in this email
                           List.of(new String[]{"Productos", itemCount + " · " + total},
                                   new String[]{"Pago", methodLabel(payment.method())}))
             .route("Cómo seguirlo", "En", "pilarestilo.com", "Mi cuenta › Pedidos")
             .build()
referenceId = order.id()
```

`data` map: `orderId`, `orderReference`, `paymentId`, `method`, `totalAmount`, `currency`.
Drop the raw-UUID subject and body. `PaymentView` exposes `method` (`TRANSFER` / gateway code) and
`createdAt` (no `paidAt`); `methodLabel(String)` is a small private mapper —
`TRANSFER → "por transferencia"`, `MERCADO_PAGO → "con Mercado Pago"`, else `"con tarjeta"`.

### `orderPreparing` — keep in-app, drop the email

`orderPreparing(OrderView order)` returns a `NotificationMessage` with **`bodyHtml = null` and
`bodyText` unchanged** is not enough — a text email would still go out. Instead:

- `NotificationMessage` gains no new field. `OrderNotificationDispatcher.compose` stops calling
  `composer.orderPreparing` for the email path.
- **`OrderNotificationDispatcher.onOrderStatusChanged`:** for `PREPARING_ORDER`, skip
  `notificationSender.send(...)` entirely and only call `notifyInApp(...)`. `SHIPPED` / `DELIVERED`
  keep both. Concretely: `compose` returns `Optional<NotificationMessage>` (empty for
  `PREPARING_ORDER`), and the send is guarded.
- `NotificationComposer.orderPreparing` is **deleted** (method + its `ORDER_PREPARING` usage in
  the composer). The constant `NotificationMessage.ORDER_PREPARING` **stays** (in-app + WhatsApp
  still use the string), as do `NotificationType.ORDER_PREPARING`,
  `InAppNotificationPort.notifyOrderPreparing`, the `TwilioWhatsAppNotificationSender` case, and
  the frontend `NotificationHistory` / `api.ts` entries — untouched.

### `discountCodeAssigned` — add HTML

```
bodyHtml = EmailLayout.titled("Tienes un código de descuento")
             .eyebrow("Solo para ti")
             .paragraph("Guardamos este código para tu próxima compra en Pilar Estilo.")
             .code(code, "Escríbelo en el carrito, en Código de descuento, antes de pagar.")
             .build()
```

### `welcome` — coupon uses `code`, add a route

Replace the `highlight(...).note(...)` coupon rendering with
`.code(coupon.code(), couponCondition(coupon) + " · válido hasta " + coupon.validUntil())`
followed by
`.route("Cómo usarlo", "Lo escribes en el carrito, en", "", "Código de descuento")` — when
`site` is blank the builder omits the `pilarestilo.com` span and just renders the crumb.
Base welcome (no coupon) gets a `route("Empieza aquí", "Entra a", "pilarestilo.com", "Catálogo")`.

### Chilean-Spanish audit

Read every string literal in the file. It is mostly correct `tú` already
(`Tienes 10 días`, `puedes hacer un nuevo pedido`, `Sube tu comprobante`). Fix any `arréglalo`
that reads as voseo. The mockups drifted to voseo — the file is the source of truth, not the
mockups. Add nothing that says `tenés` / `podés` / `revisá`.

### `orderConfirmationHtml` — adopt `orderSummary`

Replace the `highlight(LABEL_NUMERO_PEDIDO, reference).details(lines).details(amounts)` stack with
one `orderSummary(reference, formatDate(now), lines-as-Line, amounts)` call, then the
`route("Cómo ver el estado", "Entra a", "pilarestilo.com", "Mi cuenta › Pedidos")`, then
`note("Si cambias de opinión", "Tienes 10 días desde que recibes el pedido para pedir la
devolución, sin dar motivo. La solicitas desde Mi cuenta › Pedidos.")`. Keep `bodyText` as is
(already linkless).

### The other HTML emails (`transferInstructions`, `salesDocumentIssued`, `returnRequested`,
`returnApproved`, `refundRegistered`, `orderCancelled`, `orderShipped`, `orderDelivered`)

Minimal pass: swap `note(text)` → `note(label, text)` at each call site, confirm no `<a>`/link
copy, confirm Chilean `tú`. `orderShipped` / `orderDelivered` add
`route("Cómo verlo", "Entra a", "pilarestilo.com", "Mi cuenta › Pedidos")` in place of the
"avísanos desde tu cuenta" prose where it points at an action.

---

## Part C — Dispatcher rewiring for `paymentReceived`

**File:** `services/notification-service/.../application/PaymentNotificationDispatcher.java`

`onPaymentConfirmed(Events.PaymentConfirmed event)` currently calls
`composer.paymentReceived(event.paymentId())` and has the order in hand. Change to:

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

The old `NotificationRecipient.unknown()` fallback path (order not readable) drops — with no order
there is no reference to write and no customer to reach; a warn log is the right outcome.

---

## Part D — Password reset → 6-digit code (monolith)

### D-1 Migration

**File:** `backend/src/main/resources/db/migration/V105__password_reset_attempt_count.sql`

```sql
ALTER TABLE password_reset_tokens ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
```

The existing `token_hash` column now stores `hash(code)` instead of `hash(urlToken)` — same
column, same `CHAR(64)` SHA-256 hex, no rename. In-flight rows from before the deploy stop
working (their hash is of a 256-bit token, and the new flow only ever submits a 6-digit code);
that is acceptable (see Rollout).

`notification-service` does **not** map `password_reset_tokens` — no `*RoEntity` change,
`ReadOnlyMappingIT` unaffected.

### D-2 `PasswordResetTokens`

**File:** `backend/.../shared/auth/application/PasswordResetTokens.java`

Add:

```java
/** A 6-digit numeric code, zero-padded, e.g. "418302". Low entropy on purpose — paired with a
 *  30-minute TTL, single use, and a 5-attempt lock (see PasswordResetToken). */
public static String newCode() {
    return String.format("%06d", RANDOM.nextInt(1_000_000));
}
```

Keep `newRawToken()` for now (nothing else uses it after this; delete in the same commit if the
compiler confirms zero references). `hash(String)` unchanged.

### D-3 Domain model `PasswordResetToken`

**File:** `backend/.../shared/auth/domain/model/PasswordResetToken.java`

- Add `private int attemptCount;` with a getter.
- Add `public static final int MAX_ATTEMPTS = 5;`
- `issue(...)` sets `attemptCount = 0`.
- `isUsable(Instant now)` → `usedAt == null && now.isBefore(expiresAt) && attemptCount < MAX_ATTEMPTS`.
- `void recordFailedAttempt()` → `attemptCount++`.
- Reconstruction ctor / factory used by the JPA adapter gains the `attemptCount` parameter.

### D-4 Persistence

**Files:** `PasswordResetTokenEntity.java`, `PasswordResetTokenRepositoryAdapter.java`,
`PasswordResetTokenJpaRepository.java`, `PasswordResetTokenRepository.java` (port).

- Entity: `@Column(name = "attempt_count") private int attemptCount;`
- Port + adapter: add `Optional<PasswordResetToken> findActiveByUserId(UUID userId)` — the newest
  row for the user that is not used and not expired (attempt-count check happens in the use case
  so a locked row still returns and yields the one generic failure). Existing `findByTokenHash`
  stays (used nowhere after this — remove if zero references) ; `save` must persist `attemptCount`
  (it is an update path now, not only insert).
- `PasswordResetTokenRepositoryAdapterIT`: add a case for `findActiveByUserId` + one for the
  attempt-count round trip.

### D-5 `RequestPasswordResetUseCase`

**File:** `backend/.../shared/auth/application/usecases/RequestPasswordResetUseCase.java`

```java
tokenRepository.invalidateUnusedForUser(user.getId());
String code = PasswordResetTokens.newCode();
tokenRepository.save(PasswordResetToken.issue(user.getId(), PasswordResetTokens.hash(code), TOKEN_TTL));
try {
    mailer.sendResetCode(user.getEmail(), user.getFullName(), code);
} catch (RuntimeException _) {
    log.warn("Could not send the password reset email for user {}", user.getId());
}
```

Port `PasswordResetMailer.sendResetLink(email, name, rawToken)` → `sendResetCode(email, name, code)`.

### D-6 `ResetPasswordUseCase`

**File:** `backend/.../shared/auth/application/usecases/ResetPasswordUseCase.java`

Signature `execute(String rawToken, String newPassword)` → `execute(String email, String code,
String newPassword)`.

```java
@Transactional
public void execute(String email, String code, String newPassword) {
    if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
        throw new DomainException("La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
    }
    if (email == null || email.isBlank() || code == null || code.isBlank()) {
        throw new DomainException(INVALID_LINK);
    }
    User user = userRepository.findByEmail(User.normalizeEmail(email)).orElse(null);
    if (user == null) { throw new DomainException(INVALID_LINK); }

    PasswordResetToken token = tokenRepository.findActiveByUserId(user.getId()).orElse(null);
    if (token == null || !token.isUsable(Instant.now())) {
        throw new DomainException(INVALID_LINK);
    }
    if (!MessageDigest.isEqual(
            token.getTokenHash().getBytes(UTF_8),
            PasswordResetTokens.hash(code).getBytes(UTF_8))) {
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

Constant-time compare (`MessageDigest.isEqual`) so a timing side-channel does not leak code
prefixes. `INVALID_LINK` wording kept verbatim.

### D-7 Controller

**Files:** `AuthController.java`, `requests/ResetPasswordRequest.java`

`ResetPasswordRequest(String token, String newPassword)` →

```java
public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos") String code,
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must have at least 8 characters") String newPassword) {}
```

`AuthController.resetPassword` calls `resetPasswordUseCase.execute(req.email(), req.code(),
req.newPassword())`. `/auth/forgot-password` unchanged. Both stay on the auth rate-limit group
in `ApiGatewayRateLimitFilter` (already there).

### D-8 Mailer + `AuthEmailLayout`

**New file:** `backend/.../shared/auth/infrastructure/email/AuthEmailLayout.java`

A ~150-line deliberate copy of the notification-service `EmailLayout` **subset** the reset email
needs: the shell (`build`), `header`, `footer`, `eyebrow`, `paragraph`, `code`, `route`, `note`,
`escape`, and the same colour/font constants + `LOGO_CONTENT_ID` / `LOGO_RESOURCE`
(`email/pilar-estilo-logo.png`, already on the monolith classpath). Class javadoc explains why it
is duplicated: the reset mailer must not depend on `notification-service`.

**File:** `SmtpPasswordResetMailer.java`

- `sendResetLink` → `sendResetCode(String toEmail, String greetingName, String code)`.
- Body: `AuthEmailLayout.titled("Código para cambiar tu contraseña")
    .eyebrow("Seguridad")
    .paragraph("Recibimos una solicitud para cambiar la contraseña de tu cuenta. "
             + "Si fuiste tú, usa este código:")
    .code(code, null)
    .route("Cómo usarlo", "Entra a", "pilarestilo.com",
           "Iniciar sesión › ¿Olvidaste tu contraseña?")
    .paragraph("Escribe tu correo y el código, y elige tu nueva contraseña.")
    .note("Importante", "El código vence en 30 minutos y se usa una sola vez. "
        + "Si no fuiste tú, ignora este correo: tu contraseña actual sigue válida.")
    .build()`
- Plain-text alternative in the same shape, no URL.
- `app.password-reset.link-base-url` config key is now unused — **remove it** from the ctor,
  `application.yml`, `additional-spring-configuration-metadata.json`, and the three `.env` files.
  Add `app.password-reset.code-ttl-minutes` (default 30, replaces the hard-coded `Duration`) and
  `app.password-reset.max-attempts` (default 5) to metadata + `application.yml`.

---

## Part E — Frontend reset form

**File:** `frontend/src/islands/auth/ResetPasswordForm.tsx`

- Stop reading `?token=` from `window.location`. Remove the `linkDead` "no token in URL" branch
  and its `useEffect`.
- The form is now: **email** (`type=email`, `autocomplete=email`), **código** (`inputmode=numeric`,
  `pattern=\d*`, `maxlength=6`, one field — not 6 boxes), **nueva contraseña**, **repite la
  contraseña** (existing show/hide toggle stays).
- `handleSubmit` → `resetPassword(email, code, password)`. `passwordValidationError` unchanged; add
  a `code.length !== 6` check with the message `El código tiene 6 dígitos`.
- `submitErrorOutcome`: a 400 is no longer "the link is dead" — it is `El código no es válido o ya
  expiró. Pídelo de nuevo desde "¿Olvidaste tu contraseña?".` Keep 429 handling. Drop the
  `linkDead` full-screen state; errors render inline like the validation ones. The success screen
  (`OutcomeCard`, `done`) stays.
- `COPY[locale]` table gains `codeLabel`, `codePlaceholder`, and updated `dead*` strings; keep the
  Chilean `tú` register.

**File:** `frontend/src/lib/api.ts`

```ts
export async function resetPassword(email: string, code: string, newPassword: string): Promise<void> {
  return apiFetch<void>('/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify({ email, code, newPassword }),
  });
}
```

`requestPasswordReset(email)` unchanged. `ForgotPasswordForm.tsx` unchanged (still just asks for
the email; its success copy changes from "te enviamos un enlace" to "te enviamos un código").

**File:** `frontend/src/islands/auth/ForgotPasswordForm.tsx` — success-screen copy only:
`Si el correo pertenece a una cuenta, te enviamos un código para cambiar tu contraseña. El código
expira en 30 minutos.`

---

## Part F — Preview harness + tests

### Preview harness

**New file:** `services/notification-service/src/test/java/com/pilarestilo/notificationservice/application/EmailPreviewTest.java`

A `@Test` (not `@Disabled`) that builds one representative `NotificationMessage` for every HTML
templateKey with fixture data and writes `bodyHtml` to
`target/email-preview/{templateKey}.html`. Assertions per file:

- does **not** contain `<a ` or `href=` or `<button`
- contains the expected `eyebrow` text and the `<h1>` title
- for order emails: contains `order.publicReference()`
- well-formed enough: a tag-balance check (jsoup is not on the classpath — don't add it)

Same harness idea in the monolith:
**New file:** `backend/.../shared/auth/infrastructure/email/AuthEmailPreviewTest.java` — writes
`target/email-preview/PASSWORD_RESET.html`, asserts no link, contains the code placeholder and
"Seguridad".

### Existing tests to update

| File | Change |
|---|---|
| `notification-service .../application/NotificationComposerTest.java` | `paymentReceived` now takes `(OrderView, PaymentView)`; assert subject has the reference not a UUID, body has no link. Remove `orderPreparing` HTML assertions. `discountCodeAssigned` / `welcome` now have HTML — assert the `code` block. |
| `notification-service .../application/DispatchersTest.java` | `PaymentNotificationDispatcher.onPaymentConfirmed` path loads order + payment; `OrderNotificationDispatcher` sends **no email** for `PREPARING_ORDER` but still calls `notifyOrderPreparing`. |
| `backend .../email/SmtpPasswordResetMailerTest.java` | `sendResetLink` → `sendResetCode`; body contains the 6-digit code, no `http`, no `<a`. `RecordingMailer` seam updated. |
| `backend .../application/usecases/RequestPasswordResetUseCaseTest.java` (exists) | mailer stub verifies `sendResetCode` with a `\d{6}` arg. |
| `backend .../application/usecases/ResetPasswordUseCaseTest.java` (exists) | new signature; add: wrong code increments `attemptCount`; 5th wrong code locks; correct code after 4 wrong still works; unknown email → generic message. |
| `backend .../web/PasswordResetControllerIT.java` | `POST /auth/reset-password` body is `{email, code, newPassword}`; happy path; wrong code 400 generic; lockout after 5. |
| `backend .../persistence/.../PasswordResetTokenRepositoryAdapterIT.java` | `findActiveByUserId`; `attemptCount` round-trip. |
| `frontend .../auth/__tests__/ResetPasswordForm.test.tsx` | rewrite: no `?token`; fill email + code + passwords; assert `resetPassword('a@b.cl','418302','BrandNew123')`; wrong-code 400 shows the inline error; `code.length !== 6` blocks submit. |
| `frontend .../auth/__tests__/ForgotPasswordForm.test.tsx` | success copy assertion `/código/i` instead of `/enlace/i`. |

---

## Build order

1. **`EmailLayout` v2 primitives** (Part A) + `EmailPreviewTest` scaffold that renders the current
   emails through the new builder. Green when every existing HTML email still renders and the
   no-link assertion passes.
2. **`NotificationComposer`** — `orderConfirmationHtml` to `orderSummary`, `note(label,text)`
   sweep, Chilean audit, `discountCodeAssigned` / `welcome` HTML (Part B minus paymentReceived).
   `NotificationComposerTest` green.
3. **`paymentReceived` + dispatcher rewiring** (Part B `paymentReceived` + Part C). `DispatchersTest`
   green.
4. **`orderPreparing` email removal** (Part B). `DispatchersTest` + `OrderNotificationDispatcher`
   green; in-app path untouched.
5. **V105 migration + domain + persistence** (Part D-1..D-4). `PasswordResetTokenRepositoryAdapterIT`
   green.
6. **`RequestPasswordResetUseCase` + `ResetPasswordUseCase` + `PasswordResetTokens.newCode`**
   (D-2, D-5, D-6). Use-case tests green.
7. **`AuthEmailLayout` + `SmtpPasswordResetMailer` + config cleanup** (D-8) + `AuthEmailPreviewTest`.
   Mailer test green.
8. **Controller + request record** (D-7). `PasswordResetControllerIT` green.
9. **Frontend reset form + api.ts** (Part E). `ResetPasswordForm.test.tsx` +
   `ForgotPasswordForm.test.tsx` green, `tsc` clean, `npm run build` green.
10. **Full `mvn verify` (backend) + notification-service `mvn test` + frontend `vitest run`**,
    eyeball every file under `target/email-preview/`.

## Risks & rollout

- **In-flight reset links break on deploy.** A customer who requested a reset in the 30 minutes
  before deploy gets a link that no longer works; the reset page no longer reads `?token`. Volume
  is near zero (self-service, rare) and the fix is "request again". No migration of existing rows.
- **6-digit brute force.** 10^6 space, 30-min TTL, single active row per user, `MAX_ATTEMPTS = 5`
  lock, plus the existing per-IP auth rate limit. Expected value of a blind guess in the window is
  well under 1. Acceptable for a password *reset* (not a login).
- **`orderPreparing` email removal** is invisible to the bell and to WhatsApp — only the redundant
  email stops. If the shop later wants a distinct "packed, about to ship" email, it is a new
  templateKey, not a revert.
- **`AuthEmailLayout` drift.** Two layout classes can diverge. Mitigation: both are exercised by a
  preview test, and the monolith copy is intentionally the smaller subset — a visual tweak to the
  shared look is a two-file change, called out in the class javadoc.
- **No deploy-time env changes required.** `app.password-reset.link-base-url` removal is
  backward-compatible (an unknown key in `.env` is ignored); the new keys have defaults.
