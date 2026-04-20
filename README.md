# Pilar Estilo

> Lujo con Propósito — Luxury with Purpose

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

## Documentation

- [Architecture](docs/architecture.md)
- [Domain Events](docs/domain-events.md)
- [Payment Flow](docs/payment-flow.md)
- [Deployment](docs/deployment.md)
- [Roadmap](docs/roadmap.md)
- [Frontend README](frontend/README.md)
- [Backend README](backend/README.md)
