# Pilar Estilo — Architecture

## 1. Overview

Pilar Estilo is a luxury boutique ecommerce platform for fashion retail. It serves a bilingual (Spanish/English) customer base and is operated by a small team with an admin-heavy workflow (manual payment review, inventory control, promotion management).

**Deployment target:** A single Contabo VPS (Ubuntu 22.04 LTS, 4 GB RAM, 2 vCPUs). All services run as Docker containers behind a Caddy reverse proxy that handles TLS automatically via Let's Encrypt.

**Monorepo layout:**

```
PilarEstilo/
├── backend/          # Spring Boot 3 + Java 17 — REST API, domain logic
├── frontend/         # Astro 4 + React islands — storefront + admin UI
├── infra/            # Docker Compose, Caddyfile, .env.example
└── docs/             # Architecture, domain events, payment flow, deployment, roadmap
```

The frontend and backend are fully decoupled: the frontend communicates exclusively through the `/api/*` HTTP interface. The Caddy proxy routes `/api/*` to the backend and everything else to the frontend.

---

## 2. Frontend Architecture

### Framework: Astro 4

Astro was chosen for the storefront for three reasons:

1. **SEO** — Product pages, category pages, and the homepage are server-side rendered. Search engines index content without JavaScript.
2. **Zero-JS default** — Astro ships no JavaScript unless explicitly opted in with a `client:*` directive. Page weight stays low.
3. **Islands architecture** — Interactive components (cart, auth forms, admin tables) are isolated React islands that hydrate independently.

### React Islands

React is used only where interactivity is required:

| Island | Purpose |
|---|---|
| `CartBadge` | Live cart item count in navbar |
| `AddToCartButton` | Add-to-cart with immediate feedback |
| `CartPage` | Cart drawer — line items, qty edit, subtotal |
| `AccountMenu` | Navbar dropdown: login/register or account/logout |
| `LoginForm` / `RegisterForm` | JWT auth forms |
| `AccountPage` | Profile + my reviews + my orders tabs |
| `ReviewForm` / `ReviewList` | Star-rating input and approved review display |
| `ProductFilters` | URL-bound filter rail (category, brand, condition, price) |
| `AdminDashboard` | KPI cards (products, payments, reviews counts) |
| `ProductTable` | Admin product CRUD with DataTable |
| `CategoryTree` | Admin category tree CRUD |
| `ReviewModerationQueue` | Admin approve/delete reviews |
| `PaymentReviewQueue` | Admin approve/reject payments |

### Tailwind Design Tokens (v2)

All brand tokens are defined in `tailwind.config.mjs`:

| Token | Hex | Role |
|---|---|---|
| `pe-rose` | `#B76E79` | Primary accent — CTAs, active states |
| `pe-rose-deep` | `#8E4F58` | Hover/pressed accent |
| `pe-rose-soft` | `#E8C9CC` | Hover backgrounds, badges |
| `pe-black` | `#1A1A1A` | Dark text, sidebar background |
| `pe-offwhite` | `#F8F4EF` | Page background |
| `pe-cream` | `#EDE3D8` | Card backgrounds, admin table header |
| `pe-charcoal` | `#3A3A3A` | Secondary text |
| `pe-gold` | `#C6A96B` | Logo monogram only |

Typography: `Cormorant Garamond` (display/product names), `Pinyon Script` (hero cursive accent), `Montserrat` (body/nav/admin UI).

### i18n

Routing is managed by Astro's built-in i18n support:

- `/es/*` — Spanish storefront
- `/en/*` — English storefront

All UI strings live in `src/i18n/es.json` and `src/i18n/en.json`. Admin UI is Spanish-only. The `t(key, locale)` helper (in `src/i18n/index.ts`) resolves keys at build time.

### Auth Flow

JWT tokens are issued by the backend (`POST /api/auth/login`, `/api/auth/register`) and stored in two places:

1. **Zustand store** (`src/lib/authStore.ts`, persisted to `localStorage` via `pe-auth` key) — for React islands to read user identity and token.
2. **`pe_token` cookie** (set by `LoginForm`/`RegisterForm` via `document.cookie`) — for the Astro SSR middleware to read server-side.

The Astro middleware (`src/middleware.ts`) intercepts every `/admin/**` request, decodes the `pe_token` cookie, and redirects to `/admin/login` if the token is missing, expired, or lacks the `ADMIN` role.

### Admin UI

The admin panel at `/admin/*` is protected by:
1. **Server-side middleware** — `src/middleware.ts` validates the JWT cookie before rendering the page shell.
2. **Backend `@PreAuthorize`** — All admin API calls require `ADMIN` role even if someone bypassed the frontend guard.

The admin layout (`AdminLayout.astro`) renders a fixed sidebar (`AdminSidebar.tsx` island) and a sticky breadcrumb topbar.

---

## 3. Backend Architecture

### Technology Stack

- **Java 17** — LTS release, records, sealed interfaces, pattern matching.
- **Spring Boot 3.3** — Production-grade DI, auto-configuration, Actuator.
- **Spring Security** — JWT filter chain; stateless sessions; `@PreAuthorize` role guards.
- **JJWT 0.12.x** — HS256 token generation and validation.
- **Spring Data JPA + Hibernate** — ORM for PostgreSQL.
- **Flyway** — Database migration versioning.
- **PostgreSQL 16** — Primary data store.

### Hexagonal Architecture (Ports and Adapters)

```
External World
     │
     ▼
┌─────────────────────────────────────┐
│  infrastructure/web (Controllers)    │ ← drives →
│  infrastructure/persistence (JPA)   │ ← drives →
└─────────────┬───────────────────────┘
              │ via ports
              ▼
┌─────────────────────────────────────┐
│  application/usecases               │
│  (orchestrates domain, no I/O)      │
└─────────────┬───────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│  domain/model  domain/events        │ ← pure Java, no frameworks
│  domain/ports  domain/valueobjects  │
└─────────────────────────────────────┘
```

**Rule: no Spring or JPA annotations in any `domain/` package.**

### Module Map

| Module | Key Responsibility |
|---|---|
| `product` | Catalog CRUD, `avgRating`/`reviewCount` denormalization |
| `category` | Category tree (single-level parent/child), product taxonomy |
| `review` | 1–5 star reviews, approval workflow, `ReviewSummaryListener` |
| `order` | Order state machine (DRAFT → DELIVERED) |
| `payment` | Payment state machine (PENDING → APPROVED/REJECTED) |
| `discount` | Promo codes (fixed/percentage), validations |
| `user` | User accounts, roles (ADMIN/SELLER/CUSTOMER) |
| `inventory` | Stock reservation / confirmation / release |
| `notification` | `NotificationSender` port + `LogNotificationAdapter` stub |
| `customerCredit` | Balance ledger, credit movements |
| `shared/auth` | JWT issuance, BCrypt password encoding, `JwtAuthenticationFilter` |

### ReviewSummaryListener — Denormalization Pattern

The `review` module publishes `ReviewCreated`, `ReviewApproved`, and `ReviewDeleted` events. The `ReviewSummaryListener` in `product/infrastructure/listeners/` subscribes to these events and recomputes `(avg_rating, review_count)` for the affected product using a dedicated `@Transactional(propagation = REQUIRES_NEW)` transaction.

This means:
- Product listings show ratings without a JOIN to the `reviews` table.
- `GET /api/products?category=zapatos` returns `avgRating` directly on the product DTO.
- If the listener fails, the product `avg_rating` can temporarily lag — the summary endpoint `GET /api/products/{id}/reviews/summary` always queries live data.

---

## 4. Module Dependency Rules

**The Golden Rule: the `domain` package has zero framework imports.**

Modules may depend on each other **only through application-layer ports**. Direct infra-to-infra cross-module calls are forbidden.

Allowed:
```
order/application → product/domain/ports/ProductRepository (interface)
review/infrastructure/listeners → product/domain/ports/ProductRepository (via DI)
```

Forbidden:
```
order/infrastructure → product/infrastructure/ProductJpaRepository  ✗
```

---

## 5. Domain Event Publisher Seam

The `DomainEventPublisher` port (in `shared/domain/`) has one implementation today: `SpringDomainEventPublisher`, which wraps Spring's `ApplicationEventPublisher`. Events are synchronous, transactional, and in-process.

To migrate to Kafka: create `KafkaDomainEventPublisher implements DomainEventPublisher`, mark it `@Primary`, and update `@EventListener` subscribers to `@KafkaListener`. No domain or application code changes required. See `docs/domain-events.md` for the full migration guide.

---

## 6. Path to Microservices

Each module's package boundary was designed to be extractable. Extraction order recommendation: `product` (read-heavy) → `inventory` → `order` → `payment`.

---

## 7. Folder Structure

```
PilarEstilo/
│
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/pilarestilo/
│       ├── shared/
│       │   ├── auth/          # JWT provider, filter, BCrypt adapter
│       │   ├── domain/        # DomainEvent, DomainEventPublisher port
│       │   ├── application/   # Money value object
│       │   └── infrastructure/bootstrap/  # SecurityConfig
│       ├── product/           # domain / application / infrastructure
│       ├── category/          # domain / application / infrastructure
│       ├── review/            # domain / application / infrastructure + listeners/
│       ├── order/
│       ├── payment/
│       ├── discount/
│       ├── user/
│       ├── inventory/
│       ├── notification/
│       └── customerCredit/
│
├── frontend/
│   ├── Dockerfile
│   ├── astro.config.mjs       # SSR + Node adapter + i18n config
│   ├── tailwind.config.mjs    # Brand tokens, fonts, keyframes
│   └── src/
│       ├── middleware.ts      # SSR admin JWT guard
│       ├── layouts/           # BaseLayout, AdminLayout
│       ├── components/        # Navbar, Hero, ProductCard, etc. (Astro)
│       ├── islands/           # React islands (auth/, admin/, reviews/, filters/)
│       ├── pages/
│       │   ├── [locale]/      # Storefront pages (es/en)
│       │   └── admin/         # Admin pages (login, index, products, categories, reviews, payments)
│       ├── i18n/              # es.json, en.json, index.ts
│       └── lib/               # api.ts, authStore.ts, cartStore.ts
│
├── infra/
│   ├── docker-compose.yml     # All services + JWT_SECRET env var
│   ├── Caddyfile              # Reverse proxy + TLS
│   └── .env.example           # Environment variable template
│
└── docs/
    ├── architecture.md        # This file
    ├── domain-events.md       # Event catalog + Kafka migration guide
    ├── auth.md                # JWT scheme, allowlist, role matrix
    ├── payment-flow.md        # Payment state machine + admin workflow
    ├── deployment.md          # VPS deployment runbook
    └── roadmap.md             # v2 completed + P3–P5 planned
```
