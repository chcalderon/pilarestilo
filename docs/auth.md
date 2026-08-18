# Pilar Estilo - Authentication and Authorization

This document reflects the implementation currently in the codebase.

## 1. JWT Scheme

| Property | Value |
|---|---|
| Algorithm | HS256 |
| Access token TTL | 24 hours |
| Refresh token TTL | 7 days |
| Secret source | `JWT_SECRET` env var |
| Access token claims | `sub`, `email`, `role`, `permissions`, `permissionCodes`, `iat`, `exp` |
| Refresh token claims | `sub`, `type: "refresh"`, `iat`, `exp` |

`role` is a single unprefixed string (`ADMIN`, not `ROLE_ADMIN`); the `ROLE_`/`PERM_` prefixes are
added server-side in `AuthenticatedUser.toAuthorities()`. `permissions` holds legacy view keys
(`dashboard`, `productos`, …); `permissionCodes` holds modern RBAC codes (`products.read`, …).
Both are resolved at login by `RolePermissionResolutionService` — see `JwtTokenProvider`.

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
| `POST` | `/api/auth/google` | Login/register through Google ID token |
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
| `POST /api/payment-proofs` | `isAuthenticated()` |
| `GET /api/payment-proofs/{paymentId}` | `isAuthenticated()` + customer ownership check for `CUSTOMER` role |
| `GET /api/media/payment-proofs/**` | `denyAll()` — receipts moved out of the public media root |
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
- `PUT /api/auth/me/avatar`

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

The storefront navbar exposes two guest auth triggers when the user is not authenticated:

- Text CTA `Log in` that opens the popover directly in the `login` tab.
- `UserPlus` icon that opens the popover in the `register` tab.

**Components:**

| File | Role |
|---|---|
| `frontend/src/components/auth/RegisterPopoverTrigger.tsx` | Trigger button (text or icon) that owns open/close state |
| `frontend/src/components/auth/RegisterPopoverPanel.tsx` | Panel mounted via `createPortal` to `document.body` |
| `frontend/src/components/auth/RegisterPopoverForm.tsx` | Tabbed form (register / login) + Google sign-in + fallback links to full auth pages |

**Behaviour:**

- Desktop: anchored below the trigger button (`position: fixed`, width 320 px, `z-index 9999`).
- Mobile (< 640 px): full-width bottom sheet.
- Focus is trapped inside the panel; `Escape` and outside-click close it.
- Register tab collects `fullName`, `email`, `password`; calls `registerUser(email, password, fullName)`.
- Login tab collects `email`, `password`; calls `loginUser(email, password)`.
- Popover footer includes links to `/{locale}/auth/login` and `/{locale}/auth/register` for full-page fallback UX.
- Google sign-in rendered via Google Identity Services SDK (`window.google.accounts.id.renderButton`), matching the pattern in `LoginForm.tsx`.
- On success, calls `setAuth(token, user)` and triggers `onSuccess()` (closes the panel; no page navigation).

The panel is wired into `AccountMenu.tsx` and replaces direct guest navigation to `/auth/login` from the header CTA.

---
## 6. Frontend Token Storage

The token lives in two places because two runtimes need it:

| Store | Read by |
|---|---|
| `localStorage` key `pe-auth` (Zustand `authStore`, `persist`) | React islands — drives the UI |
| `pe_token` cookie | `frontend/src/middleware.ts` — the **only** token SSR can see |

**Invariant: `frontend/src/lib/authStore.ts` is the sole writer of the cookie.** `setAuth()` writes
it, `clearAuth()` deletes it, and `onRehydrateStorage` re-mirrors it on page load (or clears the
store if the JWT has expired). Never assign `document.cookie = 'pe_token=…'` anywhere else.

Writing the two stores from separate call sites is what caused the storefront → `/admin` bounce:
the navbar login popover called `setAuth()` without writing the cookie, so the middleware saw an
anonymous request and redirected an already-logged-in admin to `/admin/login`.

Cookie attributes (`writeAuthTokenCookie`):

| Attribute | Value |
|---|---|
| `path` | `/` |
| `max-age` | derived from the JWT `exp` claim, so cookie and token expire together |
| `SameSite` | `Lax` |
| `Secure` | only when `location.protocol === 'https:'` — local dev is plain `http://localhost` |
| `HttpOnly` | **not set** — the cookie is written and read by client JS, so it is XSS-readable |

Islands that may run before hydration use `token ?? readAuthTokenCookie()` (see `AdminDashboard`,
`CartPage`, `UserManagement`, …).

---

## 7. Admin SSR Guard

`frontend/src/middleware.ts` protects `/admin/**` (except `/admin/login`):

1. Reads the `pe_token` cookie; missing → redirect to `/admin/login?redirect=<path>`
2. Decodes the JWT payload (`lib/jwt.ts` — payload only, signature is not verified here)
3. Requires `role` ∈ `ADMIN_PANEL_ROLES` (`lib/roles.ts`): `ADMIN`, `SUPERVISOR`, `ADMINISTRACION`,
   `DESPACHADOR`, `SELLER`. `CUSTOMER` is rejected.
4. Rejects expired tokens via the `exp` claim
5. Revalidates server-side against `GET {INTERNAL_API_BASE_URL}/auth/me` and re-checks the role
   from the response, catching tokens with a stale or forged signature
6. Enforces per-route RBAC on three routes, ADMIN always allowed, otherwise modern
   `permissionCodes` with a legacy `permissions` fallback; denial redirects to `/admin/`:

   | Route prefix | `permissionCode` | legacy view key |
   |---|---|---|
   | `/admin/roles-permisos` | `roles.read` | `roles_permisos` |
   | `/admin/users` | `users.read` | `usuarios` |
   | `/admin/settings` | `settings.read` | `configuracion` |

Failure handling distinguishes a bad credential from an unreachable backend:

- **Invalid token** (bad role, expired, non-OK `/auth/me`) → delete the cookie, redirect to
  `/admin/login?redirect=<path>`
- **Backend unreachable** (`fetch` throws, or a 200 with an unreadable body) → redirect to
  `/admin/login?redirect=<path>&reason=backend_unavailable` and **keep** the cookie, so a
  transient outage does not log every staff member out. `admin/login.astro` reads `reason` and
  shows "el servidor administrativo no está disponible" instead of a credentials error.

Set `RBAC_DEBUG=true` (implicit in dev) for `[RBAC] middleware …` decision logging.

Backend authorization (`@PreAuthorize`, §4) remains the real security boundary — this guard only
decides what renders.

---

## 8. Seed Admin Account

| Email | Password | Role |
|---|---|---|
| `admin@pilarestilo.com` | `admin2026` | `ADMIN` |

To promote another user:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@domain.com';
```

---

## 9. Password Policy

Registration currently enforces minimum length at API boundary:

- `RegisterRequest.password` uses `@Size(min = 8)`
- Passwords are hashed with BCrypt


