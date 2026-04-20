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

---

## Implemented storefront features

- Category and attribute filtering on product list
- Search overlay (keyword search via `/api/products/search`)
- Wishlist page + heart interactions
- Product detail size selector using `sizeStocks`
- Reviews list/create flow
- Cart experience with persisted state

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
    auth/
      AccountMenu.tsx
      AccountPage.tsx
      LoginForm.tsx
      RegisterForm.tsx
    product/
      SizeSelector.tsx
    reviews/
      ReviewForm.tsx
      ReviewList.tsx
    search/
      SearchOverlay.tsx
    wishlist/
      WishlistButton.tsx
      WishlistPage.tsx
```

---

## Docker

```bash
docker build -t pilar-estilo-frontend .
docker run -p 4321:4321 -e PUBLIC_API_BASE_URL=http://api:8080/api pilar-estilo-frontend
```
