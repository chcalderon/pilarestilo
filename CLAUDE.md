# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Backend (Java 25 + Spring Boot 4.0.7)

```bash
# Run all tests (Testcontainers spins up real Postgres)
cd backend && mvn test

# Run a single test class or method
cd backend && mvn test -Dtest=ProductControllerTest
cd backend && mvn test -Dtest=ProductControllerTest#shouldCreateProduct

# Integration tests (includes Testcontainers lifecycle)
cd backend && mvn verify

# Build JAR (skip tests)
cd backend && mvn clean package -DskipTests

# Run locally against Dockerized Postgres
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Frontend (Astro 4 + React)

```bash
cd frontend && npm run dev        # dev server → http://localhost:4321
cd frontend && npm run build
cd frontend && npm run preview
cd frontend && npm run test:e2e   # Playwright
```

### Docker (primary local workflow)

`.env.example` ships with the optional subsystems switched **on**
(`APP_DOMAIN_EVENTS_KAFKA_ENABLED`, `APP_CACHE_REDIS_ENABLED`, the three
`APP_*_REMOTE_ENABLED` flags, `APP_TRACING_ENABLED`). Those flags register Kafka listeners,
a Redis connection factory and remote clients at startup, so the containers behind them have
to exist: without the matching profiles the backend cannot resolve `kafka` or `redis` and dies
in a restart loop (`No resolvable bootstrap urls given in bootstrap.servers`).

```bash
# Full stack matching the shipped .env — runs at http://localhost
cd infra && docker compose --env-file .env \
  --profile kafka --profile cache --profile microservices \
  --profile observability --profile tracing up -d --build
```

To run the minimal stack (postgres + backend + frontend + caddy) instead, first set
`APP_DOMAIN_EVENTS_KAFKA_ENABLED=false`, `APP_CACHE_REDIS_ENABLED=false`, `APP_TRACING_ENABLED=false`
and the three `APP_*_REMOTE_ENABLED=false` in `.env`:

```bash
cd infra && docker compose --env-file .env up -d --build
```

Seed admin credentials: `admin@pilarestilo.com` / `admin2026`

### SonarQube, local only

```bash
cd infra && docker compose --env-file .env --profile quality up -d sonarqube   # http://localhost:9000
cd backend && mvn -DskipTests sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token>   -Dsonar.projectKey=pilarestilo-backend
```

Never deployed: the `quality` profile is off by default **and** `scripts/deploy/vps_deploy.sh`
strips it from `DEPLOY_PROFILES`, so a profile list copied from a local `.env` cannot start a 2 GB
Java process on the VPS.

---

## Architecture

### Monorepo layout

```
backend/          Spring Boot monolith (hexagonal, 21 modules)
services/         Extracted microservices (P6 — optional profile)
  product-service, inventory-service, order-service, payment-service
frontend/         Astro 4 SSR + React islands
infra/            Docker Compose, Caddy, Flyway, env
docs/             Architecture, roadmap, API contracts
```

### Backend — hexagonal modules

Every domain module follows the same three-layer pattern:

```
{module}/
  domain/
    model/          Pure Java entities (no JPA, no Spring)
    enums/
    events/         Domain event records
    ports/          Repository and service interfaces (e.g. ProductRepository)
  application/
    usecases/       One class per use case: GetProductUseCase, CreateProductUseCase
    dto/            Input/output transfer objects
    mappers/        Domain ↔ DTO conversion
  infrastructure/
    persistence/
      entities/     JPA @Entity classes
      repositories/ Port implementations: ProductRepositoryJpaAdapter
    web/
      controllers/  @RestController, mapped to /api/{resource}
      requests/     Request body POJOs
```

Naming conventions: `{Action}UseCase`, `{Entity}RepositoryJpaAdapter`, `{Entity}Controller`. Domain objects carry no framework annotations — JPA entities are separate from domain models. Use cases take ports (interfaces) as constructor args; Spring wires them.

Modules: `product`, `category`, `inventory`, `order`, `payment`, `discount`, `review`, `wishlist`, `customercredit`, `notification`, `systemsettings`, `productai`, `cashregister`, `dispatch`, `dashboard`, `user`, `navigation`, `location`, `publication`, `customeraddress`, `billing` + `shared` (auth, rbac, kafka, common domain).

`cashregister` also exposes `POST /api/pos/sales` (stub, returns 501) — planned Windows POS integration; see `docs/pos-channel.md`.

### Frontend — Astro islands

Routes split by concern:
- **Storefront**: `/es/*` and `/en/*` — localized, SSR Astro pages
- **Admin**: `/admin/*` — guarded by `src/middleware.ts` (checks JWT `role === ADMIN`)
- Root `/` redirects to `/es/`
- **Checkout**: `/{locale}/checkout` — three steps (`?paso=envio|pago|resumen`), identical on
  mobile and desktop. `src/middleware.ts` also gates this route, locally (no backend call), so an
  unauthenticated customer logs in *before* step one instead of at the pay button. Answers persist
  in the `pe-checkout` store so the login redirect does not wipe them. The cart page holds only
  items, a subtotal and the CTA.

Static markup in `src/components/*.astro`; interactive pieces in `src/islands/` as React components hydrated client-side. Admin islands live in `src/islands/admin/`. Auth state (JWT), cart, and wishlist use Zustand stores. Token stored in `pe_token` cookie.

API calls use `PUBLIC_API_BASE_URL` (browser) and `INTERNAL_API_BASE_URL` (SSR, points to `http://backend:8080/api` in Docker).

### Caddy routing

All traffic enters through Caddy. In the `microservices` profile, read paths are routed to extracted services:

| Pattern | Upstream |
|---|---|
| `GET/HEAD /api/products*` | product-service:8081 |
| `GET/HEAD /api/inventory*` | inventory-service:8082 |
| `GET/HEAD /api/payments*` | payment-service:8084 |
| `GET/HEAD /api/orders*` | order-service:8083 |
| `POST/PATCH/DELETE /api/*` | backend:8080 |
| everything else | frontend:4321 |

Guardrails: 12 MB body cap, per-IP rate limits on auth and webhook endpoints.

### The boleta gate

A paid order cannot be claimed for dispatch, or dispatched, without a live row in
`sales_documents` — checked at **both** steps, because a dispatch claimed while its boleta was live
can still be in progress when that boleta is voided. The rule is `SalesDocumentGate` (port in
`dispatch/domain/ports/`, adapter in `billing/`), and the shop can switch it off with
`system_settings.tax_document_required_before_dispatch`.

A credit note is a **document type, not a status**. Voiding is honest only while the boleta has not
reached the SII; once the sale is declared, undoing it takes a nota de crédito (DTE 61) with its own
folio that references the boleta, and both stay valid. So a credit note is the one document allowed
to live beside another, and every "the live document for this order" read must exclude it — the
partial index, `findLiveByOrderId` and the two drawers all do. It is registered from the return, in
`/admin/devoluciones`, and the SII reference code is derived (whole total → 1 annuls, less → 3
corrects the amount) rather than asked for.

Approving the payment is deliberately **not** gated: the folio comes from the SII's eBoleta app by
hand, and making the money wait on it invites a made-up folio. Boleta files live under
`app.documents.storage-path`, never under `app.media.storage-path` — that whole tree is `permitAll`
on `/api/media/**`, and a boleta carries a RUT, a buyer name and amounts.

### Two codebases write the `orders` table

With `APP_ORDER_REMOTE_WRITE_ENABLED=true` — what production runs — `order-service` performs the
INSERT, not the monolith. They share a database and no compiler, no schema check and no test
crosses between them, so **any change to `orders` / `order_items`, their DTOs, or the events
around them must be applied in `services/order-service/` in the same commit, and both must ship in
the same deploy.** Five bugs have come from forgetting this; green suites on both sides say
nothing about it. Verify by creating a real order against the full compose stack.

Discount codes are the exception that proves the rule: `order-service` owns no redemption ledger,
so the monolith evaluates the code, claims the slot, calls the service with only the resulting
amount, then binds the ledger row to the returned order id.

### Every in-process `@EventListener` is dead when Kafka is on

`KafkaDomainEventPublisher` is `@Primary`, so with `APP_DOMAIN_EVENTS_KAFKA_ENABLED=true` only the
Kafka listeners run. Four separate defects came from a twin drifting from its in-process original,
or never existing. Behaviour now lives in a `*NotificationDispatcher` in `notification/application/`
and the listeners are transport adapters with none of their own. **Add behaviour to the dispatcher,
never to a listener.**

### Domain events

`DomainEventPublisher` port has two adapters selected by env:
- **Default**: in-process Spring `ApplicationEventPublisher`
- **Kafka** (`APP_DOMAIN_EVENTS_KAFKA_ENABLED=true`): `KafkaDomainEventPublisher`

Key flows: `OrderCreated` → payment registration; `PaymentConfirmed` → order paid; `PaymentRejected` → order cancelled + inventory compensation (`OrderInventorySaga`).

### Database migrations

Flyway manages all schema changes. Migrations live in `backend/src/main/resources/db/migration/`. Current highest: **V81**. Never edit an already-applied migration — always add a new `V{n+1}__description.sql`.

Recent migrations (V54–V66):
- V54: `products.version` + `cash_registers.version` (optimistic locking `@Version`)
- V55: `order_items.variant_color` + `variant_size` (nullable, no backfill)
- V56: `product_variants.stock` → `stock_on_hand` + `stock_reserved` (reserved stock model)
- V57: `inventory_movements` audit table
- V58: `categories.menu_visible` + `category_type` + `hero_image_url`
- V59: `navigation_sections` table (root_category_id FK, layout, column_count, banner fields, sort_order)
- V60: seed `navigation_sections` from root categories (idempotent `ON CONFLICT DO NOTHING`)
- V60_1: seed `aros` subcategory under accesorios
- V60_2: prereq repair for retail runtime alignment
- V61: retail runtime alignment — updates category_type/hero_image_url, seeds product variants, sets navigation layout
- V62: modern RBAC permissions table + role_permission_grants
- V63: seed default permission catalog
- V64: seed default role–permission grants (ADMIN full, SELLER subset)
- V65: social commerce foundation — `publications` table for multi-platform content
- V66: repair `shipping_origin_zone` alias `NATIONAL` → `NACIONAL`
- V67: discount redemptions become reservable (`status` + `order_id` on `discount_code_usages`, partial unique index on `status <> 'RELEASED'`) + `orders.public_reference` + `orders.discount_id`/`discount_code` provenance
- V68: `orders.public_reference` SET NOT NULL (contract half of V67's expand)
- V75: `sales_documents` — the boleta/factura backing a sale. Append-only: a correction voids and
  reissues, chained by `replaces_document_id`. Partial unique index keeps one live document per
  order; `CHECK (net + tax = total)` pins the arithmetic
- V76: `orders.net_amount` / `tax_amount` / `tax_rate` (expand, nullable — contract still pending)
- V77: store tax identity in `system_settings` (RUT, razón social, giro, Acteco, dirección, comuna,
  ciudad, `tax_vat_rate`, `tax_document_required_before_dispatch`, `tax_document_provider`)
- V78: `billing` permissions — `documents.read` / `documents.issue` / `documents.void`, granted to
  ADMIN and ADMINISTRACION; plus `orders.read` for ADMINISTRACION and SUPERVISOR
- V79: `orders.net_amount` / `tax_amount` / `tax_rate` SET NOT NULL + `chk_orders_tax_reconciles`
  (contract half of V76)
- V80: `return_requests` (retracto and devolución, two independent tracks: money and garment) +
  `NOTA_CREDITO` as a document type, `sales_documents.reference_code`, and the live-document index
  narrowed to `uq_sales_documents_live_sale_per_order` so a boleta and its credit note coexist
- V81: `returns.read` / `returns.manage` / `returns.refund` permissions

### Notifications go out on every enabled channel

`system_settings.notification_providers` is a **set**, not a value (V82). `SystemSettingsNotificationSender`
fans a message out to all of them, each inside its own try, so a dead SMTP host cannot take WhatsApp
down with it; if none accepts, that is logged as an error rather than passing for silence. Before
V82 the column held one provider and the sender switched on it, so turning on WhatsApp silently
stopped every email — including the written confirmation the Ley 21.398 requires.

The panel enforces one rule the domain repeats: the last active channel cannot be turned off.
`NOTIFICATION_PROVIDER` remains as the fallback for a row nobody has saved yet.

### Spring Boot 4 modular auto-configuration

Boot 4 split `spring-boot-autoconfigure` into per-technology modules. Putting a library on the
classpath no longer enables its auto-configuration — the matching `spring-boot-*` module must be
declared too, or the feature silently does nothing. The backend already declares:

| Module | Without it |
|---|---|
| `spring-boot-flyway` | migrations never run → `ddl-auto: validate` fails on missing tables |
| `spring-boot-restclient` | no `RestClient.Builder` bean → remote clients fail to wire |
| `spring-boot-kafka` | `spring-kafka` alone no longer brings `KafkaProperties` |
| `spring-boot-security-test` (test) | `@AutoConfigureMockMvc` skips `springSecurity()`, so `@WithMockUser` stops authenticating and state-changing requests answer 403 |
| `spring-boot-starter-webmvc-test` (test) | MockMvc / `@WebMvcTest` slice unavailable |
| `spring-boot-micrometer-tracing-opentelemetry` | nothing binds `management.tracing.*`; the bridge is on the classpath and no span is ever sampled |
| `spring-boot-opentelemetry` | nothing binds the OTLP exporter, so Tempo receives nothing however it is configured |

Boot 4 also renamed the tracing keys: `management.tracing.enabled` is now
`management.tracing.export.enabled`, and `management.otlp.tracing.endpoint` is now
`management.opentelemetry.tracing.export.otlp.endpoint`. The old names still parse and bind to
nothing.

Custom `app.*` keys are declared in `src/main/resources/META-INF/additional-spring-configuration-metadata.json`
— that is what stops the IDE calling them unknown, and it is where a new one gets its description.

Jackson 3 is the default: use `tools.jackson.databind.*`, not `com.fasterxml.jackson.databind.*`
(annotations stay on `com.fasterxml.jackson.annotation`). `JsonNode.asText(String)` is now
`asString(String)`, and `JsonProcessingException` is the unchecked `tools.jackson.core.JacksonException`.

### Tailwind dark mode

`darkMode: ['selector', '[data-theme="dark"]']` — both `AdminLayout.astro` and `BaseLayout.astro` set `data-theme` on `<html>`. The `dark:` prefix responds to that attribute, not OS preference.

### Key env vars

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | HS256 signing key (min 32 bytes) |
| `NOTIFICATION_PROVIDER` | Fallback channel when `system_settings.notification_providers` is empty. The shop's own list — several at once — wins over this |
| `PAYMENT_GATEWAY_PROVIDER` | STUB \| MERCADO_PAGO |
| `APP_DOMAIN_EVENTS_KAFKA_ENABLED` | Enables Kafka transport |
| `APP_PRODUCT_AI_ENABLED` | Enables AI pipeline |
| `APP_PRODUCT_AI_ENGINE` | `stub` short-circuits to the fake pipeline; **any other value** (the repo ships `ollama_backend`) routes to `APP_PRODUCT_AI_OPENAI_BASE_URL`. The name does not pick a provider — the base URL does. |
| `APP_PRODUCT_AI_OPENAI_INFER_MODEL` | Text model (default: `gpt-4.1-mini`) |
| `APP_PRODUCT_AI_OPENAI_IMAGE_MODEL` | Image model (default: `gpt-image-1`) |
| `APP_INVENTORY_REMOTE_ENABLED` | Delegate inventory write commands (`reserve/release/confirm`) to microservice |
| `APP_ORDER_REMOTE_ENABLED` | Delegate order reads to microservice |
| `APP_PAYMENT_REMOTE_ENABLED` | Delegate payment reads to microservice |
| `APP_CACHE_REDIS_ENABLED` | Redis hot-read cache for categories + settings |
| `APP_TRACING_ENABLED` | OTLP distributed tracing to Tempo |

Copy `infra/.env.example` → `infra/.env` before first run.
