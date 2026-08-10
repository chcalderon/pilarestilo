# Pilar Estilo - Architecture

This file documents the architecture currently implemented in the repository.

## 1. Overview

Pilar Estilo is a monorepo ecommerce platform with:

- SSR storefront and admin UI
- REST backend with domain-first modules
- PostgreSQL persistence
- Docker Compose local/prod baseline
- Caddy reverse proxy for routing/TLS

Monorepo layout:

```text
PilarEstilo/
  backend/
  services/
    inventory-service/
    order-service/
    payment-service/
    product-service/
  frontend/
  infra/
  docs/
```

---

## 2. Frontend

### Stack

- Astro 4 (SSR + i18n routes)
- React islands for interaction
- Tailwind with brand tokens
- Zustand for auth, cart, and wishlist state

### Route model

- Storefront: `/es/*` and `/en/*`
- Admin: `/admin/*` (no locale prefix)

### Notable implemented features

- JWT-aware SSR middleware guard for admin routes
- Search overlay wired to `/api/products/search`
- Product cards with dual-price support (`price` + optional `listPrice`)
- Wishlist page and heart actions
- Product detail size selector consuming `sizeStocks`
- Quick in-card star-only rating for authenticated users
- Account self-service profile/password management
- Admin user management surface (`/admin/users`) with role/status/password/credit actions
- Payment-gateway simulation controls in account/admin workflows for non-production environments
- Checkout en `cart` con seleccion de envio basada en libreta de direcciones (`shippingAddressId`) y snapshot de referencia de envio persistido en orden
- `Mi cuenta` incluye gestion de direcciones de cliente (crear, editar, eliminar, marcar principal)
- Direcciones de cliente con `regionId/cityId/comunaId` obligatorios y nombres canónicos derivados de catálogo geográfico (`geo_regions -> geo_cities -> geo_communes`)
- Formularios de dirección en `cart` y `mi cuenta` ahora usan combos dependientes (región -> ciudad -> comuna), sin edición manual de esos campos
- Configuración de envíos en admin usa búsqueda de comunas contra catálogo (`/api/locations/comunas/search`) para poblar zonas
- Account orders muestra snapshot de envio y permite confirmacion de entrega cuando la orden esta en `SHIPPED`

---

## 3. Backend

### Stack

- Java 25 + Spring Boot 4.0.7
- Spring Security + JWT filter
- Spring Data JPA + Hibernate
- Spring Cache abstraction with optional Redis-backed cache manager
- Flyway migrations
- PostgreSQL 16

### Architecture style

Hexagonal (Ports and Adapters):

- `domain/` for pure business model and ports
- `application/` for use-case orchestration
- `infrastructure/` for web, persistence, listeners, adapters

Rule: no Spring/JPA annotations inside `domain/`.

### Modules in codebase

- `product`
- `category` — extended with `menu_visible`, `category_type` (enum: GENERIC/CLOTHING/SHOES/JEWELRY/ACCESSORY/COLLECTION/SEASON), `hero_image_url`
- `review`
- `order`
- `payment`
- `discount`
- `inventory` — reserved-stock model (`stock_on_hand` + `stock_reserved`), `inventory_movements` audit
- `user`
- `customercredit`
- `cashregister`
- `dispatch`
- `dashboard`
- `productai`
- `wishlist`
- `notification`
- `systemsettings`
- `customeraddress`
- `navigation` — `NavigationSection` CMS-level config (layout, banners, sort order per root category); `GET /api/navigation/tree` (public), `GET /api/navigation/sections` (public), `POST/PATCH/DELETE /api/admin/navigation/sections` (ADMIN)
- `location` — geographic catalog (`geo_regions` → `geo_cities` → `geo_communes`); `GET /api/locations/comunas/search`
- `publication` — social commerce content lifecycle (`publications` table, multi-platform: INSTAGRAM/FACEBOOK/etc.)
- `shared` (`auth`, `rbac`, `domain`, common infra)

### Navigation architecture

- `GET /api/navigation/tree?locale=es` is the SSR endpoint for the mega menu (public, no auth).
- Response combines active `NavigationSection` records with the Category tree in a single roundtrip.
- `NavigationSection` is CMS-level config per root category: layout (`COLUMNS` / `FEATURED_GRID` / `EDITORIAL`), column_count (1–6), banner fields (image, title, subtitle, link), sort_order.
- `menu_visible=true` categories appear in the nav tree (separate from catalog visibility).
- When `navigation_sections` table is empty, `GetNavigationTreeUseCase` falls back to auto-generating virtual sections from menu_visible root categories.
- Admin CRUD at `/api/admin/navigation/sections` (ADMIN role); storefront admin UI at `/admin/navegacion`.
- Frontend: `MegaMenuTray.tsx` (motion.dev desktop hover tray, 120ms intent delay, 200ms grace, stagger fade) + `MobileNavOverlay.tsx` (push-navigation stack, slide from right, bottom bar).
- motion library (`motion` npm package, ≈17KB gz) installed for nav transitions; respects `prefers-reduced-motion`.

### Inventory reserved-stock model

- Ecommerce `createOrder` → increments `stock_reserved` (not `stock_on_hand`).
- `paymentApproved` → `confirm()` decrements both `stock_on_hand` and `stock_reserved`.
- `paymentRejected/cancelled` → `release()` decrements `stock_reserved` only.
- POS `posSale()` → decrements `stock_on_hand` directly.
- `InventoryMovementType`: `RESERVE / CONFIRM / RELEASE / ADJUSTMENT / POS_SALE / RETURN / MANUAL`.
- Non-variant products still use legacy aggregate `products.stock` (decremented at reserve; confirm is no-op).
- `inventory_movements` records every operation for audit.

Dispatch and shipping architecture highlights:

- `orders` persiste seleccion de envio en checkout (zona/courier/referencia + `shippingPaymentMode` snapshot).
- `dispatches` persiste snapshot de envio de la orden al crearse (estado `PAID`).
- `GET /api/despachos` lee `orderShipping*` directamente desde `dispatches` (sin enriquecimiento por consulta N+1 por orden).
- Carrier override queda auditado en columnas estructuradas de `dispatches` (`carrierOverrideConfigured`, `carrierOverrideSelected`, `carrierOverrideBy`, `carrierOverrideAt`).

### Media delivery

- Backend serves static product media via `/api/media/**`.
- `MediaResourceConfig` maps that route to `app.media.storage-path` (filesystem).
- Docker Compose binds that path to `infra/storage/media` for persistence.
- `MediaStorageService` runs uploaded images through `ImageOptimizerService` before persisting: auto-resize + JPEG compression applied on every `POST /api/media/upload`.

---

## 4. Eventing Model

The project uses a `DomainEventPublisher` port with runtime-selectable transport:

- default: in-process Spring events (`SpringDomainEventPublisher`)
- optional: Kafka (`KafkaDomainEventPublisher`) when `APP_DOMAIN_EVENTS_KAFKA_ENABLED=true`

Current listeners include:

- `OrderCreated` -> payment registration
- `PaymentConfirmed` -> order moved to `PAID`
- `PaymentRejected` -> order cancellation + stock compensation (`OrderInventorySaga`)
- Review events -> product rating denormalization
- Notification listeners for order/payment confirmation hooks
- Notification providers selectable by env (`LOG`, `WHATSAPP_SIMULATED`, `WHATSAPP_TWILIO`, `EMAIL_SENDGRID`, `EMAIL_SMTP`, `N8N_WEBHOOK`)
- Notification recipient carries per-user channel preference (`AUTO`, `WHATSAPP`, `EMAIL`, `BOTH`) used by adapters.
- `N8N_WEBHOOK` can now be configured in admin system settings with encrypted API key and env fallback.

Kafka mode includes retry + DLQ through a dedicated listener container factory.
Operational n8n setup reference: `docs/n8n-integration.md`.

---

## 5. Data and Migrations

Flyway migrations currently include baseline plus catalog refinements:

- `V1` to `V6`: core schema + auth/categories/reviews + seed updates
- `V7`: search indexes (`pg_trgm`)
- `V8`: per-size stock table + shipping origin
- `V9`: wishlist schema
- `V10`: Chile defaults (`CLP`, shipping zone normalization, category associations)
- `V11`: seeded product image URLs moved to backend media routes (`/api/media/products/*.jpg`)
- `V12`: user active-flag support for account blocking/unblocking workflows
- `V13`: singleton system settings + encrypted SMTP credential storage
- `V14`: user phone capture for profile + notification routing
- `V15`: product list-price schema support + DB constraints
- `V16`: list-price default backfill for existing products
- `V17`: runtime notification provider settings + encrypted Twilio/SendGrid secrets
- `V18`: extended seed catalog products
- `V19`: wishlist share-link token and enablement fields
- `V20`: product variants (`color + size + stock`) with backfill from `product_size_stocks`
- `V21`: media storage provider runtime settings (`LOCAL` / `S3_COMPATIBLE`)
- `V22`: checkout payment-method toggles + gateway provider selection (`MERCADO_PAGO`)
- `V23`: bank-transfer account settings + transfer snapshot fields persisted in `payments`
- `V24`: Mercado Pago runtime settings fields in `system_settings` (with encrypted token storage)
- `V25`: per-user notification channel preference (`AUTO`, `WHATSAPP`, `EMAIL`, `BOTH`) stored in `users`
- `V26`: N8N webhook notification provider settings fields in `system_settings`
- `V27`: N8N admin runtime settings (webhook URL, encrypted API key, token header name)
- `V28`: `bank_transfer_bank_name` column in `system_settings` + `transfer_bank_name` snapshot field in `payments` with CHECK constraint enforcing full transfer snapshot for `BANK_TRANSFER` payments
- `V29`: category seed restoration refresh
- `V30`: discount code usage ledger (`discount_code_usages`)
- `V31`: per-user discount assignment + persistent in-app notifications
- `V32`: featured category flag support
- `V33`: user avatar storage fields
- `V34`: manual avatar override marker
- `V35`: worker vigency range (`vigency_start`, `vigency_end`)
- `V36`: role-permission matrix persistence
- `V37`: seeded default role-permission matrix
- `V38`: cash register schema (`cash_registers`, `cash_movements`)
- `V39`: dispatch schema (`dispatches`)
- `V40`: product AI pipeline (`product_ai_drafts`, `product_ai_assets`, `product_ai_jobs`, `product_ai_outputs`)
- `V41`: Product AI infer-default fields in `system_settings` (`productAiInferDefaultBrand`, `productAiInferDefaultCondition`, `productAiInferBasePrice`, `productAiInferListPriceMultiplier`)
- `V42`: shipping settings baseline
- `V43`: shipping origin/zone rename alignment
- `V44`: product stock synchronization from variants
- `V45`: expanded size columns for composite sizes (`product_variants.size`, `product_size_stocks.size`)
- `V46`: shipping selection persisted on `orders` (`shipping_zone_code`, `shipping_courier_id`, `shipping_courier_name`, `shipping_payment_mode`, `shipping_address_reference`)
- `V47`: dispatch shipping snapshot + structured carrier override audit on `dispatches` (with one-time backfill from `orders`)
- `V48`: customer address book (`customer_addresses`), default-address constraints/indexes, and `orders.shipping_address_id`
- `V49`: catálogo geográfico Chile (`geo_regions`, `geo_cities`, `geo_communes`) + FK opcionales en `customer_addresses` (`region_id`, `city_id`, `commune_id`) con backfill best-effort
- `V50`: bank-transfer auto-cancel baseline
- `V51`: `orders.sales_channel` column
- `V52`: payment method rename alignment
- `V53`: cash movement category column
- `V54`: `products.version` + `cash_registers.version` (optimistic locking `@Version`)
- `V55`: `order_items.variant_color` + `variant_size` (nullable snapshot, no backfill)
- `V56`: `product_variants.stock` → `stock_on_hand` + ADD `stock_reserved` (reserved-stock model)
- `V57`: `inventory_movements` audit table (`RESERVE/CONFIRM/RELEASE/ADJUSTMENT/POS_SALE/RETURN/MANUAL`)
- `V58`: `categories.menu_visible BOOLEAN DEFAULT TRUE` + `category_type VARCHAR(24) DEFAULT 'GENERIC'` + `hero_image_url VARCHAR(500)`
- `V59`: `navigation_sections` table (id UUID PK, root_category_id FK ON DELETE CASCADE, layout, column_count CHECK 1–6, banner_image/title/subtitle/link, active, sort_order, timestamps, UNIQUE root_category_id)
- `V60`: idempotent seed of `navigation_sections` from root categories (`ON CONFLICT DO NOTHING`)
- `V60_1`: seed `aros` subcategory under accesorios
- `V60_2`: prereq repair for retail runtime alignment
- `V61`: retail runtime alignment — sets `category_type`/`hero_image_url` on known slugs, seeds product variants with `stock_on_hand`/`stock_reserved`, sets FEATURED_GRID layout on Mujer navigation section
- `V62`: modern RBAC — `permissions` table + `role_permission_grants` table
- `V63`: seed default permission catalog (dashboard, analytics, products, categories, navigation, etc.)
- `V64`: seed default role–permission grants (ADMIN full access, SELLER subset)
- `V65`: social commerce foundation — `publications` table (multi-platform content lifecycle: INSTAGRAM/FACEBOOK, approval workflow, idempotency key)
- `V66`: repair `shipping_origin_zone` value `NATIONAL` → `NACIONAL` for enum alignment

---

## 6. Runtime Topology (Docker Compose)

`infra/docker-compose.yml` defines baseline services:

- `postgres`
- `backend`
- `frontend`
- `caddy`

Optional profiles:

- `kafka`: adds `kafka` broker for Kafka-backed domain-events mode.
- `microservices`: adds extracted services (`product-service`, `inventory-service`, `order-service`, `payment-service`).
- `cache`: adds `redis` for hot-read response caching.
- `observability`: adds `prometheus` + `grafana` with provisioned datasource/dashboard.
- `tracing`: adds `otel-collector` + `tempo` stack for distributed traces.

Caddy now applies a read-routing policy for catalog endpoints:

- `GET`/`HEAD /api/products*` -> `product-service` (when `microservices` profile is running)
- `GET`/`HEAD /api/inventory*` -> `inventory-service` (when `microservices` profile is running)
- `GET`/`HEAD /api/payments*` -> `payment-service` (JWT auth enforced in `payment-service`)
- `GET`/`HEAD /api/orders*` -> `order-service` (when `microservices` profile is running; with backend fallback)
- `POST`/`PATCH /api/orders*` -> `backend` (auth/orchestration entrypoint; backend may delegate writes with `APP_ORDER_REMOTE_WRITE_ENABLED=true`)
- remaining `/api/*` -> `backend` (dynamic DNS upstreams; supports horizontal scale with `--scale backend=N`)
- all other routes -> `frontend`

Gateway guardrails currently enforced:

- API payload cap at edge (`/api/*` max body: `12MB`)
- API invalid method rejection at edge (`405` for unsupported verbs)
- Order API method policy at edge (`GET|HEAD|POST|PATCH` only)
- Upstream proxy timeouts (`dial_timeout` + `response_header_timeout`) for extracted services and backend
- Per-IP rate limiting in backend gateway-facing filter for sensitive public POST endpoints:
  - `/api/auth/login`
  - `/api/auth/register`
  - `/api/payments/webhooks/gateway` and `/api/payments/webhooks/gateway/mercadopago`

Redis cache baseline (P7):

- Backend cache manager runs in-memory by default.
- When `APP_CACHE_REDIS_ENABLED=true`, cache storage moves to Redis (`cache` profile service).
- Current hot-read cached entries:
  - `GET /api/categories`
  - `GET /api/categories/tree`
  - `GET /api/system-settings/public`
- Category and system-settings write operations evict those caches automatically.

Inventory write extraction (P6 step 3):

- `inventory-service` now exposes stock command endpoints:
  - `POST /api/inventory/commands/reserve`
  - `POST /api/inventory/commands/release`
  - `POST /api/inventory/commands/confirm`
- Backend delegates order-driven inventory writes to those endpoints when `APP_INVENTORY_REMOTE_ENABLED=true`.
- This delegation uses internal service-to-service calls (`APP_INVENTORY_REMOTE_BASE_URL`) and keeps storefront/public routing unchanged.

Order query extraction (P6 step 4):

- `order-service` now exposes read endpoints:
  - `GET /api/orders`
  - `GET /api/orders/{id}`
  - `GET /api/orders/_health`
- Backend can delegate order reads to that service when `APP_ORDER_REMOTE_ENABLED=true`.
- Delegation uses internal service-to-service calls (`APP_ORDER_REMOTE_BASE_URL`) and keeps Caddy public routing unchanged for `/api/orders/**`.

Order write extraction (P6 step 6):

- `order-service` now exposes command endpoints:
  - `POST /api/orders`
  - `PATCH /api/orders/{id}/status`
- Backend can delegate order writes to that service when `APP_ORDER_REMOTE_WRITE_ENABLED=true`.
- Backend still publishes `OrderCreated` / `OrderStatusChanged` domain events after delegated writes so downstream payment/saga flows keep working.

Payment query extraction (P6 step 5):

- `payment-service` now exposes read endpoints:
  - `GET /api/payments`
  - `GET /api/payments/{id}`
  - `GET /api/payments/order/{orderId}`
  - `GET /api/payments/_health`
- Backend can delegate payment reads to that service when `APP_PAYMENT_REMOTE_ENABLED=true`.
- Delegation uses internal service-to-service calls (`APP_PAYMENT_REMOTE_BASE_URL`) and can include trusted `X-Service-Token` via `APP_PAYMENT_REMOTE_SERVICE_TOKEN`.

Catalog read-replica routing (P7):

- `product-service` supports optional read-replica routing for read-only transactions.
- When `APP_DB_READ_REPLICA_ENABLED=true`, read queries (`list`, `search`, `getById`) use the replica datasource.
- Write/default traffic still uses the primary datasource.
- Required replica env vars:
  - `APP_DB_READ_REPLICA_URL`
  - `APP_DB_READ_REPLICA_USERNAME`
  - `APP_DB_READ_REPLICA_PASSWORD`

Distributed tracing flow:

- Backend, `product-service`, `inventory-service`, `order-service`, and `payment-service` emit OTLP traces (Micrometer tracing bridge + OTel exporter).
- `otel-collector` receives and batches spans.
- `tempo` stores traces and Grafana reads them through provisioned datasource.

---

## 7. Engineering Workflow (TDD + Coverage)

### TDD policy used in this repository

- New behavior and bugfixes must start with failing tests (`red`) before implementation (`green`) and cleanup (`refactor`).
- Service-level changes must prefer focused unit tests for domain/application logic plus web/controller tests for contract and status codes.
- Checkout/order/payment/shipping flows must include at least one integration or E2E regression when behavior changes across modules.

### Java coverage enforcement (JaCoCo, `LINE/COVEREDRATIO`)

Coverage gates are enforced at Maven `verify` phase with `org.jacoco:jacoco-maven-plugin`.

| Module | Gate |
|---|---|
| `services/order-service` | `50%` |
| `services/inventory-service` | `50%` |
| `services/payment-service` | `50%` |
| `services/product-service` | `50%` |
| `backend` | `22%` (temporary baseline while legacy modules are raised) |

Coverage snapshot (measured on **2026-05-10**):

| Module | Line coverage |
|---|---|
| `services/order-service` | `82.92%` |
| `services/inventory-service` | `73.84%` |
| `services/payment-service` | `85.44%` |
| `services/product-service` | `59.44%` |
| `backend` | `23.27%` |

### Verification commands

```bash
# Per-module quality gate + report
mvn verify

# Fast local checks
mvn test
```

JaCoCo reports are generated at:

- `<module>/target/site/jacoco/index.html`
- `<module>/target/site/jacoco/jacoco.csv`

### Local Docker run path and session memory

- Canonical local orchestration should use script wrappers:
  - `bash scripts/deploy/local_deploy.sh up`
  - `./scripts/deploy/local_deploy.ps1 up`
  - rebuild: `bash scripts/deploy/local_rebuild.sh` or `./scripts/deploy/local_rebuild.ps1`
- These wrappers keep `infra/docker-compose.yml` + `infra/.env` as single source of truth and auto-read `DEPLOY_PROFILES`.
- To avoid losing execution context across long sessions, store checkpoints in `docs/session-memory.md` via:
  - `bash scripts/dev/save_session_memory.sh "short note"`
  - `./scripts/dev/save_session_memory.ps1 "short note"`
