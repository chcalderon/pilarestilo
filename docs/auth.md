# Pilar Estilo — Authentication & Authorization

## Overview

Authentication is JWT-based (HS256). The backend issues signed tokens; the frontend stores them in two places so both React islands (client-side) and the Astro SSR middleware (server-side) can read them.

---

## 1. JWT Scheme

| Property | Value |
|---|---|
| Algorithm | HS256 |
| Access token TTL | 24 hours |
| Refresh token TTL | 7 days |
| Secret source | `JWT_SECRET` environment variable |
| Claim: `sub` | User UUID |
| Claim: `email` | User email address |
| Claim: `role` | One of `ADMIN`, `SELLER`, `CUSTOMER` |
| Claim: `exp` | Unix epoch seconds (expiry) |

**Generating a production secret:**

```bash
openssl rand -base64 32
```

Minimum 32 bytes (256 bits) for HS256. Set the output as `JWT_SECRET` in `infra/.env` — never commit the real secret.

---

## 2. Auth Endpoints

| Method | Path | Auth required | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | No | Create a new `CUSTOMER` account |
| `POST` | `/api/auth/login` | No | Issue access + refresh tokens |
| `POST` | `/api/auth/refresh` | Refresh token in body | Issue a new access token |
| `GET` | `/api/auth/me` | Bearer access token | Return current user info |

**Register request:**
```json
{ "email": "user@example.com", "password": "strong-password", "fullName": "Jane Doe" }
```

**Login request:**
```json
{ "email": "user@example.com", "password": "strong-password" }
```

**Login response:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "userId": "uuid",
  "email": "user@example.com",
  "fullName": "Jane Doe",
  "role": "CUSTOMER"
}
```

---

## 3. Security Allowlist

The `SecurityConfig` permits the following paths **without authentication**:

| Pattern | Methods | Reason |
|---|---|---|
| `/api/auth/**` | POST | Login and registration are public |
| `/api/products/**` | GET | Storefront product browsing |
| `/api/categories/**` | GET | Storefront category navigation |
| `/api/products/*/reviews/**` | GET | Product review display |
| `/actuator/health` | GET | Docker / Caddy healthcheck |

Everything else requires a valid JWT in the `Authorization: Bearer <token>` header.

---

## 4. Role Matrix

| Endpoint pattern | CUSTOMER | SELLER | ADMIN |
|---|---|---|---|
| `GET /api/products/**` | ✓ | ✓ | ✓ |
| `POST/PATCH/DELETE /api/products/**` | ✗ | ✓ | ✓ |
| `GET /api/categories/**` | ✓ | ✓ | ✓ |
| `POST/PATCH/DELETE /api/categories/**` | ✗ | ✗ | ✓ |
| `POST /api/products/*/reviews` | ✓ | ✓ | ✓ |
| `DELETE /api/reviews/{id}` | own only | own only | ✓ |
| `PATCH /api/reviews/{id}/approve` | ✗ | ✗ | ✓ |
| `GET /api/reviews` (admin list) | ✗ | ✗ | ✓ |
| `GET /api/orders` | own only | ✓ | ✓ |
| `POST /api/orders` | ✓ | ✓ | ✓ |
| `PATCH /api/payments/{id}/approve` | ✗ | ✗ | ✓ |
| `GET /api/auth/me` | ✓ | ✓ | ✓ |

---

## 5. Frontend Token Storage

Tokens are stored in two places so both client-side React islands and the SSR middleware can read them:

| Location | Key | Who reads it | How it's set |
|---|---|---|---|
| `localStorage` | `pe-auth` | React islands (Zustand) | `authStore.ts` after login |
| Browser cookie | `pe_token` | Astro SSR middleware | `document.cookie = 'pe_token=...; path=/; ...'` in `LoginForm` / `RegisterForm` / `AdminLoginForm` |

The cookie is **not** `httpOnly` — it is readable by JavaScript. This is intentional: the Astro Node.js server process reads it server-side from the HTTP request headers before any JavaScript executes, while React islands can clear it on logout via `document.cookie`.

**Logout** clears both:
```typescript
// clear Zustand
authStore.getState().clear();
// clear cookie
document.cookie = 'pe_token=; path=/; max-age=0; SameSite=Lax';
```

---

## 6. SSR Admin Guard (Middleware)

`frontend/src/middleware.ts` intercepts every request to `/admin/**` (except `/admin/login`):

1. Reads the `pe_token` cookie from `request.headers.get('cookie')`.
2. Decodes the JWT payload with `atob()` (base64url → JSON).
3. Checks `payload.role === 'ADMIN'` and `payload.exp > Date.now() / 1000`.
4. If valid → allows the request through.
5. If invalid/missing/expired → redirects to `/admin/login?redirect=<original-path>`.

The backend `@PreAuthorize("hasRole('ADMIN')")` guards remain active as a second layer — bypassing the frontend middleware (e.g., via `curl`) still hits the backend auth wall.

---

## 7. Bootstrapping the First Admin

The seed data in `V6__seed_v2.sql` includes one admin account:

| Field | Value |
|---|---|
| Email | `admin@pilarestilo.com` |
| Password | `admin2026` |
| Role | `ADMIN` |

**To create a new admin on a fresh deployment:**

1. Register via `POST /api/auth/register` — this creates a `CUSTOMER` account.
2. Promote to `ADMIN` directly in the database:
   ```sql
   UPDATE users SET role = 'ADMIN' WHERE email = 'your-admin@domain.com';
   ```
3. Log in via `/admin/login` — the JWT will now carry `role: ADMIN`.

There is no admin-promotion endpoint by design — privilege escalation requires direct database access.

---

## 8. Password Policy

Passwords are hashed with BCrypt (strength 12) via `BCryptPasswordEncoderAdapter`. No minimum length is enforced at the application layer currently — the seed users use 8+ character passwords. A validation annotation (`@Size(min=8)`) can be added to `RegisterRequest` to enforce this at the API boundary.
