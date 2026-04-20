# Pilar Estilo - Roadmap

This roadmap is synced with the current codebase on `master` as of April 20, 2026.

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
- [x] Admin user management hub (`/admin/users`) with role/status/password/customer-credit controls

---

## v2.1 - Catalog and Commerce Refinements (Completed)

- [x] Wishlist backend module (`/api/wishlist`) and storefront page (`/[locale]/wishlist`)
- [x] Product search endpoint (`GET /api/products/search?q=...`) + search overlay UI
- [x] Per-size stock data model foundation (`product_size_stocks`, `sizeStocks` in product DTO)
- [x] Chile-first defaults migration (`CLP` currency defaults + shipping origin normalization)
- [x] Extended catalog DB migrations (`V7` to `V11`)
- [x] Storefront mobile header/navigation polish: action-icon reflow + category slider controls + UTF-8 Spanish copy normalization
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

- [ ] Payment gateway adapter (Mercado Pago or Stripe) via `PaymentGatewayPort`
- [x] Gateway webhook receiver (`POST /api/payments/webhooks/gateway`) with optional signature guard
- [x] Gateway checkout session endpoint for `PAYMENT_GATEWAY` payments (`POST /api/payments/{id}/gateway/checkout`) using current stub adapter
- [x] Temporary gateway simulation controls in storefront/admin while production provider onboarding is pending
- [ ] WhatsApp notifications (Twilio/Meta Cloud API)
- [ ] Email notifications (SendGrid)
- [x] Customer order history and receipts in account area (`GET /api/orders/mine` + `AccountPage` orders tab)
- [x] Customer payment-proof self-service in account area (`GET /api/payments/order/{orderId}` + submit proof from `AccountPage`)
- [x] Admin payment queue visibility for `PENDING` + review actions restricted to actionable statuses

---

## P4 - Catalog and Buying Experience

- [x] Wishlist core (persisted favorites per authenticated user)
- [x] Search core (keyword search API + storefront overlay)
- [ ] Shareable wishlist links
- [ ] Full product variants (size/color combinations with dedicated admin UX)
- [ ] Direct media upload to S3/R2 from admin product form
- [ ] Order tracking timeline in customer account

---

## P5 - Event-Driven Upgrade

- [ ] Kafka infrastructure
- [ ] `KafkaDomainEventPublisher` as primary adapter
- [ ] Migrate in-process listeners to `@KafkaListener`
- [ ] Retry/DLQ strategy
- [ ] Sagas for order-inventory consistency

---

## P6 - Microservices Extraction

- [ ] Extract `product` service
- [ ] Extract `inventory` service
- [ ] Extract `order` service
- [ ] Extract `payment` service
- [ ] Introduce API gateway policies (routing, auth offload, rate limits)

---

## P7 - Observability and Scale

- [ ] Prometheus scrape endpoint and metrics pipeline
- [ ] Grafana dashboards (JVM, HTTP, DB, order/payment funnel)
- [ ] Distributed tracing (OpenTelemetry)
- [ ] Redis for cart/session acceleration
- [ ] Horizontal backend scaling behind reverse proxy
- [ ] Postgres read replicas for read-heavy catalog queries
