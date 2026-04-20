# Changelog

All notable changes to this project are documented in this file.

The format is inspired by Keep a Changelog.

## [Unreleased]

### Changed
- Pending

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
