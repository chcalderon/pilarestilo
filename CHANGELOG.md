# Changelog

All notable changes to this project are documented in this file.

The format is inspired by Keep a Changelog.

## [Unreleased]

### Added
- New localized storefront informational pages:
  - `/{locale}/about` (Sobre Pilar Estilo)
  - `/{locale}/how-we-sell` (Cómo vendemos)
  - `/{locale}/shipping-returns` (Envíos y devoluciones)
  - `/{locale}/contact` (Contacto)
- Horizontal backend scaling baseline behind Caddy using Docker Compose replicas (`--scale backend=N`).
- Postgres read-replica routing baseline for catalog queries in `product-service` (read-only transaction routing).
- Redis-backed cache baseline for hot storefront reads (`/api/categories`, `/api/categories/tree`, `/api/system-settings/public`) with opt-in runtime toggle.
- Optional Docker `cache` profile with Redis service (`pe_redis`) and persisted volume (`pe_redis_data`).
- Notification provider runtime settings in `system_settings` with migration `V17__notification_provider_settings.sql`.
- Admin system settings UI now includes provider selector cards (`LOG`, `WHATSAPP_SIMULATED`, `WHATSAPP_TWILIO`, `EMAIL_SENDGRID`, `EMAIL_SMTP`) and provider-specific forms.
- Encrypted-at-rest storage for Twilio auth token and SendGrid API key in admin-managed system settings.
- Product dual-pricing fields in catalog API (`listPriceAmount`, `listPriceCurrency`) with domain/persistence validation and support in admin create/update forms.
- Catalog migration `V15__product_list_price.sql` (schema + constraints) and `V16__seed_product_list_price_defaults.sql` (backfill defaults for existing products).
- Quick star-only rating widget for authenticated customers directly in storefront product cards (`QuickRateStars` island).
- Gateway checkout session endpoint: `POST /api/payments/{id}/gateway/checkout` for payments created with `PAYMENT_GATEWAY`.
- Gateway webhook endpoint: `POST /api/payments/webhooks/gateway` with optional signature validation via `X-Gateway-Signature`.
- New payment gateway webhook processor use case with idempotent handling of repeated final-state events.
- Stub gateway adapter now returns checkout session payload (`gatewayReference`, `checkoutUrl`, `expiresAt`) instead of throwing `UnsupportedOperationException`.
- Auth profile self-service endpoints: `GET /api/auth/me/profile`, `PATCH /api/auth/me/profile`, `PATCH /api/auth/me/password`.
- Admin user-management endpoints: `PATCH /api/users/{id}`, `PATCH /api/users/{id}/password`, `DELETE /api/users/{id}`.
- Admin payment panel now includes `Por revisar` and `Pagados` tabs with search, date sorting, and filter reset.
- Temporary gateway simulation controls in UI:
  - Customer account: `Simular aprobado` / `Simular rechazado` for `PAYMENT_GATEWAY` orders.
  - Admin queue: `Sim aprobar` / `Sim rechazar` actions for pending gateway rows.
- Mercado Pago provider webhook bridge endpoint: `POST /api/payments/webhooks/gateway/mercadopago`.
- Simulated WhatsApp notification adapter (`WHATSAPP_SIMULATED`) selectable by env for development flows.
- Twilio WhatsApp notification adapter (`WHATSAPP_TWILIO`) for production-ready message delivery via Twilio API.
- SendGrid email notification adapter (`EMAIL_SENDGRID`) for transactional order/payment emails.
- SMTP email notification adapter (`EMAIL_SMTP`) for direct delivery via your own mail server.
- Floating storefront WhatsApp CTA button (desktop + mobile) with locale-aware prefilled message.
- System settings module (`/api/system-settings`) with admin-managed storefront channels (WhatsApp, Instagram, Facebook).
- Admin system settings screen (`/admin/settings`) to manage storefront contact channels and SMTP configuration.
- Public settings endpoint (`GET /api/system-settings/public`) for storefront runtime channel links.
- SMTP password encryption-at-rest with AES-GCM and env-driven crypto secret (`SYSTEM_SETTINGS_CRYPTO_SECRET`).
- User profile phone capture in auth profile API (`GET/PATCH /api/auth/me/profile` with `phone`).
- User phone persistence migration (`V14__user_phone.sql`) with index support.
- Customer account order timeline UI across lifecycle states (`CREATED` -> `DELIVERED`, with `CANCELLED` handling) in `AccountPage`.
- Shareable wishlist links with authenticated owner controls (`GET/POST/DELETE /api/wishlist/share-link`) and public read endpoint (`GET /api/wishlist/shared/{token}`).
- Full product variants support (`color + size + stock`) with migration `V20__product_variants.sql` and API payload support on create/update product requests.
- Storefront product detail now includes variant picker (`ProductVariantSelector`) with color+talla selection and stock-aware add-to-cart behavior.
- Admin product form now supports dedicated variant management rows (add/edit/remove combinations) and automatic total stock calculation from variants.
- Kafka event infrastructure for domain events (Docker service + backend wiring) with runtime toggle `APP_DOMAIN_EVENTS_KAFKA_ENABLED`.
- `KafkaDomainEventPublisher` as primary `DomainEventPublisher` adapter when Kafka mode is enabled.
- Kafka listeners added for payment, review summary, and notification event consumers.
- Retry + dead-letter strategy for Kafka consumers via `DefaultErrorHandler` and `<topic>.dlt` recoverer.
- `OrderInventorySaga` introduced for payment-driven order/inventory consistency handling.
- New extracted read-oriented microservice `services/product-service` (P6 step 1) with compatible endpoints:
  - `GET /api/products`
  - `GET /api/products/{id}`
  - `GET /api/products/search?q=...`
- New extracted read-oriented microservice `services/inventory-service` (P6 step 2) with compatible endpoints:
  - `GET /api/inventory/products`
  - `GET /api/inventory/products/{id}`
  - `GET /api/inventory/_health`
- Inventory command endpoints in extracted `inventory-service` (P6 step 3):
  - `POST /api/inventory/commands/reserve`
  - `POST /api/inventory/commands/release`
  - `POST /api/inventory/commands/confirm`
- New extracted query-oriented microservice `services/order-service` (P6 step 4) with endpoints:
  - `GET /api/orders`
  - `GET /api/orders/{id}`
  - `GET /api/orders/_health`
- New extracted query-oriented microservice `services/payment-service` (P6 step 5) with endpoints:
  - `GET /api/payments`
  - `GET /api/payments/{id}`
  - `GET /api/payments/order/{orderId}`
  - `GET /api/payments/_health`
- Order command endpoints added to extracted `services/order-service` (P6 step 6):
  - `POST /api/orders`
  - `PATCH /api/orders/{id}/status`
- Observability baseline (P7) with Docker profile `observability`:
  - `prometheus` scraping backend/product-service actuator endpoints
  - `grafana` with provisioned Prometheus datasource and starter dashboard
- Prometheus metric registry enabled in backend and product-service via `micrometer-registry-prometheus`.
- Distributed tracing baseline (P7) with OpenTelemetry:
  - `otel-collector` + `tempo` services (optional `tracing` profile)
  - Grafana Tempo datasource provisioning
  - OTLP tracing bridge dependencies on backend and product-service

### Changed
- Footer informational links now point to real localized routes instead of placeholders.
- Caddy fallback API upstream now uses dynamic DNS discovery (`dynamic a backend 8080`) with round-robin balancing for scaled backend replicas.
- `backend` service no longer uses a fixed container name, enabling `docker compose --scale backend=N`.
- `product-service` now supports `APP_DB_READ_REPLICA_*` env config and routes read-only queries to replica when enabled.
- Backend cache manager now defaults to in-memory cache and switches to Redis when `APP_CACHE_REDIS_ENABLED=true`.
- Category and system-settings write use cases now evict related hot-read caches automatically.
- Notification sender selection is now runtime-configurable from admin settings (`system_settings.notification_provider`) with env fallback for seeded/default state.
- `WHATSAPP_SIMULATED`, `WHATSAPP_TWILIO`, `EMAIL_SENDGRID`, and `EMAIL_SMTP` adapters now resolve provider config from admin settings first, then env fallback.
- Storefront cards now display rating stars and dual price visualization (list price struck-through, discounted sale price, computed discount badge).
- Card-level rating-only submissions are auto-approved server-side so product `avgRating/reviewCount` updates immediately without requiring comment moderation.
- `PaymentGatewayPort` now returns a structured checkout session object.
- Payment domain now supports gateway-driven transitions (`confirmByGateway`, `rejectByGateway`) with safeguards against conflicting final states.
- Security config now explicitly allows unauthenticated POST calls only to `/api/payments/webhooks/gateway`.
- Security config now explicitly permits unauthenticated `GET /api/wishlist/shared/**` for public wishlist sharing.
- Security config now explicitly permits unauthenticated `GET /api/inventory/**` for inventory read endpoints.
- Cart lines now support optional variant metadata while keeping order creation compatibility through canonical `productId` mapping.
- Domain-event mode now supports `spring` (default in-process) and `kafka` (topic-routed) without changing use-case/domain code.
- `PaymentEventListener` now delegates payment outcomes to `OrderInventorySaga` (single orchestration point for order/inventory consistency).
- `infra/docker-compose.yml` now keeps Kafka optional behind `--profile kafka` and introduces `--profile microservices` for `product-service`.
- `infra/docker-compose.yml` now includes optional `--profile observability` for Prometheus/Grafana.
- Default local stack (`backend + frontend + postgres + caddy`) no longer requires pulling/starting Kafka when Kafka mode is disabled.
- Backend actuator exposure now includes `metrics` and `prometheus` under `/api/actuator/*`.
- Kafka profile now uses `apache/kafka:3.8.0` with single-node KRaft settings (fixes broken `bitnami/kafka:3.7` tag).
- Caddy gateway now routes `GET/HEAD /api/products*` directly to `product-service` when microservices profile is enabled; remaining `/api/*` traffic stays on backend.
- Caddy gateway now routes `GET/HEAD /api/inventory*` to `inventory-service` when microservices profile is enabled.
- Backend now exposes inventory read endpoints (`/api/inventory/products*`) using the same product stock source as the extracted service.
- Backend inventory application now supports optional remote command delegation to extracted `inventory-service` using:
  - `APP_INVENTORY_REMOTE_ENABLED=true`
  - `APP_INVENTORY_REMOTE_BASE_URL=http://inventory-service:8082`
- Backend order query use cases (`GetOrderUseCase`, `ListOrdersUseCase`) now support optional delegation to extracted `order-service` using:
  - `APP_ORDER_REMOTE_ENABLED=true`
  - `APP_ORDER_REMOTE_BASE_URL=http://order-service:8083`
- Backend order command use cases (`CreateOrderUseCase`, `UpdateOrderStatusUseCase`) now support optional delegation to extracted `order-service` using:
  - `APP_ORDER_REMOTE_WRITE_ENABLED=true`
  - `APP_ORDER_REMOTE_BASE_URL=http://order-service:8083`
  - `APP_ORDER_REMOTE_SERVICE_TOKEN` for backend->order-service internal auth header (`X-Service-Token`)
- Backend payment query use cases (`GetPaymentUseCase`, `GetPaymentByOrderUseCase`, `ListPaymentsUseCase`) now support optional delegation to extracted `payment-service` using:
  - `APP_PAYMENT_REMOTE_ENABLED=true`
  - `APP_PAYMENT_REMOTE_BASE_URL=http://payment-service:8084`
- `infra/docker-compose.yml` microservices profile now includes `order-service` and `payment-service`, plus backend wiring for order/payment remote env toggles.
- Caddy gateway now routes public `/api/orders*` traffic directly to `order-service`.
- `order-service` now enforces JWT auth/role rules for `/api/orders/**` and supports trusted internal service calls via `X-Service-Token`.
- Caddy gateway now routes `GET/HEAD /api/payments*` traffic to `payment-service`; non-read payment endpoints remain on backend.
- `payment-service` now enforces JWT auth/role rules for payment queries and supports trusted internal calls via `X-Service-Token`.
- Backend payment query remote client now supports `APP_PAYMENT_REMOTE_SERVICE_TOKEN` for backend->payment-service auth.
- Caddy gateway now enforces baseline API policies:
  - `/api/*` request body max size `12MB`
  - unsupported API methods rejected with `405`
  - `/api/orders*` restricted to `GET|HEAD|POST|PATCH`
  - upstream timeout guardrails for extracted services and backend
- Backend now applies per-IP gateway-facing rate limits for sensitive public POST endpoints:
  - `/api/auth/login`
  - `/api/auth/register`
  - `/api/payments/webhooks/gateway` and `/api/payments/webhooks/gateway/mercadopago`
- Backend and product-service runtime config now supports `APP_TRACING_ENABLED`, `APP_TRACING_OTLP_ENDPOINT`, and `APP_TRACING_SAMPLING_PROBABILITY`.
- Storefront checkout now allows selecting payment method (`BANK_TRANSFER` or `PAYMENT_GATEWAY`) and applies employee discount visualization for `SELLER` users.
- Account profile screen now supports inline profile editing and password change workflow.
- API error handling in frontend now surfaces backend `detail/message` when available (including media-proof upload failures).
- Payment gateway provider is now switchable via env (`PAYMENT_GATEWAY_PROVIDER=STUB|MERCADO_PAGO`) and includes Mercado Pago adapter support.
- Account orders now include `Ir a pagar` action to open live gateway checkout sessions when available.
- Stub checkout default URL now points to a real storefront route (`/es/account?tab=orders`) and appends `ref` safely.
- Admin product table now supports category filtering in addition to condition and brand filters.
- Admin user management now uses server-side pagination per tab (`clientes`/`trabajadores`) with status filter (`todos`/`habilitados`/`bloqueados`).
- Admin users screen now includes explicit guidance to edit the current admin profile/password through `Mi cuenta`.
- Cart dark-mode typography was adjusted for stronger contrast in secondary labels/actions.
- Storefront navbar logo transition now avoids scroll oscillation loops near the compact/full threshold.
- Notification sender selection is now env-driven (`NOTIFICATION_PROVIDER=LOG|WHATSAPP_SIMULATED`).
- `PaymentNotificationListener` now resolves the order customer contact from repositories instead of hardcoded `unknown` when available.
- Notification provider selection now supports `NOTIFICATION_PROVIDER=LOG|WHATSAPP_SIMULATED|WHATSAPP_TWILIO`.
- Notification provider selection now supports `NOTIFICATION_PROVIDER=LOG|WHATSAPP_SIMULATED|WHATSAPP_TWILIO|EMAIL_SENDGRID`.
- Notification provider selection now supports `NOTIFICATION_PROVIDER=LOG|WHATSAPP_SIMULATED|WHATSAPP_TWILIO|EMAIL_SENDGRID|EMAIL_SMTP`.
- Frontend Docker/env config now supports `PUBLIC_WHATSAPP_PHONE`, `PUBLIC_WHATSAPP_MESSAGE_ES`, and `PUBLIC_WHATSAPP_MESSAGE_EN`.
- Storefront footer social links and floating WhatsApp button now resolve from backend-managed system settings (with safe fallback defaults).
- Notification listeners now prioritize customer phone (when available) before email for outbound WhatsApp destination contact resolution.
- Notification listeners now publish structured recipients (`phone` + `email`) so channel-specific providers can choose correct destination safely.
- `EMAIL_SENDGRID` can use explicit SendGrid env vars or fallback to admin SMTP credentials (`smtpPassword` decrypted server-side and `smtpFromEmail`).
- `EMAIL_SMTP` supports env overrides (`EMAIL_SMTP_*`) and fallback to admin SMTP settings (`smtpHost`, `smtpPort`, `smtpUsername`, encrypted password, `smtpFromEmail`).
- System settings load no longer fails when a legacy/corrupted encrypted SMTP password exists; admin can now open the page and replace/clear credentials from UI.

### Verified
- Frontend build passes after adding new informational pages and footer links (`npm run build`).
- Docker Compose config validates after backend scaling changes (`docker compose -f infra/docker-compose.yml --env-file infra/.env config`).
- Caddy config validates after dynamic backend upstream change (`caddy validate --config /etc/caddy/Caddyfile`).
- `product-service` compiles with read-replica routing configuration (`mvn -DskipTests compile` in `services/product-service`).
- Backend compiles with Redis cache integration (`mvn -DskipTests compile`).
- Backend test suite passes (`mvn test`) with 51 tests, including new gateway webhook and domain transition coverage.
- Backend test suite passes (`mvn test`) with 53 tests after notification provider refactor.
- Frontend build passes (`npm run build`).
- Docker stack rebuild for `backend` and `frontend` completed successfully.
- Docker rebuild validated Flyway migration `V17` and healthy startup for backend/frontend services.
- Backend tests pass after saga/listener refactor (`mvn clean test`, 54 tests).
- New `product-service` builds successfully (`mvn -DskipTests compile`) and runs healthy in Docker profile `microservices`.
- Default Docker stack rebuild (`backend` + `frontend`) succeeds without Kafka profile enabled.
- `product-service` smoke check succeeded for `GET /api/products?page=0&size=1`.
- Caddy routing smoke checks now pass for extracted catalog reads:
  - `GET /api/products/_health` -> `204`
  - `GET /api/products?page=0&size=1` -> `200`
- New `inventory-service` builds successfully (`mvn -DskipTests compile`) and runs healthy in Docker profile `microservices`.
- Inventory routing smoke checks pass through Caddy:
  - `GET /api/inventory/_health` -> `204`
  - `GET /api/inventory/products?page=0&size=1` -> `200`
- Inventory command endpoints validate in extracted service:
  - `POST /api/inventory/commands/reserve` -> `204`
  - `POST /api/inventory/commands/release` -> `204`
- New `order-service` builds successfully (`mvn -DskipTests compile`) and runs healthy in Docker profile `microservices`.
- Order-service smoke checks pass:
  - `GET /api/orders/_health` -> `204`
  - `GET /api/orders?page=0&size=1` -> `200`
- Backend delegation smoke checks pass with `APP_ORDER_REMOTE_ENABLED=true`:
  - `GET /api/orders/mine?page=0&size=1` -> `200`
  - `GET /api/orders?page=0&size=1` -> `200`
- Backend delegation smoke checks pass with `APP_ORDER_REMOTE_WRITE_ENABLED=true`:
  - `POST /api/orders` -> `201`
  - `PATCH /api/orders/{id}/status` -> `200`
  - Follow-up payment registration still works (order event published in backend after delegated create)
- New `payment-service` builds successfully (`mvn -DskipTests compile`) and runs healthy in Docker profile `microservices`.
- Payment-service smoke checks pass:
  - `GET /api/payments/_health` -> `204`
  - `GET /api/payments?page=0&size=1` -> `200`
- Backend delegation smoke checks pass with `APP_PAYMENT_REMOTE_ENABLED=true`:
  - `GET /api/payments?page=0&size=1` -> `200`
  - `GET /api/payments/order/{orderId}` -> `200`
- Gateway payment read offload smoke checks pass:
  - `GET /api/payments/_health` -> `204`
  - `GET /api/payments?page=0&size=1` as admin -> `200`
  - `GET /api/payments?page=0&size=1` as customer -> `403`
- Gateway policy smoke checks pass:
  - unsupported methods on API paths are rejected at edge (`400/405` depending on HTTP verb parsing stage)
  - `DELETE /api/orders` -> `405`
  - oversized payload `POST /api/auth/login` (~13MB JSON) -> `413`
  - repeated `POST /api/auth/login` attempts exceed threshold and return `429`

## [2026-04-20] - Customer proof submission and admin payment queue alignment

### Added
- Customer-facing payment resolution endpoint: `GET /api/payments/order/{orderId}`.
- Customer proof media upload endpoint: `POST /api/media/upload-proof`.
- Account page proof workflow: upload image or provide proof URL, then submit via `/api/payments/{id}/proof`.

### Changed
- `PATCH /api/payments/{id}/proof` now enforces customer ownership checks for `CUSTOMER` role.
- Admin payment review queue now includes `PENDING` entries for visibility while keeping actions only for reviewable statuses.
- Frontend API client now includes:
  - `uploadPaymentProofImage`
  - `getPaymentByOrder`
  - `submitPaymentProof`

### Verified
- Frontend build passes (`npm run build`).
- Backend authorization integration tests pass (`mvn -Dtest=AuthorizationGuardsIT test`).
- End-to-end API validation confirmed `PENDING -> SUBMITTED` transition and admin queue visibility.

## [2026-04-20] - Backend-managed product media storage

### Changed
- Added backend static media delivery via `GET /api/media/**` mapped to filesystem storage.
- Added `MEDIA_STORAGE_PATH` runtime configuration and Docker bind mount (`infra/storage/media:/app/media`) for persistence.
- Migrated seeded catalog products to backend-local image paths through `V11__product_images_from_backend_media.sql`.
- Updated frontend fallback products and admin product form defaults to use backend media routes instead of external image URLs.
- Added sample product images (`product-001.jpg` to `product-010.jpg`) under `infra/storage/media/products/`.

### Verified
- Docker stack rebuilt and backend health endpoint returned `200`.
- `GET /api/products` includes `/api/media/products/...` paths.
- `GET /api/media/products/product-001.jpg` returned `200`.

## [2026-04-20] - Storefront mobile nav and UTF-8 text normalization

### Fixed
- Made storefront category navigation rail fully scrollable on mobile in `Navbar`.
- Added mobile category slider controls (`‹` / `›`) with smooth step scrolling and auto-hide at bounds.
- Improved slider control visibility logic to avoid initial double-button flash and hide/show based on real first/last item position.
- Reworked mobile header layout so search/wishlist/cart actions do not overlap the logo on initial view.
- Normalized Spanish storefront/wishlist copy with proper accents (removed mojibake), including labels like:
  - `Colección`
  - `Categorías`
  - `Condición`
  - `Paginación`
  - `Tu lista de favoritos está vacía`
- Replaced broken breadcrumb separator glyphs in category pages.

### Verified
- Frontend build passes (`npm run build`).
- Docker frontend image rebuilt and container restarted successfully (`docker compose ... up -d --build frontend`).

## [2026-04-20] - Admin UI/UX and mobile responsiveness refresh

### Changed
- Admin products now supports `Grilla` and `Cards` view modes with local persistence.
- Added mobile-first rendering for admin data tables (`DataTable`) using card layout under small breakpoints.
- Added mobile admin navigation drawer in `AdminLayout` + `AdminSidebar` (menu button, overlay, close behavior).
- Refined admin action controls with icon-based buttons in product management and form flows.
- Darkened light-mode visual tokens for storefront and admin to improve contrast while keeping the brand style.
- Updated storefront/wishlist product grids to render as single-column first on small screens.

## [2026-04-20] - Documentation synchronization

### Changed
- Synchronized roadmap with implemented code and marked wishlist/search/size-stock foundation as completed items.
- Updated authentication documentation to match current `SecurityConfig` and controller-level `@PreAuthorize` rules.
- Updated payment flow documentation to match active endpoints:
  - `PATCH /api/payments/{id}/proof`
  - `PATCH /api/payments/{id}/review` with `APPROVE` and `REJECT` actions
- Updated domain events documentation to match real payloads and current subscribers/listeners.
- Updated architecture documentation to reflect current module map and migrations `V1` to `V10`.
- Updated backend and frontend READMEs to reflect implemented modules/features and current runtime setup.
- Fixed deployment runbook command from `git pull origin main` to `git pull origin master`.

## [2026-04-19] - Initial platform baseline

### Added
- Monorepo foundation:
  - `backend/` (Spring Boot 3.3, Java 17, hexagonal modules)
  - `frontend/` (Astro 4 SSR, React islands, Tailwind)
  - `infra/` (Docker Compose + Caddy)
  - `docs/` (architecture, auth, events, payments, deployment, roadmap)
- Core backend domains and APIs:
  - auth (JWT register/login/refresh/me)
  - product and category management
  - orders and semi-manual payments
  - reviews and moderation
  - discounts and customer credits
  - inventory reservation/release flow
  - notification listener scaffolding
- Frontend storefront and admin surfaces:
  - localized storefront routes (`/es`, `/en`)
  - account/auth screens
  - admin dashboard and management screens
  - cart and review interfaces
- Database migrations from `V1` to `V10`, including:
  - taxonomy/reviews/auth seeds
  - search indexes (`pg_trgm`)
  - product size stock tables
  - wishlist schema
  - Chile defaults (`CLP`, shipping normalization)
