# Pilar Estilo

> Lujo con Proposito - Luxury with Purpose

Luxury online boutique for curated new and second-hand branded clothing.

## Stack

| Layer | Tech |
|---|---|
| Frontend | Astro 7 (SSR) + React islands + Tailwind CSS |
| Backend | Java 25 + Spring Boot 4.1 + Hexagonal Architecture (21 domain modules) |
| Database | PostgreSQL 16 (Flyway migrations) |
| Messaging | Kafka (optional, domain events) |
| Cache | Redis (optional, hot-read cache) |
| Reverse Proxy | Caddy (auto-TLS) |
| Container | Docker Compose |

The backend is a monolith by default. Four read paths (`product`, `inventory`, `order`,
`payment`) can be extracted into standalone microservices behind Caddy via the `microservices`
compose profile — see [Architecture](docs/architecture.md).

## Quick Start (local)

```bash
cp infra/.env.example infra/.env
# fill in infra/.env
bash scripts/deploy/local_deploy.sh up
# or PowerShell:
# ./scripts/deploy/local_deploy.ps1 up
```

Open http://localhost

Rebuild completo local:

```bash
bash scripts/deploy/local_rebuild.sh
# or PowerShell:
# ./scripts/deploy/local_rebuild.ps1
```

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

## Current state

The storefront (`/es`, `/en`) and full admin panel (`/admin`) are live; the business itself has
been selling daily through social media and the admin panel is used for real inventory, users and
stock. The online checkout has not launched to customers yet, so order/payment data in a fresh
environment is disposable test data until launch day.

Backend domain modules: `product`, `category`, `inventory`, `order`, `payment`, `discount`,
`review`, `wishlist`, `customercredit`, `notification`, `systemsettings`, `productai`,
`cashregister`, `dispatch`, `dashboard`, `user`, `navigation`, `location`, `publication`,
`customeraddress`, `billing`, `returns` — plus `shared` (auth, RBAC, Kafka, common domain).
Database is at Flyway migration V86; see [Architecture](docs/architecture.md) for the module
layout and conventions, and `git log` for recent history (`CHANGELOG.md` predates most of it).

## Documentation

- [Architecture](docs/architecture.md)
- [AI Product Pipeline Integration (PR1 Design)](docs/ai-product-pipeline-integration.md)
- [AI Product Pipeline Progress](docs/ai-product-pipeline-progress.md)
- [Domain Events](docs/domain-events.md)
- [Payment Flow](docs/payment-flow.md)
- [Deployment](docs/deployment.md)
- [Session Memory](docs/session-memory.md)
- [GitHub Actions VPS Deploy](docs/github-actions-vps.md)
- [Documentation Source Diff (2026-05-09)](docs/documentation-source-diff-2026-05-09.md)
- [Roadmap](docs/roadmap.md)
- [Changelog](CHANGELOG.md)
- [Frontend README](frontend/README.md)
- [Backend README](backend/README.md)
