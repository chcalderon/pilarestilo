# Pilar Estilo

> Lujo con Proposito - Luxury with Purpose

Luxury online boutique for curated new and second-hand branded clothing.

## Stack

| Layer | Tech |
|---|---|
| Frontend | Astro 4 + React islands + Tailwind CSS |
| Backend | Java 17 + Spring Boot 3.3 + Hexagonal Architecture |
| Database | PostgreSQL 16 |
| Reverse Proxy | Caddy (auto-TLS) |
| Container | Docker Compose |

## Quick Start (local)

```bash
cp infra/.env.example infra/.env
# fill in infra/.env
docker compose -f infra/docker-compose.yml --env-file infra/.env up --build
```

Open http://localhost

Optional compose profiles:

```bash
# Kafka broker (for APP_DOMAIN_EVENTS_KAFKA_ENABLED=true)
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile kafka up -d

# Extracted read services (P6 steps)
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile microservices up -d --build product-service inventory-service order-service payment-service
# Optional inventory write delegation (P6 step 3):
# APP_INVENTORY_REMOTE_ENABLED=true
# APP_INVENTORY_REMOTE_BASE_URL=http://inventory-service:8082
# Optional order read delegation (P6 step 4):
# APP_ORDER_REMOTE_ENABLED=true
# Optional order write delegation (P6 step 6):
# APP_ORDER_REMOTE_WRITE_ENABLED=true
# APP_ORDER_REMOTE_BASE_URL=http://order-service:8083
# Optional payment read delegation (P6 step 5):
# APP_PAYMENT_REMOTE_ENABLED=true
# APP_PAYMENT_REMOTE_BASE_URL=http://payment-service:8084
# APP_PAYMENT_REMOTE_SERVICE_TOKEN=payment-service-internal-token

# Prometheus + Grafana observability stack (P7 baseline)
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile observability up -d

# Distributed tracing stack (P7)
docker compose -f infra/docker-compose.yml --env-file infra/.env --profile tracing up -d
```

## Latest updates (April 2026)

- Product cards now support dual pricing (`listPrice` struck-through + discounted sale price) across home, category, listing, wishlist, and search contexts.
- Existing catalog rows are backfilled with default `listPrice` values via DB migration so discount visuals render immediately in storefront cards.
- Logged-in customers can now leave quick star-only ratings directly from product cards (no comment required in quick flow).
- Admin product management now supports `Grilla` and `Cards` modes with responsive/mobile improvements.
- Storefront mobile header now keeps action icons clear of the logo through a dedicated small-screen layout.
- Storefront category navigation is now a mobile slider with `<` / `>` controls and smooth step scrolling.
- Spanish storefront and wishlist UI copy was normalized to UTF-8 (accent/mojibake fixes).
- Product images now resolve through backend media routes (`/api/media/**`) backed by persisted Docker storage in `infra/storage/media`.
- Customer account now shows an order tracking timeline with visual status progression for each order.
- Wishlist now supports shareable public links generated from customer favorites (`/[locale]/wishlist/shared/{token}`).
- Catalog now supports full product variants (`color + talla + stock`) with dedicated admin UX and storefront variant picker on product detail.
- P5 started: optional Kafka-backed domain events are now available (`APP_DOMAIN_EVENTS_KAFKA_ENABLED=true`) with retry/DLQ and saga orchestration for order/payment/inventory consistency.
- P6 step 1 completed: extracted `services/product-service` now provides read-only compatible product endpoints (`GET /api/products*`) in optional `microservices` profile.
- Caddy now routes `GET/HEAD /api/products*` to `product-service` when `microservices` profile is enabled; remaining API routes stay on backend.
- P6 step 2 completed: extracted `services/inventory-service` now provides read-only inventory endpoints (`GET /api/inventory*`) and Caddy routes those reads to the service in `microservices` profile.
- P6 step 3 completed: backend inventory writes (`reserve/release/confirm`) can now be delegated to `inventory-service` through `APP_INVENTORY_REMOTE_ENABLED=true`.
- P6 step 4 completed: extracted `services/order-service` now provides order query endpoints, and backend can delegate order reads through `APP_ORDER_REMOTE_ENABLED=true`.
- P6 step 5 completed: extracted `services/payment-service` now provides payment query endpoints, and backend can delegate payment reads through `APP_PAYMENT_REMOTE_ENABLED=true`.
- Caddy now routes `GET/HEAD /api/payments*` traffic directly to `payment-service` with JWT auth offloaded there.
- P6 step 6 completed: extracted `services/order-service` now supports order command endpoints, and backend can delegate order create/status updates through `APP_ORDER_REMOTE_WRITE_ENABLED=true`.
- Caddy now routes public `/api/orders*` traffic directly to `order-service` (JWT auth offloaded there).
- Gateway guardrails now include API body-size/method policies at Caddy plus per-IP rate limits for sensitive public POST endpoints in backend (`login/register/payment webhooks`).
- P7 baseline observability is available through optional `observability` profile (`prometheus` + `grafana`) with preprovisioned dashboard.
- P7 tracing baseline is now available through optional `tracing` profile (`otel-collector` + `tempo`) and Grafana Tempo datasource provisioning.
- Customer account now supports bank-transfer proof submission (image upload or manual URL) directly from `My orders`.
- Admin payment queue now includes `PENDING` rows for visibility and keeps review actions only for reviewable statuses.
- Payment module now exposes gateway-ready endpoints for checkout session creation and webhook ingestion (`/api/payments/{id}/gateway/checkout`, `/api/payments/webhooks/gateway`), currently backed by stub adapter.
- Payment gateway provider is now configurable by env (`PAYMENT_GATEWAY_PROVIDER=STUB|MERCADO_PAGO`) with simulation fallback kept in UI/admin flows.
- Checkout now allows selecting `Transferencia` or `Pasarela (simulada)` so payment flows can be tested before production gateway onboarding.
- Account area now includes profile self-service (`nombre`) and password change actions.
- Admin now includes user management (`/admin/users`) for customers/workers, including role, status, password reset, deletion, and credit assignment.
- Admin products now include category filtering alongside condition/brand filters.
- Admin users now load with server-side pagination per tab plus status filter (`todos`, `habilitados`, `bloqueados`).
- Cart dark mode readability was improved in summary/actions text.
- Navbar logo scroll behavior was stabilized to prevent compact/full logo flicker loops on slight scroll.
- Storefront now includes a floating WhatsApp CTA button (configurable phone/message per locale via env).
- Admin now includes `Configuracion del sistema` (`/admin/settings`) to manage storefront channels and runtime notification provider selection (`LOG`, WhatsApp simulated/Twilio, SendGrid, SMTP).
- Storefront WhatsApp button + footer social links now consume backend system settings (`/api/system-settings/public`).
- Storefront contact page (`/{locale}/contact`) now consumes public admin settings for WhatsApp, support email, and social links.
- Customer profile now supports WhatsApp phone capture (`/api/auth/me/profile`) and notifications prioritize that phone as destination contact.
- Backend notifications now support runtime provider switching from admin settings without restart, with env variables kept as fallback.
- Twilio auth token, SendGrid API key, and SMTP password are now encrypted at rest in `system_settings`.
- Footer informational section now links to dedicated storefront pages: `Sobre Pilar Estilo`, `Cómo vendemos`, `Envíos y devoluciones`, and `Contacto`.
- Added new localized storefront routes: `/{locale}/about`, `/{locale}/how-we-sell`, `/{locale}/shipping-returns`, and `/{locale}/contact`.

## Documentation

- [Architecture](docs/architecture.md)
- [Domain Events](docs/domain-events.md)
- [Payment Flow](docs/payment-flow.md)
- [Deployment](docs/deployment.md)
- [GitHub Actions VPS Deploy](docs/github-actions-vps.md)
- [Roadmap](docs/roadmap.md)
- [Changelog](CHANGELOG.md)
- [Frontend README](frontend/README.md)
- [Backend README](backend/README.md)
