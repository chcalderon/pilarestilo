# Pilar Estilo — Frontend

> "Lujo con Propósito" — Luxury with Purpose

Astro 4 SSR frontend for the Pilar Estilo luxury boutique platform.

## Stack

- **Astro 4** (SSR, `@astrojs/node` adapter)
- **React 18** (islands architecture — interactive components only)
- **Tailwind CSS** with custom brand tokens
- **Zustand** (auth + cart state, persisted to `localStorage`)
- **Lucide React** (icon set)
- **Playwright** (E2E smoke tests)

## Running Locally

```bash
cd frontend
npm install
npm run dev        # starts dev server at http://localhost:4321
npm run build      # production build
npm run preview    # preview built output
npm run test:e2e   # run Playwright smoke tests
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `PUBLIC_API_BASE_URL` | `/api` | Base URL for backend API calls |

```env
# frontend/.env  (never committed)
PUBLIC_API_BASE_URL=http://localhost:8080/api
```

---

## Typography

| Font | Family | Weights | Usage |
|---|---|---|---|
| Cormorant Garamond | `font-display` | 300/400/500 | Product names, section headers, hero subheads |
| Pinyon Script | `font-script` | 400 | "Elegancia & Estilo" hero accent, chapter marks. Never below 32px |
| Montserrat | `font-sans` (default) | 300/400/500/600 | Body, nav, buttons, admin UI |

Fonts are loaded from `@fontsource` packages (no CDN, offline-build friendly). Imported in `BaseLayout.astro`.

---

## Design Tokens

| Token | Hex | Usage |
|---|---|---|
| `pe-rose` | `#B76E79` | Primary accent — CTAs, active states, ornamental borders |
| `pe-rose-deep` | `#8E4F58` | Hover/pressed accent |
| `pe-rose-soft` | `#E8C9CC` | Hover backgrounds, badges |
| `pe-black` | `#1A1A1A` | Dark text, sidebar background |
| `pe-offwhite` | `#F8F4EF` | Page background |
| `pe-cream` | `#EDE3D8` | Card backgrounds, admin table header |
| `pe-charcoal` | `#3A3A3A` | Secondary text |
| `pe-gold` | `#C6A96B` | Logo monogram only |

All tokens defined in `tailwind.config.mjs`.

---

## i18n

Routes prefixed with locale: `/es/...` and `/en/...`. Root `/` redirects to `/es/`.

Keys live in:
- `src/i18n/es.json` — Spanish (default)
- `src/i18n/en.json` — English mirror

`t(key, locale)` in `src/i18n/index.ts` resolves dot-notation keys with Spanish fallback.

Admin UI is Spanish-only (no locale prefix under `/admin/**`).

**Adding a new key:** add to both JSON files, then use `t('your.key', locale)` in Astro or pass as prop to React islands.

---

## Auth Flow

1. User submits `LoginForm` / `RegisterForm` island.
2. Island calls `POST /api/auth/login` or `POST /api/auth/register`.
3. On success, token stored in two places:
   - **Zustand `authStore`** (`localStorage` key `pe-auth`) — React islands read user identity.
   - **`pe_token` cookie** (`document.cookie`, `SameSite=Lax`, 24h) — Astro SSR middleware reads server-side.
4. Logout clears both Zustand store and cookie.

---

## Admin Access

Admin routes (`/admin/**`) are protected by `src/middleware.ts`:

- Reads `pe_token` cookie server-side on every request.
- Decodes JWT, verifies `role === 'ADMIN'` and token not expired.
- Non-admin or missing token → redirect to `/admin/login?redirect=<path>`.

**Login credentials (development seed):**

| Email | Password | Role |
|---|---|---|
| `admin@pilarestilo.com` | `admin2026` | ADMIN |

Backend `@PreAuthorize("hasRole('ADMIN')")` guards remain active as a second layer.

---

## Component Map

```
src/
├── middleware.ts                — SSR admin JWT guard
├── layouts/
│   ├── BaseLayout.astro        — Root HTML shell: fonts, Navbar, Footer, grain overlay
│   └── AdminLayout.astro       — Fixed sidebar + sticky breadcrumb topbar
├── components/                 — Pure Astro server components
│   ├── Navbar.astro            — Two-row: utility bar + category mega-menu
│   ├── Hero.astro              — Full-bleed editorial with grain + cursive overlay
│   ├── ProductCard.astro       — Ornamental rules, hover lift, rating stars
│   ├── RatingStars.astro       — Read-only star display (0–5)
│   ├── CategoryTile.astro      — Square tile for category landing links
│   ├── Breadcrumbs.astro       — Listing/detail/admin breadcrumb trail
│   ├── OrnamentalDivider.astro — Roman-numeral chapter mark divider
│   ├── Button.astro            — Variants: primary/outline/ghost/icon
│   ├── Icon.astro              — Lucide SVG renderer for Astro context
│   ├── Footer.astro            — 4-col with newsletter, social, brand statement
│   └── LocaleSwitcher.astro    — ES | EN toggle preserving path
├── islands/                    — React islands (client-side interactive)
│   ├── auth/
│   │   ├── LoginForm.tsx       — JWT login, sets cookie + Zustand
│   │   ├── RegisterForm.tsx    — Register, sets cookie + Zustand
│   │   ├── AccountMenu.tsx     — Navbar dropdown: login/register or account/logout
│   │   └── AccountPage.tsx     — Profile + my reviews + my orders tabs
│   ├── reviews/
│   │   ├── ReviewForm.tsx      — Star rating input (authenticated users only)
│   │   └── ReviewList.tsx      — Paginated approved reviews with rating filter
│   ├── filters/
│   │   └── ProductFilters.tsx  — Category tree + brand + condition + price; URL-bound
│   ├── admin/
│   │   ├── AdminLoginForm.tsx  — Admin-specific login (checks role === ADMIN)
│   │   ├── AdminSidebar.tsx    — Collapsible nav island
│   │   ├── DataTable.tsx       — Generic sortable/paginated table with bulk actions
│   │   ├── AdminDashboard.tsx  — KPI cards + quick-link grid
│   │   ├── ProductTable.tsx    — Products CRUD on DataTable
│   │   ├── ProductForm.tsx     — Create/edit product modal with category select
│   │   ├── CategoryTree.tsx    — Tree view with inline create/edit/delete
│   │   ├── ReviewModerationQueue.tsx — Approve/delete reviews with filter tabs
│   │   └── PaymentReviewQueue.tsx    — Approve/reject payments on DataTable
│   └── CartPage.tsx            — Cart drawer with line items, qty edit, subtotal
├── pages/
│   ├── index.astro             — Redirects to /es/
│   ├── [locale]/
│   │   ├── index.astro         — Home: Hero + category tiles + product rail
│   │   ├── products/index.astro — Filtered listing with ProductFilters island
│   │   ├── products/[id].astro  — Detail: gallery + info + tabs (desc/reviews/shipping)
│   │   ├── categories/[slug].astro — Category landing with filter rail
│   │   ├── cart.astro          — Cart page
│   │   ├── auth/login.astro    — Login page
│   │   ├── auth/register.astro — Register page
│   │   └── account/index.astro — Account dashboard
│   └── admin/
│       ├── login.astro         — Admin login (dark theme)
│       ├── index.astro         — Dashboard (AdminDashboard island)
│       ├── products.astro      — Product management
│       ├── categories.astro    — Category tree management
│       ├── reviews.astro       — Review moderation queue
│       └── payments.astro      — Payment review queue
├── lib/
│   ├── api.ts                  — Typed fetch wrapper + all backend call functions
│   ├── authStore.ts            — Zustand store for current user (hydrated from /api/auth/me)
│   ├── cartStore.ts            — Zustand cart store (persisted to localStorage)
│   └── icons.ts               — Re-exports app-wide lucide icon set
└── i18n/
    ├── es.json                 — Spanish translations
    ├── en.json                 — English translations
    └── index.ts               — t() helper + locale utilities
```

## Docker

```bash
docker build -t pilar-estilo-frontend .
docker run -p 4321:4321 -e PUBLIC_API_BASE_URL=http://api:8080/api pilar-estilo-frontend
```
