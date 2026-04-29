# Pilar Estilo - Authentication and Authorization

This document reflects the implementation currently in the codebase.

## 1. JWT Scheme

| Property | Value |
|---|---|
| Algorithm | HS256 |
| Access token TTL | 24 hours |
| Refresh token TTL | 7 days |
| Secret source | `JWT_SECRET` env var |
| Claims | `sub`, `email`, `role`, `exp` |

Generate a secure secret:

```bash
openssl rand -base64 32
```

---

## 2. Auth Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Create `CUSTOMER` account |
| `POST` | `/api/auth/login` | Issue access + refresh tokens |
| `POST` | `/api/auth/refresh` | Rotate access token |
| `GET` | `/api/auth/me` | Return current authenticated user |
| `GET` | `/api/auth/me/profile` | Return authenticated user profile details |
| `PATCH` | `/api/auth/me/profile` | Update authenticated user profile (`fullName`, `phone`, `notificationChannelPreference`) |
| `PATCH` | `/api/auth/me/password` | Change authenticated user password |
| `PUT` | `/api/auth/me/avatar` | Upload authenticated user avatar |

---

## 3. Global Security Rules (`SecurityConfig`)

### Public routes

- `POST /api/auth/**`
- `POST /api/payments/webhooks/gateway`
- `POST /api/payments/webhooks/gateway/**`
- `GET /api/products/**`
- `GET /api/inventory/**`
- `GET /api/wishlist/shared/**`
- `GET /api/categories/**`
- `GET /api/media/**`
- `GET /api/system-settings/public`
- `/actuator/**`
- `/api/actuator/**`

### Authenticated routes

- `GET /api/auth/me`
- `GET /api/auth/me/profile`
- `PATCH /api/auth/me/profile`
- `PATCH /api/auth/me/password`
- Any route not explicitly listed as public

---

## 4. Method-Level Role Rules (`@PreAuthorize`)

These are the explicit role checks currently present in controllers:

| Endpoint | Rule |
|---|---|
| `GET /api/dashboard/stats` | `hasAnyRole('ADMIN','SUPERVISOR','SELLER','DESPACHADOR','ADMINISTRACION')` |
| `GET /api/notifications` | `isAuthenticated()` (class-level on controller) |
| `GET /api/notifications/unread-count` | `isAuthenticated()` (class-level on controller) |
| `PUT /api/notifications/{id}/read` | `isAuthenticated()` (class-level on controller) |
| `PUT /api/notifications/read-all` | `isAuthenticated()` (class-level on controller) |
| `GET /api/customers/{customerId}/credit` | `isAuthenticated()` + customer ownership check for `CUSTOMER` role |
| `GET /api/customers/{customerId}/credit/movements` | `isAuthenticated()` + customer ownership check for `CUSTOMER` role |
| `POST /api/customers/{customerId}/credit/grant` | `hasAnyRole('ADMIN','SELLER')` |
| `POST /api/customers/{customerId}/credit/use` | `hasAnyRole('ADMIN','SELLER')` |
| `POST /api/caja/open` | `hasAnyRole('SELLER','ADMIN')` |
| `POST /api/caja/close` | `hasAnyRole('SELLER','ADMIN')` |
| `GET /api/caja/current` | `hasAnyRole('SELLER','ADMIN')` |
| `POST /api/caja/movements` | `hasAnyRole('SELLER','ADMIN')` |
| `GET /api/caja/history` | `hasAnyRole('SELLER','ADMIN')` |
| `GET /api/admin/caja` | `hasAnyRole('ADMIN','SUPERVISOR')` |
| `GET /api/despachos` | `hasAnyRole('DESPACHADOR','ADMIN')` |
| `POST /api/despachos/{id}/claim` | `hasAnyRole('DESPACHADOR','ADMIN')` |
| `POST /api/despachos/{id}/unclaim` | `hasAnyRole('DESPACHADOR','ADMIN')` |
| `POST /api/despachos/{id}/dispatch` | `hasAnyRole('DESPACHADOR','ADMIN')` |
| `POST /api/despachos/{id}/deliver` | `hasAnyRole('DESPACHADOR','ADMIN')` |
| `POST /api/despachos/{id}/fail` | `hasAnyRole('DESPACHADOR','ADMIN')` |
| `GET /api/admin/despachos` | `hasAnyRole('ADMIN','SUPERVISOR')` |
| `GET /api/admin/despachos/history` | `hasRole('ADMIN')` or `isAuthenticated()` with `despachos` permission |
| `POST /api/admin/despachos/seed` | `hasRole('ADMIN')` |
| `PATCH /api/orders/{id}/confirm-delivery` | `isAuthenticated()` (customer ownership enforced in use case) |
| `POST /api/discounts` | `hasAnyRole('ADMIN','SELLER')` |
| `GET /api/discounts/{id}` | `hasAnyRole('ADMIN','SELLER')` |
| `GET /api/discounts` | `hasAnyRole('ADMIN','SELLER')` |
| `DELETE /api/discounts/{id}` | `hasAnyRole('ADMIN','SELLER')` |
| `GET /api/discounts/suggest-code` | `hasAnyRole('ADMIN','SELLER')` |
| `GET /api/discounts/validate-for-user` | `isAuthenticated()` |
| `GET /api/discounts/validate` | `hasAnyRole('ADMIN','SELLER')` |
| `POST /api/products/{productId}/reviews` | `isAuthenticated()` |
| `DELETE /api/reviews/{reviewId}` | `isAuthenticated()` |
| `GET /api/reviews/mine` | `isAuthenticated()` |
| `PATCH /api/reviews/{reviewId}/approve` | `hasRole('ADMIN')` |
| `GET /api/reviews` | `hasRole('ADMIN')` |
| `GET /api/wishlist` | `isAuthenticated()` |
| `POST /api/wishlist/items/{productId}` | `isAuthenticated()` |
| `DELETE /api/wishlist/items/{productId}` | `isAuthenticated()` |
| `GET /api/wishlist/share-link` | `isAuthenticated()` |
| `POST /api/wishlist/share-link` | `isAuthenticated()` |
| `DELETE /api/wishlist/share-link` | `isAuthenticated()` |
| `GET /api/orders` | `hasAnyRole('ADMIN','SELLER')` |
| `GET /api/orders/mine` | `isAuthenticated()` |
| `GET /api/orders/{id}` | `isAuthenticated()` + customer ownership check |
| `PATCH /api/orders/{id}/status` | `hasAnyRole('ADMIN','SELLER')` |
| `POST /api/payments` | `hasAnyRole('ADMIN','SELLER')` |
| `GET /api/payments/order/{orderId}` | `isAuthenticated()` + customer ownership check |
| `PATCH /api/payments/{id}/proof` | `isAuthenticated()` + customer ownership check for `CUSTOMER` role |
| `PATCH /api/payments/{id}/review` | `hasAnyRole('ADMIN','SELLER')` |
| `POST /api/payments/{id}/gateway/checkout` | `isAuthenticated()` + customer ownership check for `CUSTOMER` role |
| `GET /api/payments/{id}` | `hasAnyRole('ADMIN','SELLER')` |
| `GET /api/payments` | `hasAnyRole('ADMIN','SELLER')` |
| `POST /api/media/upload` | `hasAnyRole('ADMIN','SELLER')` |
| `POST /api/media/upload-proof` | `isAuthenticated()` |
| `POST /api/admin/media/migrate-category-images` | `hasRole('ADMIN')` |
| `GET /api/system-settings` | `hasRole('ADMIN')` |
| `PATCH /api/system-settings` | `hasRole('ADMIN')` |
| `GET /api/admin/workers` | `hasRole('ADMIN')` |
| `POST /api/admin/workers/{userId}/assign` | `hasRole('ADMIN')` |
| `DELETE /api/admin/workers/{userId}/revoke` | `hasRole('ADMIN')` |
| `GET /api/admin/permissions` | `hasRole('ADMIN')` |
| `PUT /api/admin/permissions` | `hasRole('ADMIN')` |
| `GET /api/users` | `hasRole('ADMIN')` |
| `GET /api/users/{id}` | `hasRole('ADMIN')` |
| `PATCH /api/users/{id}` | `hasRole('ADMIN')` (plus self-protection guards) |
| `PATCH /api/users/{id}/password` | `hasRole('ADMIN')` |
| `DELETE /api/users/{id}` | `hasRole('ADMIN')` (self-deletion blocked) |

Note: `GET /api/orders/{id}` - ADMIN/SELLER can read any order; CUSTOMER can only read their own (`customerId` must match principal id, otherwise `AccessDeniedException`).

Note: `GET /api/payments/order/{orderId}`, `PATCH /api/payments/{id}/proof`, and `POST /api/payments/{id}/gateway/checkout` apply ownership checks for `CUSTOMER` users. ADMIN/SELLER users can operate for support/admin workflows.

Note: `GET /api/customers/{customerId}/credit` and `GET /api/customers/{customerId}/credit/movements` apply the same customer ownership guard for `CUSTOMER` users.

Note: `POST /api/admin/despachos/seed` is designed for bootstrap/testing convenience and still requires admin role.

Note: `/api/auth/me/profile` and `/api/auth/me/password` are authenticated via global security rules and use current principal id internally.

Note: endpoints without method-level role guards still require authentication unless they are in the global public list.

Sensitive endpoints currently authenticated (but not role-scoped) because they have no `@PreAuthorize`:

- `POST /api/products`
- `PUT /api/products/{id}`
- `DELETE /api/products/{id}`
- `POST /api/categories`
- `PATCH /api/categories/reorder`
- `PATCH /api/categories/{id}`
- `DELETE /api/categories/{id}`

### Profile patch payload

`PATCH /api/auth/me/profile` accepts:

```json
{
  "fullName": "Pilar Admin",
  "phone": "+56912345678",
  "notificationChannelPreference": "AUTO"
}
```

Allowed values for `notificationChannelPreference`: `AUTO`, `WHATSAPP`, `EMAIL`, `BOTH`.

---

---

## 5. Inline Register / Login Popover

The storefront navbar exposes a `UserPlus` icon (`RegisterPopoverTrigger`) visible when the user is not authenticated. Clicking it opens an auth panel without navigating away from the current page.

**Components:**

| File | Role |
|---|---|
| `frontend/src/components/auth/RegisterPopoverTrigger.tsx` | Icon button that owns open/close state |
| `frontend/src/components/auth/RegisterPopoverPanel.tsx` | Panel mounted via `createPortal` to `document.body` |
| `frontend/src/components/auth/RegisterPopoverForm.tsx` | Tabbed form (register / login) + Google sign-in |

**Behaviour:**

- Desktop: anchored below the trigger button (`position: fixed`, width 320 px, `z-index 9999`).
- Mobile (< 640 px): full-width bottom sheet.
- Focus is trapped inside the panel; `Escape` and outside-click close it.
- Register tab collects `fullName`, `email`, `password`; calls `registerUser(email, password, fullName)`.
- Login tab collects `email`, `password`; calls `loginUser(email, password)`.
- Google sign-in rendered via Google Identity Services SDK (`window.google.accounts.id.renderButton`), matching the pattern in `LoginForm.tsx`.
- On success, calls `setAuth(token, user)` and triggers `onSuccess()` (closes the panel; no page navigation).

The panel is wired into `AccountMenu.tsx` — when the user is not authenticated the icon replaces the old `/register` link.

---

## 6. Frontend Token Storage

Frontend stores auth data in two places:

1. `localStorage` (`pe-auth`) via Zustand (`authStore`)
2. `pe_token` cookie for Astro SSR middleware checks

Logout clears both.

---

## 6. Admin SSR Guard

`frontend/src/middleware.ts` protects `/admin/**` (except `/admin/login`):

1. Reads `pe_token` cookie
2. Decodes JWT payload
3. Validates token expiry and `role === 'ADMIN'`
4. Redirects unauthorized users to `/admin/login?redirect=<path>`

Backend authorization remains the second protection layer.

---

## 7. Seed Admin Account

| Email | Password | Role |
|---|---|---|
| `admin@pilarestilo.com` | `admin2026` | `ADMIN` |

To promote another user:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@domain.com';
```

---

## 8. Password Policy

Registration currently enforces minimum length at API boundary:

- `RegisterRequest.password` uses `@Size(min = 8)`
- Passwords are hashed with BCrypt

