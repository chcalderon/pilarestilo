# Pilar Estilo - Backend

Spring Boot 3.3 API for Pilar Estilo.

## Stack

- Java 17
- Spring Boot 3.3
- Spring Security + JWT
- Spring Data JPA + Hibernate
- Flyway
- PostgreSQL 16

---

## Architecture

Hexagonal (Ports and Adapters), module-oriented.

```
<module>/
  domain/
  application/
  infrastructure/
```

Rule: `domain/` remains framework-agnostic (no Spring/JPA annotations).

---

## Modules

| Module | Responsibility |
|---|---|
| `shared/auth` | JWT issuance/validation, auth filter, login/register/refresh/me + self profile/password |
| `product` | Product CRUD, catalog filters, search, rating summary fields, sizeStocks projection |
| `category` | Category tree and taxonomy |
| `review` | Product reviews + moderation workflow |
| `order` | Order aggregate and status transitions |
| `payment` | Payment registration, proof submission, review (approve/reject) |
| `discount` | Promo code creation/validation/application |
| `inventory` | Stock reservation/release and stock events |
| `wishlist` | Customer favorites (`/api/wishlist`) |
| `customercredit` | Credit balance and movement history |
| `notification` | Notification port + provider-based adapters (`LOG`, `WHATSAPP_SIMULATED`, `WHATSAPP_TWILIO`, `EMAIL_SENDGRID`, `EMAIL_SMTP`) + domain listeners |
| `user` | User repository and user-facing data |
| `systemsettings` | Admin-managed storefront/system configuration (channels + notification providers) |

---

## Key API Surface

### Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`
- `GET /api/auth/me/profile`
- `PATCH /api/auth/me/profile`
- `PATCH /api/auth/me/password`

### Users (admin)

- `GET /api/users`
- `GET /api/users/{id}`
- `PATCH /api/users/{id}`
- `PATCH /api/users/{id}/password`
- `DELETE /api/users/{id}`

### System settings

- `GET /api/system-settings` (ADMIN)
- `PATCH /api/system-settings` (ADMIN)
- `GET /api/system-settings/public` (public storefront channels)

### Catalog

- `GET /api/products`
- `GET /api/products/{id}`
- `GET /api/products/search?q=...`
- `GET /api/categories`
- `GET /api/categories/tree`
- `GET /api/media/**` (static media served from backend storage path)

### Wishlist

- `GET /api/wishlist`
- `POST /api/wishlist/items/{productId}`
- `DELETE /api/wishlist/items/{productId}`

### Payments

- `POST /api/payments`
- `GET /api/payments/order/{orderId}` (authenticated; customer ownership enforced)
- `PATCH /api/payments/{id}/proof`
- `PATCH /api/payments/{id}/review` (actions: `APPROVE` or `REJECT`)
- `POST /api/payments/{id}/gateway/checkout` (authenticated; owner/admin/seller)
- `POST /api/payments/webhooks/gateway` (public; optional `X-Gateway-Signature` validation)
- `POST /api/payments/webhooks/gateway/mercadopago` (public; optional `token` query validation)

### Media upload

- `POST /api/media/upload` (ADMIN/SELLER)
- `POST /api/media/upload-proof` (authenticated users; used by customer proof flow)

---

## Running locally

### With Docker Compose (recommended)

```bash
cd infra
cp .env.example .env
docker compose up -d postgres
cd ../backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Full local stack

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up --build
```

---

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | DB username |
| `SPRING_DATASOURCE_PASSWORD` | Yes | DB password |
| `JWT_SECRET` | Yes | HS256 secret (min 32 bytes recommended) |
| `SYSTEM_SETTINGS_CRYPTO_SECRET` | No | Secret used to encrypt/decrypt SMTP password in `system_settings` table (defaults to `JWT_SECRET` if missing) |
| `MEDIA_STORAGE_PATH` | No | Filesystem directory used by `/api/media/**` (default `./media`) |
| `NOTIFICATION_PROVIDER` | No | Default/fallback notification provider before admin overrides (`LOG`, `WHATSAPP_SIMULATED`, `WHATSAPP_TWILIO`, `EMAIL_SENDGRID`, `EMAIL_SMTP`) |
| `WHATSAPP_SIMULATED_TO` | No | Destination phone used by simulated WhatsApp logs (default `+56900000000`) |
| `WHATSAPP_SIMULATED_SENDER` | No | Sender alias for simulated WhatsApp logs |
| `WHATSAPP_TWILIO_API_BASE_URL` | No | Twilio API base URL (default `https://api.twilio.com`) |
| `WHATSAPP_TWILIO_ACCOUNT_SID` | Optional | Twilio Account SID fallback when admin setting is empty |
| `WHATSAPP_TWILIO_AUTH_TOKEN` | Optional | Twilio Auth Token fallback when admin setting is empty |
| `WHATSAPP_TWILIO_FROM` | Optional | WhatsApp sender fallback in Twilio format (e.g. `whatsapp:+14155238886`) |
| `WHATSAPP_TWILIO_TO_FALLBACK` | No | Fallback destination used when recipient contact is not a phone number |
| `WHATSAPP_TWILIO_SENDER_ALIAS` | No | Sender alias used in Twilio message text |
| `SENDGRID_API_BASE_URL` | No | SendGrid API base URL (default `https://api.sendgrid.com`) |
| `SENDGRID_API_KEY` | Optional | SendGrid API key fallback when admin setting is empty |
| `SENDGRID_FROM_EMAIL` | Optional | Sender email fallback when admin setting is empty |
| `SENDGRID_SENDER_NAME` | No | Sender display name (default `Pilar Estilo`) |
| `SENDGRID_TO_FALLBACK` | No | Fallback destination when recipient does not have a valid email |
| `EMAIL_SMTP_HOST` | Optional | SMTP host fallback when admin setting is empty |
| `EMAIL_SMTP_PORT` | Optional | SMTP port fallback when admin setting is empty |
| `EMAIL_SMTP_USERNAME` | No | SMTP username (required only if auth is enabled) |
| `EMAIL_SMTP_PASSWORD` | No | SMTP password (required only if auth is enabled) |
| `EMAIL_SMTP_FROM_EMAIL` | Optional | Sender email fallback when admin setting is empty |
| `EMAIL_SMTP_SENDER_NAME` | No | Sender display name (default `Pilar Estilo`) |
| `EMAIL_SMTP_AUTH_ENABLED` | No | SMTP auth toggle (`true/false`), defaults to admin setting |
| `EMAIL_SMTP_STARTTLS_ENABLED` | No | STARTTLS toggle (`true/false`), defaults to admin setting |
| `EMAIL_SMTP_SSL_ENABLED` | No | SMTP SSL toggle (`true/false`); defaults to `true` when port is `465` |
| `EMAIL_SMTP_TO_FALLBACK` | No | Fallback destination when recipient does not have a valid email |
| `PAYMENT_GATEWAY_PROVIDER` | No | `STUB` (default) or `MERCADO_PAGO` |
| `PAYMENT_GATEWAY_WEBHOOK_SECRET` | No | If set, must match `X-Gateway-Signature` on `/api/payments/webhooks/gateway` |
| `PAYMENT_GATEWAY_STUB_CHECKOUT_BASE_URL` | No | Base URL used by stub gateway checkout sessions (default `/es/account?tab=orders`) |
| `PAYMENT_GATEWAY_MP_API_BASE_URL` | No | Mercado Pago API base URL (default `https://api.mercadopago.com`) |
| `PAYMENT_GATEWAY_MP_ACCESS_TOKEN` | Yes if provider `MERCADO_PAGO` | Mercado Pago access token |
| `PAYMENT_GATEWAY_MP_SUCCESS_URL` | No | Back URL for successful checkout |
| `PAYMENT_GATEWAY_MP_PENDING_URL` | No | Back URL for pending checkout |
| `PAYMENT_GATEWAY_MP_FAILURE_URL` | No | Back URL for failed checkout |
| `PAYMENT_GATEWAY_MP_NOTIFICATION_URL` | No | Webhook callback URL used in preference creation |
| `PAYMENT_GATEWAY_MP_WEBHOOK_TOKEN` | No | If set, required as `token` query param on Mercado Pago webhook endpoint |
| `SPRING_PROFILES_ACTIVE` | No | `local` for dev profile |
| `SERVER_PORT` | No | API port (default 8080) |

Current note:
- Notification listeners now resolve a structured recipient (`phone` + `email`) so providers can choose the right channel safely.
- Active notification provider is now selected from `/admin/settings` (`notificationProvider` in `system_settings`) and can be changed at runtime.
- `WHATSAPP_TWILIO` prioritizes user phone and falls back to `whatsappTwilioToFallback` (admin) or `WHATSAPP_TWILIO_TO_FALLBACK` (env).
- `EMAIL_SENDGRID` uses its own admin-managed credentials (`sendgridApiKey`, `sendgridFromEmail`, etc.) with env fallback.
- `EMAIL_SMTP` sends directly through your SMTP server, prioritizes user email, and supports admin-managed values with env fallback.
- Sensitive values are encrypted at rest in `system_settings` (`smtpPassword`, Twilio auth token, SendGrid API key).

---

## Tests

```bash
mvn test      # unit tests
mvn verify    # includes integration tests (Testcontainers)
```

---

## Database migrations

Flyway scripts in `src/main/resources/db/migration` currently run from `V1` to `V17`, including:

- search indexes (`V7`)
- per-size stock schema (`V8`)
- wishlist schema (`V9`)
- Chile currency/default normalization (`V10`)
- product image path migration to backend media routes (`V11`)
- active-flag support for users (`V12`)
- singleton system settings + SMTP credential storage (`V13`)
- user phone capture (`V14`)
- product list-price schema + constraints (`V15`)
- default list-price backfill for existing catalog rows (`V16`)
- notification provider admin configuration fields + encrypted Twilio/SendGrid secrets (`V17`)

---

## Seed admin (dev)

| Email | Password |
|---|---|
| `admin@pilarestilo.com` | `admin2026` |
