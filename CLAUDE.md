# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Backend (Java 25 + Spring Boot 3.5)

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

```bash
# Minimal stack (postgres + backend + frontend + caddy) — runs at http://localhost
cd infra && docker compose --env-file .env up -d --build

# Add optional profiles
cd infra && docker compose --env-file .env --profile kafka up -d
cd infra && docker compose --env-file .env --profile cache up -d
cd infra && docker compose --env-file .env --profile microservices up -d --build
cd infra && docker compose --env-file .env --profile observability up -d
cd infra && docker compose --env-file .env --profile tracing up -d
```

Seed admin credentials: `admin@pilarestilo.com` / `admin2026`

---

## Architecture

### Monorepo layout

```
backend/          Spring Boot monolith (hexagonal, 16 modules)
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

Modules: `product`, `category`, `inventory`, `order`, `payment`, `discount`, `review`, `wishlist`, `customercredit`, `notification`, `systemsettings`, `productai`, `cashregister`, `dispatch`, `dashboard`, `user` + `shared` (auth, rbac, kafka, common domain).

`cashregister` also exposes `POST /api/pos/sales` (stub, returns 501) — planned Windows POS integration; see `docs/pos-channel.md`.

### Frontend — Astro islands

Routes split by concern:
- **Storefront**: `/es/*` and `/en/*` — localized, SSR Astro pages
- **Admin**: `/admin/*` — guarded by `src/middleware.ts` (checks JWT `role === ADMIN`)
- Root `/` redirects to `/es/`

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

### Domain events

`DomainEventPublisher` port has two adapters selected by env:
- **Default**: in-process Spring `ApplicationEventPublisher`
- **Kafka** (`APP_DOMAIN_EVENTS_KAFKA_ENABLED=true`): `KafkaDomainEventPublisher`

Key flows: `OrderCreated` → payment registration; `PaymentConfirmed` → order paid; `PaymentRejected` → order cancelled + inventory compensation (`OrderInventorySaga`).

### Database migrations

Flyway manages all schema changes. Migrations live in `backend/src/main/resources/db/migration/`. Current highest: **V53**. Never edit an already-applied migration — always add a new `V{n+1}__description.sql`.

### Tailwind dark mode

`darkMode: ['selector', '[data-theme="dark"]']` — both `AdminLayout.astro` and `BaseLayout.astro` set `data-theme` on `<html>`. The `dark:` prefix responds to that attribute, not OS preference.

### Key env vars

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | HS256 signing key (min 32 bytes) |
| `NOTIFICATION_PROVIDER` | LOG \| WHATSAPP_SIMULATED \| WHATSAPP_TWILIO \| EMAIL_SENDGRID \| EMAIL_SMTP \| N8N_WEBHOOK |
| `PAYMENT_GATEWAY_PROVIDER` | STUB \| MERCADO_PAGO |
| `APP_DOMAIN_EVENTS_KAFKA_ENABLED` | Enables Kafka transport |
| `APP_PRODUCT_AI_ENABLED` | Enables AI pipeline |
| `APP_PRODUCT_AI_ENGINE` | `stub` \| `openai_backend` |
| `APP_PRODUCT_AI_OPENAI_INFER_MODEL` | Text model (default: `gpt-4.1-mini`) |
| `APP_PRODUCT_AI_OPENAI_IMAGE_MODEL` | Image model (default: `gpt-image-1`) |
| `APP_INVENTORY_REMOTE_ENABLED` | Delegate inventory write commands (`reserve/release/confirm`) to microservice |
| `APP_ORDER_REMOTE_ENABLED` | Delegate order reads to microservice |
| `APP_PAYMENT_REMOTE_ENABLED` | Delegate payment reads to microservice |
| `APP_CACHE_REDIS_ENABLED` | Redis hot-read cache for categories + settings |
| `APP_TRACING_ENABLED` | OTLP distributed tracing to Tempo |

Copy `infra/.env.example` → `infra/.env` before first run.
