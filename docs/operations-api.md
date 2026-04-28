# Pilar Estilo - Operations and Admin API Surface

This document complements `docs/auth.md` and `docs/architecture.md` with the operational modules added after the initial core commerce flows.
It is based on the current controllers under `backend/src/main/java/com/pilarestilo/**/web`.

## 1. Dashboard

Controller: `dashboard/infrastructure/web/DashboardController`

- `GET /api/dashboard/stats`
  - Access: `ADMIN`, `SUPERVISOR`, `SELLER`, `DESPACHADOR`, `ADMINISTRACION`
  - Purpose: role-aware KPI payload for worker dashboard cards

## 2. Cash Register (Caja)

Controllers:

- `cashregister/infrastructure/web/CajaController`
- `cashregister/infrastructure/web/AdminCajaController`

Endpoints:

- `POST /api/caja/open` - open current worker cash register (`SELLER`, `ADMIN`)
- `POST /api/caja/close` - close current worker cash register (`SELLER`, `ADMIN`)
- `GET /api/caja/current` - current worker cash register state (`SELLER`, `ADMIN`)
- `POST /api/caja/movements` - add manual IN/OUT movement (`SELLER`, `ADMIN`)
- `GET /api/admin/caja` - paginated admin/supervisor list (`ADMIN`, `SUPERVISOR`)

## 3. Dispatch (Despachos)

Controllers:

- `dispatch/infrastructure/web/DespachoController`
- `dispatch/infrastructure/web/AdminDespachoController`

Endpoints:

- `GET /api/despachos` (`DESPACHADOR`, `ADMIN`)
- `POST /api/despachos/{id}/claim` (`DESPACHADOR`, `ADMIN`)
- `POST /api/despachos/{id}/unclaim` (`DESPACHADOR`, `ADMIN`)
- `POST /api/despachos/{id}/dispatch` (`DESPACHADOR`, `ADMIN`)
- `POST /api/despachos/{id}/deliver` (`DESPACHADOR`, `ADMIN`)
- `POST /api/despachos/{id}/fail` (`DESPACHADOR`, `ADMIN`)
- `GET /api/admin/despachos` (`ADMIN`, `SUPERVISOR`)
- `POST /api/admin/despachos/seed` (`ADMIN`) for admin bootstrap/testing

## 4. Worker Roles and Permission Matrix

Controllers:

- `shared/rbac/infrastructure/web/WorkerController`
- `shared/rbac/infrastructure/web/PermissionController`

Endpoints:

- `GET /api/admin/workers` (`ADMIN`)
- `POST /api/admin/workers/{userId}/assign` (`ADMIN`)
- `DELETE /api/admin/workers/{userId}/revoke` (`ADMIN`)
- `GET /api/admin/permissions` (`ADMIN`)
- `PUT /api/admin/permissions` (`ADMIN`)

## 5. Customer Credit

Controller: `customercredit/infrastructure/web/controllers/CustomerCreditController`

Endpoints:

- `GET /api/customers/{customerId}/credit`
  - Access: authenticated
  - Runtime guard: `CUSTOMER` can only query their own `customerId`
- `GET /api/customers/{customerId}/credit/movements`
  - Access: authenticated
  - Runtime guard: `CUSTOMER` can only query their own `customerId`
- `POST /api/customers/{customerId}/credit/grant` (`ADMIN`, `SELLER`)
- `POST /api/customers/{customerId}/credit/use` (`ADMIN`, `SELLER`)

## 6. In-App Notifications

Controller: `notification/infrastructure/web/controllers/NotificationController`

All endpoints are authenticated via class-level `@PreAuthorize("isAuthenticated()")`:

- `GET /api/notifications`
- `GET /api/notifications/unread-count`
- `PUT /api/notifications/{id}/read`
- `PUT /api/notifications/read-all`

## 7. Media Admin Operations

Controller: `shared/infrastructure/web/controllers/MediaAdminController`

- `POST /api/admin/media/migrate-category-images` (`ADMIN`)
  - Purpose: migrate external category image URLs into current media storage provider.

## 8. Frontend Surfaces Linked to These Modules

Operational/admin pages currently present in `frontend/src/pages/admin` and islands:

- `dashboard`
- `caja`
- `despachos`
- `roles-permisos`
- `users`
- `settings`
- `discounts`
- `payments`
- `reviews`

Store/customer surfaces:

- account notifications/history (`account`)
- wishlist (`wishlist` and shared wishlist route)
- checkout + payment proof flow (`cart`, payment islands)
