# Worker Roles, Vigency & Permissions — Design Spec

**Date:** 2026-04-27
**Status:** Approved

---

## Overview

Extend the user model with worker-specific roles, vigency dates, and a DB-stored permission matrix. Permissions are embedded in the JWT at login time — no per-request DB lookup, no frontend fetch needed for navigation. ADMIN can assign workers, set their active dates, and view/edit which views each role can access. When the matrix changes, affected workers must re-login for changes to take effect (acceptable tradeoff — matrix changes are infrequent admin operations).

---

## Roles

Extend `UserRole` enum with four worker roles (existing `CUSTOMER` and `ADMIN` stay unchanged):

| Role | Description |
|---|---|
| `ADMIN` | Full access, manages all workers and config |
| `SUPERVISOR` | Oversight of caja and dashboard |
| `ADMINISTRACION` | Manages users and roles/permissions view |
| `DESPACHADOR` | Handles dispatch workflow only |
| `SELLER` | Operates the cash register (caja) |
| `CUSTOMER` | End customer, no worker access |

---

## Vigency

Two new nullable fields on `User` domain model:

- `workerVigencyStart: LocalDate` — date from which worker access activates (null = no restriction)
- `workerVigencyEnd: LocalDate | null` — date after which access deactivates (null = no end)

**Behavior:**
- If today is before `workerVigencyStart`: worker lands on a waiting page ("Tu acceso comienza el {date}")
- If today is after `workerVigencyEnd`: treated as inactive worker (redirect to login with message)
- Vigency only applies to roles `SUPERVISOR`, `ADMINISTRACION`, `DESPACHADOR`, `SELLER` — not `ADMIN` or `CUSTOMER`

**DB columns added to `users` table:**
```sql
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS worker_vigency_start DATE,
  ADD COLUMN IF NOT EXISTS worker_vigency_end DATE;
```

---

## Permission Matrix

Stored in `role_permissions` table — not hardcoded. Each row = one role has access to one view.

```sql
CREATE TABLE role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    view_key VARCHAR(100) NOT NULL,
    UNIQUE (role, view_key)
);
```

**View keys** (match frontend route segments):
- `dashboard`
- `productos`
- `usuarios`
- `caja`
- `despachos`
- `configuracion`
- `roles_permisos`

**Default seed (Flyway):**

| view_key | ADMIN | SUPERVISOR | ADMINISTRACION | DESPACHADOR | SELLER |
|---|:---:|:---:|:---:|:---:|:---:|
| dashboard | ✓ | ✓ | ✓ | ✓ | ✓ |
| productos | ✓ | — | — | — | ✓ |
| usuarios | ✓ | — | ✓ | — | — |
| caja | ✓ | ✓ | — | — | ✓ |
| despachos | ✓ | — | — | ✓ | — |
| configuracion | ✓ | — | — | — | — |
| roles_permisos | ✓ | — | ✓ | — | — |

ADMIN always has all permissions (enforced at API layer, not just DB).

---

## API Endpoints

### Worker Management (ADMIN only)

```
GET    /api/admin/workers                   — list all workers with role + vigency
POST   /api/admin/workers/{userId}/assign   — assign role + vigency to a user
DELETE /api/admin/workers/{userId}/revoke   — remove worker role (revert to CUSTOMER)
```

`POST /api/admin/workers/{userId}/assign` body:
```json
{
  "role": "SELLER",
  "vigencyStart": "2026-05-01",
  "vigencyEnd": null
}
```

### Permissions (ADMIN only)

```
GET  /api/admin/permissions          — full matrix: all roles × all view_keys
PUT  /api/admin/permissions          — replace matrix (array of {role, viewKey})
```

All endpoints secured with `@PreAuthorize`. Worker management and permissions require `ADMIN`.

---

## Frontend Components

### Worker Assignment Modal (Admin Users view)

Triggered from user row actions in the admin users table. Fields:
- Role dropdown (`SUPERVISOR`, `ADMINISTRACION`, `DESPACHADOR`, `SELLER`)
- Vigency start date picker
- Vigency end date picker (optional, "sin fecha de fin" label)
- Save / Cancel

### Worker Waiting Page

Route: `/[locale]/worker/waiting`

Shown when `today < workerVigencyStart`. Displays:
- Brand header
- "Tu acceso de trabajador comienza el {workerVigencyStart}" message
- Logout button

### Roles/Permissions View (Admin)

Route: `/[locale]/admin/roles-permisos`

Editable grid:
- Rows = view_keys (human-readable labels)
- Columns = roles (except CUSTOMER and ADMIN — ADMIN always has all, CUSTOMER has none)
- Cells = toggle checkboxes
- Save button POSTs full matrix

### Navigation Guard (frontend)

Permissions are read directly from the JWT claim `permissions: string[]` — no API call needed. On mount, the auth store decodes the JWT and exposes the `permissions` array. The sidebar filters nav items against this list. Redirect to `/worker/waiting` or login if not eligible.

---

## Security Layers

1. **JWT claim `permissions`**: embedded at login by querying `role_permissions` for the user's role. Sidebar reads this claim — no DB hit per request.
2. **`@PreAuthorize` on API endpoints**: hardcoded in Java, enforces access at server level regardless of JWT content. This is the real security boundary.
3. **Vigency check on login**: `JwtTokenProvider` validates `workerVigencyStart`/`End` at token generation time. Expired vigency = token not issued. Frontend also checks the claim on mount to catch mid-session expiry.

**JWT payload additions:**
```json
{
  "sub": "uuid",
  "role": "SELLER",
  "permissions": ["dashboard", "productos", "caja"],
  "vigencyStart": "2026-05-01",
  "vigencyEnd": null
}
```

**When matrix changes take effect:** Next login. ADMIN changing the matrix does not invalidate existing tokens — workers get new permissions on next login. Acceptable for an infrequent admin operation.

**Token expiry window risk:** If a worker's vigency ends mid-session, their access token remains valid until it expires (default 1h). Mitigated by short access token TTL. Refresh token endpoint re-checks vigency on each refresh.

---

## Flyway Migrations

| Version | Description |
|---|---|
| V35 | Add `worker_vigency_start`, `worker_vigency_end` to `users` |
| V36 | Create `role_permissions` table |
| V37 | Seed default permission matrix |

---

## Out of Scope (Future)

- Per-user permission overrides (override matrix for a single user)
- Permission audit log
- Role-based discount limits
- Fine-grained action permissions (currently view-level only)

> **TODO (future):** Parameterize permission matrix further — add action-level permissions (read/write/delete per view). Tracked as future enhancement, not in this implementation.
