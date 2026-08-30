# Pilar Estilo - Roadmap

This roadmap is synced with the current codebase on `master` as of May 7, 2026.

## v1 - Initial Release (Completed)

- [x] Product catalog CRUD
- [x] Order lifecycle with state transitions
- [x] Semi-manual payments
- [x] Discounts and promo codes
- [x] Customer credit balance + movement history
- [x] Inventory reservation on order creation
- [x] Bilingual storefront (`/es` and `/en`)
- [x] Admin panel base pages
- [x] Docker Compose + Caddy reverse proxy
- [x] Flyway migrations + seed data

---

## v2 - Auth, Taxonomy, Reviews, Admin UX (Completed)

- [x] JWT auth (`/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`, `/api/auth/me`, `/api/auth/me/profile`, `/api/auth/me/password`)
- [x] SSR admin guard in Astro middleware (`/admin/**`)
- [x] Roles in JWT claims (`ADMIN`, `SELLER`, `CUSTOMER`)
- [x] Category taxonomy + category filter on products
- [x] Review module (create/list/approve/delete)
- [x] Product denormalized rating summary (`avg_rating`, `review_count`)
- [x] Editorial storefront redesign + luxury brand tokens
- [x] Admin layout with reusable data table patterns
- [x] Admin mobile drawer navigation + mobile card rendering for data tables
- [x] Admin product view toggle (`Grilla`/`Cards`) with persisted preference
- [x] Admin product filters include category + server-backed pagination
- [x] Admin user management hub (`/admin/users`) with role/status/password/customer-credit controls
- [x] Admin users server-side pagination by role tab + status filter (`todos`, `habilitados`, `bloqueados`)

---

## v2.1 - Catalog and Commerce Refinements (Completed)

- [x] Wishlist backend module (`/api/wishlist`) and storefront page (`/[locale]/wishlist`)
- [x] Product search endpoint (`GET /api/products/search?q=...`) + search overlay UI
- [x] Dual product pricing support (`price` + optional `listPrice`) with storefront discount rendering
- [x] Quick star-only in-card rating flow for authenticated customers
- [x] Per-size stock data model foundation (`product_size_stocks`, `sizeStocks` in product DTO)
- [x] Chile-first defaults migration (`CLP` currency defaults + shipping origin normalization)
- [x] Extended catalog DB migrations (`V7` to `V16`)
- [x] Storefront mobile header/navigation polish: action-icon reflow + category slider controls + UTF-8 Spanish copy normalization
- [x] Navbar logo transition stabilized to avoid compact/full flicker loop on slight scroll
- [x] Backend-managed product media routes with Docker-persisted local storage (`/api/media/**` -> `infra/storage/media`)

---

## Baseline Guardrails (Active)

- [x] Chile-first commerce defaults (`CLP`, local shipping labels/copy)
- [x] Admin routes remain outside locale prefix (`/admin/*`)
- [x] Admin write operations require JWT authentication
- [x] Category-product assignment flow stays available in admin product form
- [x] Canonical local run path: `infra/docker-compose.yml` + `infra/.env`

---

## P3 - Payments and Notifications

- [x] Payment gateway adapter via `PaymentGatewayPort` (`STUB` + configurable `MERCADO_PAGO`)
- [x] Gateway webhook receiver (`POST /api/payments/webhooks/gateway`) with optional signature guard
- [x] Gateway checkout session endpoint for `PAYMENT_GATEWAY` payments (`POST /api/payments/{id}/gateway/checkout`) using current stub adapter
- [x] Temporary gateway simulation controls in storefront/admin while production provider onboarding is pending
- [x] WhatsApp notifications (simulated provider for development: `WHATSAPP_SIMULATED`)
- [x] WhatsApp production-ready provider integration (`WHATSAPP_TWILIO`)
- [x] Customer phone capture/profile wiring for per-user WhatsApp destination (`/api/auth/me/profile` now supports `phone`)
- [x] Customer notification channel preference (`AUTO` / `WHATSAPP` / `EMAIL` / `BOTH`) from account profile
- [x] Email notifications (SendGrid and direct SMTP provider)
- [x] n8n webhook notification provider (`N8N_WEBHOOK`) for external orchestration flows
- [x] Customer order history and receipts in account area (`GET /api/orders/mine` + `AccountPage` orders tab)
- [x] Customer payment-proof self-service in account area (`GET /api/payments/order/{orderId}` + submit proof from `AccountPage`)
- [x] Admin payment queue visibility for `PENDING` + review actions restricted to actionable statuses

---

## P4 - Catalog and Buying Experience

- [x] Wishlist core (persisted favorites per authenticated user)
- [x] Search core (keyword search API + storefront overlay)
- [x] Shareable wishlist links
- [x] Full product variants (size/color combinations with dedicated admin UX)
- [ ] Direct media upload to S3/R2 from admin product form
- [x] Order tracking timeline in customer account

---

## P5 - Event-Driven Upgrade

- [x] Kafka infrastructure
- [x] `KafkaDomainEventPublisher` as primary adapter
- [x] Migrate in-process listeners to `@KafkaListener`
- [x] Retry/DLQ strategy
- [x] Sagas for order-inventory consistency

---

## P6 - Microservices Extraction

> **Reverted 2026-08-30.** `product` / `inventory` / `order` / `payment-service` were consolidated
> back into the monolith — they were thin layers over the shared DB with no throughput benefit at
> this scale (a load test ran 9 concurrent purchase flows at load 2.5 / 6 cores). The one real
> extraction, `notification-service` (own DB, Kafka-only), stays. The `[x]` items below are kept as
> the historical record. See `docs/superpowers/plans/2026-08-30-consolidate-shim-services.md`.

- [x] Extract `product` read service (`services/product-service`) with compatible `GET /api/products*` endpoints
- [x] Route gateway read traffic (`GET/HEAD /api/products*`) to extracted `product-service` through Caddy
- [x] Extract `inventory` read service (`services/inventory-service`) with compatible `GET /api/inventory*` endpoints
- [x] Route gateway read traffic (`GET/HEAD /api/inventory*`) to extracted `inventory-service` through Caddy
- [x] Extract `inventory` write paths (`reserve/release/confirm`) into dedicated service
- [x] Extract `order` query service (`services/order-service`) with backend delegation toggle (`APP_ORDER_REMOTE_ENABLED`)
- [x] Extract `payment` query service (`services/payment-service`) with backend delegation toggle (`APP_PAYMENT_REMOTE_ENABLED`)
- [x] Route gateway read traffic (`GET/HEAD /api/payments*`) to extracted `payment-service` with auth offload
- [x] Extract `order` write paths into `order-service` with backend delegation toggle (`APP_ORDER_REMOTE_WRITE_ENABLED`)
- [x] Move public `/api/orders/**` traffic to extracted order-service (gateway/auth offload phase)
- [x] Introduce API gateway policies (routing, auth offload, rate limits)

---

## P7 - Observability and Scale

- [x] Prometheus scrape endpoint and metrics pipeline
- [x] Grafana dashboards (baseline JVM/HTTP/DB pool overview)
- [x] Distributed tracing baseline with OpenTelemetry Collector + Tempo
- [x] Redis-backed hot-read cache baseline (optional profile) for categories + public store settings
- [x] Horizontal backend scaling behind reverse proxy (`docker compose --scale backend=N`)
- [x] ~~Postgres read-replica routing baseline for catalog queries in `product-service`~~ (removed with the P6 revert, 2026-08-30)

---

## P8 - Operations and Worker Tools (Completed)

- [x] Worker role system: `SELLER`, `DESPACHADOR`, `ADMINISTRACION`, `SUPERVISOR` worker roles with vigency dates (`vigencyStart` / `vigencyEnd`) enforced per-request
- [x] RBAC permission matrix: granular named permissions (e.g. `despachos`) assignable per worker via `PUT /api/admin/permissions`; checked alongside role in `@PreAuthorize` expressions
- [x] Worker management endpoints (`GET /api/admin/workers`, `POST /api/admin/workers/{id}/assign`, `DELETE /api/admin/workers/{id}/revoke`) and frontend `roles-permisos` admin page
- [x] Discount user assignment: a discount code can be tied to a specific `CUSTOMER` user at creation (`assignedUserId` on `POST /api/discounts`) so only that customer can apply it. There is no separate assign-user endpoint.
- [x] In-app notifications: `NotificationController` (`GET /api/notifications`, `/unread-count`, `PUT .../read`, `PUT .../read-all`) with types `DISCOUNT_CODE_ASSIGNED`, `ORDER_CONFIRMED`, `PAYMENT_RECEIVED`, `ORDER_PREPARING`, `ORDER_SHIPPED`; `NavNotificationBell` island in storefront navbar
- [x] Cash register (Caja) module: open/close register, manual movements, paginated seller history (`GET /api/caja/history`) and cross-seller admin view (`GET /api/admin/caja`); admin `caja` page
- [x] Dispatch (Despachos) module: claim/unclaim/dispatch/deliver/fail lifecycle for `DESPACHADOR` workers; admin dispatch queue and history (`GET /api/admin/despachos/history`) with `dispatchedBy` / `soldBy` enrichment; `DispatchAutoDeliveryScheduler` auto-confirms shipments older than 15 days
- [x] Customer confirm-delivery endpoint (`PATCH /api/orders/{id}/confirm-delivery`) with symmetric dispatch `DELIVERED` update
- [x] Role-aware dashboard stats (`GET /api/dashboard/stats`): sealed domain model with four role-specific payloads (Admin, Seller, Despachador, Administracion); Recharts revenue chart for admin; frontend `DashboardPage` island
- [x] Inline register/login popover: `UserPlus` icon in storefront navbar opens anchored popover panel (desktop) / bottom sheet (mobile) with tabbed register+login form and Google Identity Services sign-in — no full-page navigation required

---

## Inventory Evolution (Completed — 2026-05-16)

- [x] Optimistic locking on `products` and `cash_registers` (`@Version` + Flyway V54)
- [x] `order_items` variant snapshot columns (`variant_color`, `variant_size`) — Flyway V55
- [x] Reserved-stock model: `product_variants.stock` → `stock_on_hand` + `stock_reserved` — Flyway V56
- [x] `inventory_movements` audit table (RESERVE/CONFIRM/RELEASE/ADJUSTMENT/POS_SALE/RETURN/MANUAL) — Flyway V57
- [x] `InventoryService`: `reserve()` increments `stock_reserved`; `confirm()` decrements both; `release()` decrements `stock_reserved` only; `posSale()` decrements `stock_on_hand` directly
- [x] Available stock = `stock_on_hand − stock_reserved`; `ProductVariant.getStock()` alias retained for backward compat

---

## Navigation Redesign (Completed — 2026-05-16)

- [x] `category` extended with `menu_visible`, `category_type` (GENERIC/CLOTHING/SHOES/JEWELRY/ACCESSORY/COLLECTION/SEASON), `hero_image_url` — Flyway V58
- [x] `navigation_sections` table: CMS-level config per root category (layout/banner/sort_order) — Flyway V59–V61
- [x] `NavigationSection` hexagonal module: domain model + ports + use cases + adapters + controllers
- [x] `GET /api/navigation/tree?locale=es` — SSR-optimized public endpoint combining sections + category tree
- [x] `GET /api/navigation/sections` (public) + `POST/PATCH/DELETE /api/admin/navigation/sections` (ADMIN)
- [x] `MegaMenuTray.tsx`: desktop hover-intent tray, motion stagger, editorial banner slot, FEATURED_GRID layout
- [x] `MobileNavOverlay.tsx`: full-screen push-navigation (root→section→child), slide motion, bottom bar, scroll lock
- [x] `motion` npm package installed for nav transitions; `prefers-reduced-motion` respected throughout
- [x] Admin manager `NavigationSectionsManager.tsx` at `/admin/navegacion`
- [x] Modern RBAC: `permissions` + `role_permission_grants` tables with code-based permission catalog — Flyway V62–V64
- [x] Social commerce foundation: `publications` table for multi-platform content lifecycle — Flyway V65

---

## P9 - Product AI Pipeline and Campaign Ops (In Progress)

- [x] Technical design + API contracts documented (`docs/ai-product-pipeline-integration.md`)
- [x] Admin UX split validated:
  - `Productos` keeps individual fast flow
  - new `Publicaciones e Imagenes` module for massive workflows
- [x] Admin navigation and page shell added for `/admin/publicaciones`
- [x] Backend `productai` module baseline (drafts/assets/jobs/retries + scheduler)
- [x] Flyway `V40` schema for product AI pipeline
- [x] Frontend job status wiring from `/admin/publicaciones` to backend APIs
- [x] OpenAI migration: `ProductAiOpenAiClient` uses separate models for text inference (`APP_PRODUCT_AI_OPENAI_INFER_MODEL`) and image generation (`APP_PRODUCT_AI_OPENAI_IMAGE_MODEL`); Ollama removed from pipeline and Docker Compose
- [x] Flyway `V41` adds Product AI infer-default admin settings fields
- [ ] n8n campaign workflow templates (Instagram/Facebook posting orchestration)

---

## UX Improvements (Ongoing)

- [x] Admin user management: `UserEditDrawer` slide-out panel replaces modal dialogs
- [x] Admin credit section visible for all user types (not just CUSTOMER)
- [x] Admin category tree supports up to 4 levels of subcategories
- [x] Server-side image optimization on upload (`ImageOptimizerService`)
- [x] Storefront product detail: subcategory pills + product header image
- [x] Landing carousel: CSS transform-only mode, touch swipe, dark-mode dots, 4s index-based autoplay
- [x] Landing category carousel: featured nodes at any tree depth
- [x] Admin products: sortable `Fecha ingreso` column and date-range filters (`desde` / `hasta`) with calendar inputs
- [x] Admin products: composite size flow from multi-select (`M` + `L` => `M-L`) with extended sizes (`XXL`, `XXXL`) and stock consistency by variant
- [x] Admin products: `Tomar foto` uses direct camera capture workflow (desktop/mobile) with graceful file-picker fallback
- [x] Admin products: unsaved-change close confirmation moved from browser alert to in-app modal UX
- [x] Admin settings: Shipping tab with configurable zones (`shipping_zones_json`), couriers (`shipping_couriers_json`), and payment mode (`shipping_payment_mode`); Flyway V42+V43; storefront `shipping-returns` page and `DeliveryEstimate` component now fetch live zone/ETA data
- [x] Checkout shipping selection now uses customer address book (`shippingAddressId`) with immutable shipping snapshot on order; dispatch queue pre-fills configured courier using dispatch-level shipping snapshot and stores structured carrier-override audit (`carrierOverrideConfigured`, `carrierOverrideSelected`, `carrierOverrideBy`, `carrierOverrideAt`)
- [x] Customer addresses enabled in account + checkout flow (`/api/auth/me/addresses`, CRUD + default address), with mandatory address selection before checkout submit
