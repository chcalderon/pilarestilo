# Pilar Estilo - Frontend

Astro 4 SSR frontend for storefront + admin.

## Stack

- Astro 4 (`@astrojs/node`)
- React 18 islands
- Tailwind CSS
- Zustand (`auth`, `cart`, `wishlist`)
- Lucide React icons
- Playwright smoke tests

---

## Run locally

```bash
cd frontend
npm install
npm run dev
npm run build
npm run preview
npm run test:e2e
```

Default dev URL: `http://localhost:4321`

---

## Environment

| Variable | Default | Description |
|---|---|---|
| `PUBLIC_API_BASE_URL` | `/api` | Browser-side API base |
| `INTERNAL_API_BASE_URL` | `http://backend:8080/api` | SSR/server-side API base |
| `PUBLIC_WHATSAPP_PHONE` | `+56900000000` | Storefront WhatsApp destination number (used in floating CTA) |
| `PUBLIC_WHATSAPP_MESSAGE_ES` | `Hola Pilar Estilo, quiero ayuda con una compra.` | Prefilled WhatsApp message for Spanish storefront |
| `PUBLIC_WHATSAPP_MESSAGE_EN` | `Hi Pilar Estilo, I need help with a purchase.` | Prefilled WhatsApp message for English storefront |

Example:

```env
PUBLIC_API_BASE_URL=http://localhost:8080/api
```

---

## Routing and i18n

- Storefront localized routes: `/es/*`, `/en/*`
- Root `/` redirects to `/es/`
- Admin routes are non-localized: `/admin/*`

Translations:

- `src/i18n/es.json`
- `src/i18n/en.json`

---

## Auth and admin protection

- Login/register islands obtain JWT from backend.
- Token is persisted in Zustand and mirrored to `pe_token` cookie.
- `src/middleware.ts` guards `/admin/**` server-side and requires `role === 'ADMIN'`.
- In `AccountMenu`, guest CTA now uses `Log in` (popover-first UX) instead of direct navigation to `/auth/login`.
- Auth popover can open directly on `login` tab and includes fallback links to full-page `/{locale}/auth/login` and `/{locale}/auth/register`.

---

## Implemented storefront features

- Category and attribute filtering on product list
- Search overlay (keyword search via `/api/products/search`)
- Dual-price product cards (`listPrice` struck-through + discounted sale price)
- Wishlist page + heart interactions
- Wishlist page now supports creating/copying/disabling public share links
- Product detail size selector using `sizeStocks`
- Product detail variant picker (`color + talla`) with stock-aware add-to-cart for variant combinations
- Reviews list/create flow
- Quick star-only rating from product cards for authenticated users
- Cart experience with persisted state
- Product cards now consume backend-hosted media paths (`/api/media/**`) instead of external image hosts
- Account orders tab supports payment-proof submission for `BANK_TRANSFER` orders (image upload or proof URL)
- Account profile tab now supports profile name update + password change (`/api/auth/me/profile`, `/api/auth/me/password`)
- Account profile tab now supports WhatsApp phone capture in profile (`/api/auth/me/profile`) for notification routing.
- Account profile tab now supports notification channel preference (`AUTO`, `WHATSAPP`, `EMAIL`, `BOTH`) to route transactional notifications per user.
- Cart summary now supports payment method selection (`Transferencia` / `Pasarela (simulada)`) and reflects worker discount
- Gateway simulation actions are available in account orders for `PAYMENT_GATEWAY` (approve/reject simulation)
- Account orders now include `Ir a pagar / Pay now` action to open `/api/payments/{id}/gateway/checkout` sessions.
- Public shared wishlist route available at `/:locale/wishlist/shared/:token`.

---

## Admin UX and responsive updates (April 2026)

- Product admin (`ProductTable`) supports dual view modes:
  - `Grilla` (table workflow)
  - `Cards` (visual workflow), persisted in local storage (`pe-admin-products-view`)
- Product admin filters now include `Categoria` in addition to `Condicion` and `Marca`.
- Product admin now includes `Fecha ingreso` column in grid view, sortable ascending/descending, plus date-range filters (`Fecha desde` / `Fecha hasta`) with native calendar inputs.
- `DataTable` has mobile card rendering for better usability on phones.
- Admin layout now includes a mobile navigation drawer with menu button + overlay close behavior.
- Admin action buttons were refreshed with consistent Lucide icon usage in core management flows.
- Light theme tokens were darkened slightly (storefront + admin) to improve readability/contrast.
- Storefront and wishlist grids were adjusted to be mobile-first (`1 column` at smallest breakpoints).
- Payment review queue now lists `PENDING` entries and shows review actions only for `SUBMITTED`/`UNDER_REVIEW`.
- Payment review queue now has `Por revisar` / `Pagados` tabs, search, date sorting, and clear filters.
- Added admin user management screen (`/admin/users`) with edit/reset password/role/status/delete and customer credit assignment.
- Admin user management now uses server-side pagination by tab (`Clientes`/`Trabajadores`) and status filter (`Todos`/`Habilitados`/`Bloqueados`).
- Admin users page now points admins to `Mi cuenta` (`/es/account?tab=profile`) for editing their own profile/password.
- Cart dark theme copy in summary/actions was updated to improve text contrast.
- Storefront now includes a floating WhatsApp button that opens a prefilled chat in `wa.me` (desktop + mobile).
- Added admin system settings screen (`/admin/settings`) to edit storefront channels and choose/configure notification provider (`LOG`, WhatsApp Simulado/Twilio, SendGrid, SMTP, N8N webhook) from one UI, including n8n webhook URL/header/API key fields.
- Storefront WhatsApp and footer social URLs now load from backend public settings endpoint (`/api/system-settings/public`).

---

## Storefront mobile and encoding fixes (April 2026)

- Mobile header actions were reflowed to avoid overlapping logo real estate on initial viewport load.
- Category navigation in `Navbar` now behaves as a mobile slider with `<` / `>` controls and smooth horizontal step scrolling.
- Slider controls auto-hide at start/end and initialize hidden until navigation bounds are computed.
- Navbar logo compact/full switch now uses directional scroll gating + transition lock to avoid flicker loops near threshold.
- Spanish storefront and wishlist strings were normalized to UTF-8 and corrected for accent rendering (`Coleccion`, `Categorias`, `Condicion`, `Paginacion`, etc.).
- Category breadcrumbs now use a proper separator glyph and no longer display broken encoded characters.
- Verified in local build and Dockerized runtime (`npm run build` + `docker compose ... up -d --build frontend`).

---

## Component map (current)

```text
src/
  components/
    Button.astro
    CuotasDisplay.astro
    DeliveryEstimate.astro
    EmptyState.astro
    Footer.astro
    Hero.astro
    Icon.astro
    LocaleSwitcher.astro
    Navbar.astro
    ProductCard.astro
    RatingStars.astro

  islands/
    AddToCartButton.tsx
    CartBadge.tsx
    CartDrawer.tsx
    CartPage.tsx
    admin/
      AdminDashboard.tsx
      AdminLoginForm.tsx
      AdminSidebar.tsx
      CategoryTree.tsx
      DataTable.tsx
      PaymentReviewQueue.tsx
      ProductForm.tsx
      ProductTable.tsx
      ReviewModerationQueue.tsx
      SystemSettingsPanel.tsx
      UserManagement.tsx
    auth/
      AccountMenu.tsx
      AccountPage.tsx
      LoginForm.tsx
      RegisterForm.tsx
    product/
      ProductVariantSelector.tsx
      SizeSelector.tsx
    reviews/
      QuickRateStars.tsx
      ReviewForm.tsx
      ReviewList.tsx
    search/
      SearchOverlay.tsx
    wishlist/
      WishlistButton.tsx
      WishlistPage.tsx
      SharedWishlistPage.tsx
```

---

## Docker

```bash
docker build -t pilar-estilo-frontend .
docker run -p 4321:4321 -e PUBLIC_API_BASE_URL=http://api:8080/api pilar-estilo-frontend
```
