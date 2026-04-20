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

## Latest updates (April 2026)

- Admin product management now supports `Grilla` and `Cards` modes with responsive/mobile improvements.
- Storefront mobile header now keeps action icons clear of the logo through a dedicated small-screen layout.
- Storefront category navigation is now a mobile slider with `<` / `>` controls and smooth step scrolling.
- Spanish storefront and wishlist UI copy was normalized to UTF-8 (accent/mojibake fixes).
- Product images now resolve through backend media routes (`/api/media/**`) backed by persisted Docker storage in `infra/storage/media`.
- Customer account now supports bank-transfer proof submission (image upload or manual URL) directly from `My orders`.
- Admin payment queue now includes `PENDING` rows for visibility and keeps review actions only for reviewable statuses.
- Payment module now exposes gateway-ready endpoints for checkout session creation and webhook ingestion (`/api/payments/{id}/gateway/checkout`, `/api/payments/webhooks/gateway`), currently backed by stub adapter.
- Checkout now allows selecting `Transferencia` or `Pasarela (simulada)` so payment flows can be tested before production gateway onboarding.
- Account area now includes profile self-service (`nombre`) and password change actions.
- Admin now includes user management (`/admin/users`) for customers/workers, including role, status, password reset, deletion, and credit assignment.

## Documentation

- [Architecture](docs/architecture.md)
- [Domain Events](docs/domain-events.md)
- [Payment Flow](docs/payment-flow.md)
- [Deployment](docs/deployment.md)
- [Roadmap](docs/roadmap.md)
- [Changelog](CHANGELOG.md)
- [Frontend README](frontend/README.md)
- [Backend README](backend/README.md)
