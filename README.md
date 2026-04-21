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

- Product cards now support dual pricing (`listPrice` struck-through + discounted sale price) across home, category, listing, wishlist, and search contexts.
- Existing catalog rows are backfilled with default `listPrice` values via DB migration so discount visuals render immediately in storefront cards.
- Logged-in customers can now leave quick star-only ratings directly from product cards (no comment required in quick flow).
- Admin product management now supports `Grilla` and `Cards` modes with responsive/mobile improvements.
- Storefront mobile header now keeps action icons clear of the logo through a dedicated small-screen layout.
- Storefront category navigation is now a mobile slider with `<` / `>` controls and smooth step scrolling.
- Spanish storefront and wishlist UI copy was normalized to UTF-8 (accent/mojibake fixes).
- Product images now resolve through backend media routes (`/api/media/**`) backed by persisted Docker storage in `infra/storage/media`.
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
- Customer profile now supports WhatsApp phone capture (`/api/auth/me/profile`) and notifications prioritize that phone as destination contact.
- Backend notifications now support runtime provider switching from admin settings without restart, with env variables kept as fallback.
- Twilio auth token, SendGrid API key, and SMTP password are now encrypted at rest in `system_settings`.

## Documentation

- [Architecture](docs/architecture.md)
- [Domain Events](docs/domain-events.md)
- [Payment Flow](docs/payment-flow.md)
- [Deployment](docs/deployment.md)
- [Roadmap](docs/roadmap.md)
- [Changelog](CHANGELOG.md)
- [Frontend README](frontend/README.md)
- [Backend README](backend/README.md)
