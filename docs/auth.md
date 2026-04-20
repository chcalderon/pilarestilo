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

---

## 3. Global Security Rules (`SecurityConfig`)

### Public routes

- `POST /api/auth/**`
- `GET /api/products/**`
- `GET /api/categories/**`
- `/actuator/**`
- `/api/actuator/**`

### Authenticated routes

- `GET /api/auth/me`
- Any route not explicitly listed as public

---

## 4. Method-Level Role Rules (`@PreAuthorize`)

These are the explicit role checks currently present in controllers:

| Endpoint | Rule |
|---|---|
| `POST /api/products/{productId}/reviews` | `isAuthenticated()` |
| `DELETE /api/reviews/{reviewId}` | `isAuthenticated()` |
| `GET /api/reviews/mine` | `isAuthenticated()` |
| `PATCH /api/reviews/{reviewId}/approve` | `hasRole('ADMIN')` |
| `GET /api/reviews` | `hasRole('ADMIN')` |
| `GET /api/wishlist` | `isAuthenticated()` |
| `POST /api/wishlist/items/{productId}` | `isAuthenticated()` |
| `DELETE /api/wishlist/items/{productId}` | `isAuthenticated()` |
| `GET /api/orders` | `hasAnyRole('ADMIN','SELLER')` |
| `GET /api/orders/mine` | `isAuthenticated()` |
| `GET /api/orders/{id}` | `isAuthenticated()` + customer ownership check |
| `PATCH /api/orders/{id}/status` | `hasAnyRole('ADMIN','SELLER')` |
| `POST /api/payments` | `hasAnyRole('ADMIN','SELLER')` |
| `PATCH /api/payments/{id}/proof` | `isAuthenticated()` |
| `PATCH /api/payments/{id}/review` | `hasAnyRole('ADMIN','SELLER')` |
| `GET /api/payments/{id}` | `hasAnyRole('ADMIN','SELLER')` |
| `GET /api/payments` | `hasAnyRole('ADMIN','SELLER')` |

Note: `GET /api/orders/{id}` - ADMIN/SELLER can read any order; CUSTOMER can only read their own (`customerId` must match principal id, otherwise `AccessDeniedException`).

Note: endpoints without method-level role guards still require authentication unless they are in the global public list.

---

## 5. Frontend Token Storage

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

