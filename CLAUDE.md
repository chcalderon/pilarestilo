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
(`APP_DOMAIN_EVENTS_KAFKA_ENABLED`, `APP_CACHE_REDIS_ENABLED`, `APP_TRACING_ENABLED`). Those flags
register Kafka listeners and a Redis connection factory at startup, so the containers behind them
have to exist: without the matching profiles the backend cannot resolve `kafka` or `redis` and
dies in a restart loop (`No resolvable bootstrap urls given in bootstrap.servers`). The
`microservices` profile now brings up only `notification-service`.

```bash
# Full stack matching the shipped .env — runs at http://localhost
cd infra && docker compose --env-file .env \
  --profile kafka --profile cache --profile microservices \
  --profile observability --profile tracing up -d --build
```

To run the minimal stack (postgres + backend + frontend + caddy) instead, first set
`APP_DOMAIN_EVENTS_KAFKA_ENABLED=false`, `APP_CACHE_REDIS_ENABLED=false`, `APP_TRACING_ENABLED=false`
in `.env` and drop `--profile microservices` (that skips `notification-service`, whose Kafka
listeners a no-Kafka stack cannot serve anyway):

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
Java process on the VPS. Its database and indices live in `infra/storage/sonar/`, so the history
survives `docker compose down -v`.

One command scans everything with coverage:

```bash
SONAR_TOKEN=<token> bash scripts/quality/sonar-scan.sh
```

**Do not run `mvn verify` while the app compose stack or SonarQube is up.** On Docker Desktop the
Testcontainers Postgres a `*IT` needs and SonarQube's embedded Elasticsearch fight over the VM's
disk IO and ephemeral ports — the ITs start failing with `Connection to localhost:NNNNN refused`.
Stop everything Docker-side, run `mvn verify` (or `mvn test jacoco:report` for a coverage-only
pass), then bring SonarQube up just for the `sonar:sonar` upload. The SonarQube service is heap-
capped (`SONAR_*_JAVAOPTS`, `mem_limit: 2g`) so it is a lighter neighbour, but stopped is still
best. If `mvn clean` fails `Failed to delete target/site/jacoco/…`, the VS Code `redhat.java`
Language Server is holding the handle: `rm -rf backend/target`, kill stray `java` forks, or run
"Java: Disable autobuild" from the Command Palette.

Sonar Community has **no taint analysis**, so it never follows a value from a request into a query
— it says so in a banner on every project. That gap is covered locally and for free by
`scripts/quality/security-scan.sh`: Find Security Bugs (bytecode taint for Java, run with
`mvn -P security verify`), Semgrep (source rules for Java and TypeScript) and OSV-Scanner (known
CVEs in dependencies, which no edition of Sonar covers). Findings refused on purpose live in
`config/spotbugs-noise.xml`, each with its reason; anything not listed there is expected to be
fixed.

The default quality gate is **PilarEstilo sin smells**, and it applies to every project and every
language: `new_violations = 0` — a single new smell, bug or vulnerability fails it — plus 60%
coverage and under 3% duplication on new code, all hotspots reviewed, and maintainability rating A
overall so the existing backlog cannot grow. Clean as you code: the ~890 smells already there do not
block, and are burned down deliberately rather than by a gate nobody can turn green.

---

## Architecture

### Monorepo layout

```
backend/          Spring Boot monolith (hexagonal, 21 modules)
services/         Extracted microservice (optional profile)
  notification-service   (own DB, Kafka-only; the four read/write shims —
  product/inventory/order/payment-service — were consolidated back into the monolith 2026-08-30)
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

Modules: `product`, `category`, `inventory`, `order`, `payment`, `discount`, `review`, `wishlist`, `customercredit`, `systemsettings`, `productai`, `cashregister`, `dispatch`, `dashboard`, `user`, `navigation`, `location`, `publication`, `customeraddress`, `billing` + `shared` (auth, rbac, kafka, common domain). (`notification` was extracted to `services/notification-service/` and deleted from the monolith — T16.)

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

All traffic enters through Caddy. The notification read/mark-as-read paths route to
notification-service; everything else is served by the monolith.

| Pattern | Upstream |
|---|---|
| `GET/HEAD/PUT /api/notifications*` | notification-service:8085 (the whole resource, reads and mark-as-read writes), `backend:8080` fallback |
| everything else under `/api/*` | backend:8080 |
| everything else | frontend:4321 |

(product/inventory/order/payment-service were consolidated back into the monolith 2026-08-30 —
those `/api/*` paths now fall through to `backend:8080` like everything else.)

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

### notification-service reads the monolith's tables

The monolith writes `orders` / `order_items` directly again (2026-08-30 consolidation — the old
`order-service` two-writer hazard, and the five bugs it caused, are retired). But
`services/notification-service/` is still a **read-only** third party on the shared DB: it maps
minimal `insertable=false` views of `orders`, `order_items`, `users`, `payments`, `sales_documents`,
`return_requests` and `system_settings`, under `ddl-auto: validate`. **A column rename or drop on
any of those must update the matching `*RoEntity` in
`notification-service/.../persistence/readonly/` in the same commit and deploy**, or the service
fails to boot. `ReadOnlyMappingIT` (runs the monolith's real migration set) turns that into a red
local test.

### Every in-process `@EventListener` is dead when Kafka is on

`KafkaDomainEventPublisher` is `@Primary`, so with `APP_DOMAIN_EVENTS_KAFKA_ENABLED=true` only the
Kafka listeners run. Four separate defects came from a twin drifting from its in-process original,
or never existing. Behaviour lives in a `*NotificationDispatcher`, and the listeners are transport
adapters with none of their own. **Add behaviour to the dispatcher, never to a listener.**

**All notification code now lives in `services/notification-service/`** (Kafka-only, consumer
group `pe-notification-service`, port 8085). The monolith's `notification` / `notifications`
packages were deleted in the cleanup deploy (T16) — with them went `PersistenceConfig` /
`EntityScanConfig` and the second `pilarestilo_notifications` datasource, so the backend is back to
one Boot-autoconfigured JPA datasource. The `notification` rule above still applies inside the
service. Rollback is no longer a flag flip: it needs `git revert` of the cleanup commit plus
`APP_NOTIFICATION_LISTENERS_ENABLED=false` on the service. **V98 dropped the old `notifications`
table on the main DB** (2026-09-04) — a week-plus of stable prod traffic through
notification-service's own separate `pilarestilo_notifications` database was enough confidence to
close that door, so a revert of the cleanup commit would now also need a new migration to recreate
the table. `infra/postgres/init/01-notifications-database.sh` is unrelated and stays: it creates
that separate database, not this table.
The monolith still ships `spring-boot-starter-mail` — `SmtpPasswordResetMailer` (`shared/auth`)
uses `JavaMailSender` directly and reads `EMAIL_SMTP_*` (env or `system_settings`).

### Domain events

`DomainEventPublisher` port has two adapters selected by env:
- **Default**: in-process Spring `ApplicationEventPublisher`
- **Kafka** (`APP_DOMAIN_EVENTS_KAFKA_ENABLED=true`): `KafkaDomainEventPublisher`

Key flows: `OrderCreated` → payment registration; `PaymentConfirmed` → order paid; `PaymentRejected` → order cancelled + inventory compensation (`OrderInventorySaga`).

### Database migrations

Flyway manages all schema changes. Migrations live in `backend/src/main/resources/db/migration/`. Current highest: **V98**. Never edit an already-applied migration — always add a new `V{n+1}__description.sql`.

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
- (V82–V92 not listed individually — see the migration folder: notification_providers set,
  variant templates V89/V90, password-reset + session_version V91, seed featured categories V92)
- V93: contract half of V69/V87 — drop `products.variant_type`, `categories.defines_variant_fields`,
  `categories.variant_field_config` (dead since the `varianttemplate` module shipped; `category_type`
  kept). CHECK constraints drop with the columns
- V94: external sale intake (Fase 2 F) — `orders.customer_id` becomes nullable (an off-platform
  sale has no account), `orders.delivery_method` (`SHIPPING`/`PICKUP`, default `SHIPPING`,
  backfilled), `orders.buyer_name`/`buyer_contact` (free-text snapshot, nullable),
  `orders.external_idempotency_key` (partial unique index), + `orders.create` permission for
  ADMIN and SELLER. `RegisterExternalSaleUseCase` + `POST /api/admin/sales/external` create a
  born-PAID order via reserve+confirm; a `PICKUP` order skips the dispatch queue.
- V95: `payments.gateway_flag` + `gateway_flagged_at` — a gateway-reported refund/chargeback after
  approval flags the payment for manual review instead of silently doing nothing
- V96: `orders.idempotency_key` (separate from V94's `external_idempotency_key`, which is scoped
  to external sales only) — a client-minted key so a refresh mid-request or a fast double-click on
  checkout can't create two real orders
- V97: repairs the shipping-zone comuna seed — LOCAL only had 4 of the Aconcagua valley's 10 real
  comunas, REGIONAL shipped with an empty comuna list since V42 despite its old "V Region y RM"
  name; retitled to just the rest of the Valparaíso region (Santiago/RM falls to NACIONAL now)
- V98: drops the old `notifications` table on the main DB — dead since the T16 cleanup deploy
  deleted the monolith's own notification code; kept until this migration as the data-side half of
  a rollback path, closed after a week-plus of stable prod traffic through notification-service's
  own separate database

### Notifications go out on every enabled channel

`system_settings.notification_providers` is a **set**, not a value (V82). `SystemSettingsNotificationSender`
(now in `services/notification-service/`) fans a message out to all of them, each inside its own try,
so a dead SMTP host cannot take WhatsApp down with it; if none accepts, that is logged as an error
rather than passing for silence. Before V82 the column held one provider and the sender switched on
it, so turning on WhatsApp silently stopped every email — including the written confirmation the Ley
21.398 requires.

The panel enforces one rule the domain repeats: the last active channel cannot be turned off.
`NOTIFICATION_PROVIDER` (on the notification-service container) remains as the fallback for a row
nobody has saved yet.

### Spring Boot 4 modular auto-configuration

Boot 4 split `spring-boot-autoconfigure` into per-technology modules. Putting a library on the
classpath no longer enables its auto-configuration — the matching `spring-boot-*` module must be
declared too, or the feature silently does nothing. The backend already declares:

| Module | Without it |
|---|---|
| `spring-boot-flyway` | migrations never run → `ddl-auto: validate` fails on missing tables |
| `spring-boot-restclient` | no `RestClient.Builder` bean → the MercadoPago gateway, ProductAI and n8n clients fail to wire |
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
| `APP_CACHE_REDIS_ENABLED` | Redis hot-read cache for categories + settings |
| `APP_TRACING_ENABLED` | OTLP distributed tracing to Tempo |

Copy `infra/.env.example` → `infra/.env` before first run.
