# Worker Roles, Vigency & Permissions — Design Spec

**Date:** 2026-04-27
**Status:** Approved

---

## Overview

Extend the user model with worker-specific roles, vigency dates, and a DB-stored permission matrix that controls both UI navigation and API access. The goal is a flexible RBAC foundation where ADMIN can assign workers, set their active dates, and view/edit which views each role can access.

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

### Current user permissions (authenticated)

```
GET /api/me/permissions   — returns list of view_keys the current user can access
```

All endpoints secured with `@PreAuthorize`. Worker management and permissions require `ADMIN`. `/api/me/permissions` requires any authenticated worker role.

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

On protected worker routes: fetch `/api/me/permissions`, then filter sidebar nav items to only show accessible views. Redirect to `/worker/waiting` or login if not eligible.

---

## Security Layers

1. **DB permissions → UI navigation**: sidebar items rendered only if view_key in user's permissions list
2. **`@PreAuthorize` on API endpoints**: enforces access at server level regardless of UI state
3. **Vigency check in `AuthService`** (or a filter): validate dates on every authenticated request for worker roles

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
