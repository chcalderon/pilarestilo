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
| `product` | Product CRUD, catalog filters, search, rating summary fields, sizeStocks projection, and variant combinations (`color + size + stock`) |
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
| `systemsettings` | Admin-managed storefront/system configuration (channels + notifications + checkout payment-method toggles/providers) |
| `shared/kafka` | Optional Kafka domain-event transport (`KafkaDomainEventPublisher`, listener retry/DLQ config) |

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
- `GET /api/system-settings/public` (public storefront channels + checkout payment methods + transfer details)

### Catalog

- `GET /api/products`
- `GET /api/products/{id}`
- `GET /api/products/search?q=...`
- `GET /api/inventory/products`
- `GET /api/inventory/products/{id}`
- `GET /api/categories`
- `GET /api/categories/tree`
- `GET /api/media/**` (static media served from backend storage path)

Inventory command endpoints (extracted service, backend-to-backend):
- `POST /api/inventory/commands/reserve`
- `POST /api/inventory/commands/release`
- `POST /api/inventory/commands/confirm`

Order query endpoints (extracted service, backend-to-backend):
- `GET /api/orders`
- `GET /api/orders/{id}`
- `GET /api/orders/_health`

### Wishlist

- `GET /api/wishlist`
- `POST /api/wishlist/items/{productId}`
- `DELETE /api/wishlist/items/{productId}`
- `GET /api/wishlist/share-link` (authenticated owner)
- `POST /api/wishlist/share-link` (authenticated owner, enable/generate token)
- `DELETE /api/wishlist/share-link` (authenticated owner, disable sharing)
- `GET /api/wishlist/shared/{token}` (public read-only shared wishlist)

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

### Actuator (ops)

- `GET /api/actuator/health`
- `GET /api/actuator/metrics`
- `GET /api/actuator/prometheus`

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

Optional profiles from repo root:

```bash
# Kafka broker for domain-events mode
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile kafka up -d

# Extracted read microservices (P6)
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile microservices up -d --build product-service inventory-service order-service payment-service

# Tracing stack (P7)
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile tracing up -d
```

When that profile is running, Caddy routes:
- `GET/HEAD /api/products*` to `product-service`.
- `GET/HEAD /api/inventory*` to `inventory-service`.
- `GET/HEAD /api/payments*` to `payment-service`.
- `/api/orders*` to `order-service`.

Optional inventory write delegation from backend to `inventory-service`:
- Set `APP_INVENTORY_REMOTE_ENABLED=true`.
- Backend will forward `reserve/release/confirm` stock commands to `APP_INVENTORY_REMOTE_BASE_URL`.

Optional order read delegation from backend to `order-service`:
- Set `APP_ORDER_REMOTE_ENABLED=true`.
- Backend will resolve order queries (`/api/orders*`) through `APP_ORDER_REMOTE_BASE_URL`.

Optional order write delegation from backend to `order-service`:
- Set `APP_ORDER_REMOTE_WRITE_ENABLED=true`.
- Backend will resolve order create/status commands through `APP_ORDER_REMOTE_BASE_URL`.

Optional payment read delegation from backend to `payment-service`:
- Set `APP_PAYMENT_REMOTE_ENABLED=true`.
- Backend will resolve payment queries (`/api/payments*`) through `APP_PAYMENT_REMOTE_BASE_URL`.
- If `payment-service` internal auth is enabled, set `APP_PAYMENT_REMOTE_SERVICE_TOKEN` so backend includes `X-Service-Token`.

Gateway-facing rate-limit filter (backend side):
- Protects sensitive public POST endpoints (`/api/auth/login`, `/api/auth/register`, payment webhooks).
- Per-IP window and thresholds are tunable through `APP_GATEWAY_RATE_LIMIT_*` variables.

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
| `KAFKA_BOOTSTRAP_SERVERS` | No | Kafka brokers for domain-event mode (default `localhost:9092`) |
| `APP_DOMAIN_EVENTS_KAFKA_ENABLED` | No | Enables Kafka as primary `DomainEventPublisher` (`true/false`, default `false`) |
| `APP_DOMAIN_EVENTS_KAFKA_TOPIC_PREFIX` | No | Topic prefix for domain events (default `pe.domain`) |
| `APP_DOMAIN_EVENTS_KAFKA_CONSUMER_GROUP_ID` | No | Kafka group id used by backend domain-event consumers |
| `APP_DOMAIN_EVENTS_KAFKA_RETRY_BACKOFF_MS` | No | Backoff in ms for Kafka listener retries |
| `APP_DOMAIN_EVENTS_KAFKA_RETRY_MAX_ATTEMPTS` | No | Max delivery attempts before dead-letter routing |
| `APP_DOMAIN_EVENTS_KAFKA_DLT_SUFFIX` | No | Dead-letter topic suffix (default `.dlt`) |
| `APP_INVENTORY_REMOTE_ENABLED` | No | Enables remote inventory command delegation from backend (`true/false`, default `false`) |
| `APP_INVENTORY_REMOTE_BASE_URL` | No | Base URL for extracted inventory-service commands (default `http://inventory-service:8082`) |
| `APP_ORDER_REMOTE_ENABLED` | No | Enables remote order query delegation from backend (`true/false`, default `false`) |
| `APP_ORDER_REMOTE_WRITE_ENABLED` | No | Enables remote order command delegation from backend (`true/false`, default `false`) |
| `APP_ORDER_REMOTE_BASE_URL` | No | Base URL for extracted order-service queries (default `http://order-service:8083`) |
| `APP_ORDER_REMOTE_SERVICE_TOKEN` | No | Internal token sent by backend when calling order-service (`X-Service-Token`) |
| `APP_PAYMENT_REMOTE_ENABLED` | No | Enables remote payment query delegation from backend (`true/false`, default `false`) |
| `APP_PAYMENT_REMOTE_BASE_URL` | No | Base URL for extracted payment-service queries (default `http://payment-service:8084`) |
| `APP_PAYMENT_REMOTE_SERVICE_TOKEN` | No | Internal token sent by backend when calling payment-service (`X-Service-Token`) |
| `APP_GATEWAY_RATE_LIMIT_ENABLED` | No | Enables backend gateway-facing rate-limit filter (`true/false`, default `true`) |
| `APP_GATEWAY_RATE_LIMIT_WINDOW_SECONDS` | No | Rate-limit time window in seconds (default `60`) |
| `APP_GATEWAY_RATE_LIMIT_LOGIN_MAX_REQUESTS` | No | Max requests per IP/window for `POST /api/auth/login` (default `12`) |
| `APP_GATEWAY_RATE_LIMIT_REGISTER_MAX_REQUESTS` | No | Max requests per IP/window for `POST /api/auth/register` (default `6`) |
| `APP_GATEWAY_RATE_LIMIT_WEBHOOK_MAX_REQUESTS` | No | Max requests per IP/window for payment webhooks (default `180`) |
| `APP_TRACING_ENABLED` | No | Enables OTLP tracing export (`true/false`, default `false`) |
| `APP_TRACING_OTLP_ENDPOINT` | No | OTLP HTTP traces endpoint (default `http://otel-collector:4318/v1/traces`) |
| `APP_TRACING_SAMPLING_PROBABILITY` | No | Trace sampling ratio between `0.0` and `1.0` (default `1.0`) |
| `SPRING_PROFILES_ACTIVE` | No | `local` for dev profile |
| `SERVER_PORT` | No | API port (default 8080) |

Current note:
- Notification listeners now resolve a structured recipient (`phone` + `email`) so providers can choose the right channel safely.
- Active notification provider is now selected from `/admin/settings` (`notificationProvider` in `system_settings`) and can be changed at runtime.
- `WHATSAPP_TWILIO` prioritizes user phone and falls back to `whatsappTwilioToFallback` (admin) or `WHATSAPP_TWILIO_TO_FALLBACK` (env).
- `EMAIL_SENDGRID` uses its own admin-managed credentials (`sendgridApiKey`, `sendgridFromEmail`, etc.) with env fallback.
- `EMAIL_SMTP` sends directly through your SMTP server, prioritizes user email, and supports admin-managed values with env fallback.
- Sensitive values are encrypted at rest in `system_settings` (`smtpPassword`, Twilio auth token, SendGrid API key).
- Checkout payment methods are runtime-configurable from admin settings: `BANK_TRANSFER` and `PAYMENT_GATEWAY` can be toggled, and gateway mode requires at least one enabled provider (`MERCADO_PAGO` for now).
- Bank-transfer account details are admin-managed in `system_settings` and are snapshotted into each `payments` record created with `BANK_TRANSFER`.
- Mercado Pago connection settings can be managed from admin system settings, with encrypted-at-rest storage for `access token` and optional `webhook token`; env values remain fallback.
- Domain events can run in-process (default) or over Kafka via runtime toggle. Kafka mode includes retry + DLT and `OrderInventorySaga` for payment/inventory consistency.
- Distributed tracing is optional and emits OpenTelemetry spans to OTLP when enabled.

---

## Tests

```bash
mvn test      # unit tests
mvn verify    # includes integration tests (Testcontainers)
```

---

## Database migrations

Flyway scripts in `src/main/resources/db/migration` currently run from `V1` to `V24`, including:

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
- extended catalog seed with additional products (`V18`)
- wishlist share-link token and enablement fields (`V19`)
- product variants table + backfill from size stocks (`V20`)
- media storage provider configuration (`V21`)
- checkout payment method + gateway provider settings (`V22`)
- bank-transfer configuration fields and per-payment transfer snapshot persistence (`V23`)
- Mercado Pago settings fields in system settings (`V24`)

---

## Seed admin (dev)

| Email | Password |
|---|---|
| `admin@pilarestilo.com` | `admin2026` |
