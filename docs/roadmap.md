# Pilar Estilo — Roadmap

## v1 — Initial Release (Completed)

- [x] Product catalog with full CRUD (create, read, update, archive)
- [x] Order management with state machine (DRAFT → AWAITING_PAYMENT → PAID → PROCESSING → SHIPPED → DELIVERED → CANCELLED)
- [x] Semi-manual payment flow (bank transfer, cash on delivery, WhatsApp agreement, store credit)
- [x] Discount and promo code system (fixed-amount and percentage codes, expiry dates, usage limits)
- [x] Customer credit system (grant credits, apply to orders, track balance history)
- [x] Inventory management — synchronous stock tracking with reservation on order creation
- [x] Bilingual storefront (Spanish and English via Astro i18n routing)
- [x] Minimal admin UI (product, order, payment, and inventory management pages)
- [x] Dockerized deployment with Caddy reverse proxy (automatic TLS via Let's Encrypt)
- [x] Hexagonal architecture with domain event seams in place
- [x] Flyway database migrations + seed data

---

## v2 — Editorial Redesign, Categories, Ratings & Real Auth (Completed)

- [x] JWT authentication — access tokens (24h) + refresh tokens (7d), BCrypt password hashing
- [x] Role-based access control — `ADMIN`, `SELLER`, `CUSTOMER` roles with `@PreAuthorize` guards
- [x] Admin login page — real JWT-based login form, server-side SSR middleware guard
- [x] Token refresh endpoint + `GET /api/auth/me`
- [x] Category taxonomy — tree structure (parent + children), product-category join table
- [x] Category filter on product listing (`?category=<slug>`)
- [x] Review module — 1–5 star ratings, approval workflow, `ReviewSummaryListener` denormalization
- [x] `avg_rating` + `review_count` denormalized on `products` for fast listing queries
- [x] Editorial luxury storefront redesign — Cormorant Garamond + Pinyon Script + Montserrat typography
- [x] New brand palette — rose gold (`pe-rose`), off-white (`pe-offwhite`), cream (`pe-cream`)
- [x] Falabella-style admin panel — `AdminLayout`, `DataTable`, sidebar nav, `CategoryTree`, `ReviewModerationQueue`
- [x] Auth pages — `/[locale]/auth/login`, `/[locale]/auth/register`, `/[locale]/account`
- [x] Full i18n coverage — all v2 keys in `es.json` + `en.json`

---

## Baseline Considerations (Plan Guardrails)

These are not treated as bug fixes. They are baseline decisions the team should preserve in upcoming phases.

- [x] Chile-first commerce baseline - prices, labels, and checkout copy aligned to CLP and Chilean operation.
- [x] Brand consistency on landing - single primary logo placement in header, with readable size and slogan visibility.
- [x] Theme consistency - light/dark mode must apply site-wide (not header-only) for storefront and admin surfaces.
- [x] Admin routing stability - keep Astro i18n routing compatible with `/admin/*` routes and `/admin/login` access.
- [x] Authenticated admin mutations - product/category/payment/review write actions must always send Bearer token.
- [x] Category-product integrity - maintain assignable category flow in admin product form and visible category listings.
- [x] Infra runbook canonical path - local startup and verification via `infra/docker-compose.yml` + `infra/.env`.

---

## P3 — Payment & Notifications

- [ ] Payment gateway integration — Mercado Pago adapter via existing `PaymentGatewayPort` seam
- [ ] WhatsApp notifications via Twilio or Meta Cloud API — order confirmation, payment status, shipping updates
- [ ] Email notifications via SendGrid — receipts, rejection notices, shipping confirmations
- [ ] Webhook receiver for gateway payment events → `PaymentConfirmed` / `PaymentRejected`
- [ ] Customer self-service portal — order history, shipment tracking, downloadable receipts

---

## P4 — Catalog Enhancements

- [ ] Wishlist / favorites — heart icon persisted per user, shareable links
- [ ] Order tracking timeline — visual state machine display on account page
- [ ] Image upload to S3 / Cloudflare R2 — replace URL input in admin product form with direct upload
- [ ] Full-text product search — Postgres `tsvector` or Meilisearch adapter
- [ ] Product variants — size/color combinations with per-variant stock

---

## P5 — Event-Driven Architecture

The `DomainEventPublisher` port is defined with a Kafka-ready swap path (see `docs/domain-events.md`).

- [ ] Kafka cluster setup (single-broker for VPS, or Confluent Cloud for managed)
- [ ] `KafkaDomainEventPublisher` adapter — replaces `SpringDomainEventPublisher` as `@Primary` bean
- [ ] Update all `@EventListener` subscribers to `@KafkaListener` with consumer groups
- [ ] Saga orchestration for order/inventory consistency (replace synchronous reservation)
- [ ] Dead-letter topic handling — failed event processing with retry and alerting

---

## P6 — Microservices

Package boundaries already designed for extraction. Recommended order:

- [ ] Extract `product` service — own Spring Boot app, own PostgreSQL schema
- [ ] Extract `inventory` service — consumes `OrderCreated` from Kafka
- [ ] Extract `order` service
- [ ] Extract `payment` service
- [ ] API Gateway — Kong or extended Caddy for routing, rate limiting, auth offloading

---

## P7 — Observability & Scale

- [ ] Prometheus metrics scraping — enable `/actuator/prometheus`
- [ ] Grafana dashboards — JVM heap, HTTP latency, DB pool, order/payment funnel
- [ ] Distributed tracing — OpenTelemetry → Jaeger or Grafana Tempo
- [ ] CDN for product images — Cloudflare in front of R2 bucket
- [ ] Redis for cart and session — server-rendered cart totals, cross-device sync
- [ ] Horizontal scaling — stateless backend behind Caddy upstream pool
- [ ] Read replicas — PostgreSQL streaming replica for catalog read queries
