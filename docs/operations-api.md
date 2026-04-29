# Pilar Estilo - Operations and Admin API Surface

This document complements `docs/auth.md` and `docs/architecture.md` with the operational modules added after the initial core commerce flows.
It is based on the current controllers under `backend/src/main/java/com/pilarestilo/**/web`.

## 1. Dashboard

Controller: `dashboard/infrastructure/web/DashboardController`

- `GET /api/dashboard/stats`
  - Access: `ADMIN`, `SUPERVISOR`, `SELLER`, `DESPACHADOR`, `ADMINISTRACION`
  - Purpose: role-aware KPI payload for worker dashboard cards

Response is a flat JSON object with `@JsonInclude(NON_NULL)` — null fields are omitted per role.

### Response shape by role

**ADMIN / SUPERVISOR**

```json
{
  "role": "ADMIN",
  "dailySales":           { "amount": 0.00, "orderCount": 0 },
  "weeklySales":          { "amount": 0.00, "orderCount": 0 },
  "openCashRegisters":    0,
  "pendingDispatches":    0,
  "inProgressDispatches": 0,
  "topProducts": [
    { "productId": "uuid", "name": "Vestido X", "unitsSold": 12 }
  ],
  "dailyRevenueSeries": [
    { "date": "2026-04-22", "amount": 0.00 }
  ]
}
```

**SELLER**

```json
{
  "role": "SELLER",
  "currentCaja": {
    "status": "OPEN",
    "openedAt": "2026-04-28T09:00:00",
    "expectedBalance": 0.00,
    "saleCount": 0,
    "saleTotal": 0.00
  },
  "lastSale": { "amount": 0.00, "recordedAt": "2026-04-28T10:30:00" }
}
```

`currentCaja` and `lastSale` are omitted when no register is open / no sales recorded.

**DESPACHADOR**

```json
{
  "role": "DESPACHADOR",
  "pendingDispatches": 0,
  "myDispatchedToday": 0,
  "myInProgress":      0
}
```

**ADMINISTRACION**

```json
{
  "role": "ADMINISTRACION",
  "activeWorkers": 0,
  "expiringWorkers": [
    { "userId": "uuid", "fullName": "Ana Pérez", "vigencyEnd": "2026-05-01" }
  ]
}
```

## 2. Cash Register (Caja)

Controllers:

- `cashregister/infrastructure/web/CajaController`
- `cashregister/infrastructure/web/AdminCajaController`

Endpoints:

- `POST /api/caja/open` - open current worker cash register (`SELLER`, `ADMIN`)
- `POST /api/caja/close` - close current worker cash register (`SELLER`, `ADMIN`)
- `GET /api/caja/current` - current worker cash register state (`SELLER`, `ADMIN`)
- `POST /api/caja/movements` - add manual IN/OUT movement (`SELLER`, `ADMIN`)
- `GET /api/caja/history` - paginated history for the authenticated seller/admin (`SELLER`, `ADMIN`)
  - Optional filters:
    - `status`: `OPEN` | `CLOSED`
    - `from`: `YYYY-MM-DD` (openedAt lower bound)
    - `to`: `YYYY-MM-DD` (openedAt upper bound)
    - `page`, `size`, `sort`
- `GET /api/admin/caja` - paginated cross-seller list (`ADMIN`, `SUPERVISOR`)
  - Optional filters:
    - `status`: `OPEN` | `CLOSED`
    - `sellerId`: UUID
    - `from`: `YYYY-MM-DD` (openedAt lower bound)
    - `to`: `YYYY-MM-DD` (openedAt upper bound)
    - `page`, `size`, `sort`

The `GET /api/caja/history` endpoint is served by `ListSellerCashRegisterHistoryUseCase` and scopes results to the authenticated user's own registers. The admin list endpoint (`GET /api/admin/caja`) is served by `ListCashRegistersUseCase` and supports cross-seller filtering via `sellerId`.

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
- `GET /api/admin/despachos` - paginated active dispatch queue (`ADMIN`, `SUPERVISOR`)
- `GET /api/admin/despachos/history` - paginated dispatch history (`ADMIN`, or any authenticated user with `despachos` permission)
  - Optional filters:
    - `from`: `YYYY-MM-DD` (dispatchedAt / scheduledDate lower bound; defaults to first of current month)
    - `to`: `YYYY-MM-DD` (inclusive upper bound; defaults to end of same month)
    - `page`, `size`
  - Returns `DispatchHistoryRowDto` with enriched fields: `dispatchedBy` (user full name), `soldBy` (seller full name or `"Web"`)
- `POST /api/admin/despachos/seed` (`ADMIN`) for admin bootstrap/testing

Order status side effects linked to dispatch actions:

- `POST /api/despachos/{id}/claim` updates order status to `PREPARING_ORDER`
- `POST /api/despachos/{id}/dispatch` updates order status to `SHIPPED`
- `POST /api/despachos/{id}/deliver` updates order status to `DELIVERED`
- `PATCH /api/orders/{id}/confirm-delivery` (`isAuthenticated()`) - customer self-confirms delivery; also sets dispatch to `DELIVERED` if not already

### Auto-confirm scheduled job

`DispatchAutoDeliveryScheduler` runs on the cron schedule configured via `app.dispatch.auto-delivery.cron` (default: every 30 minutes, `0 */30 * * * *`). It finds dispatches in `DISPATCHED` status whose `dispatchedAt` timestamp is older than 15 days and automatically transitions them to `DELIVERED`, updating the linked order status as well.

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

Current in-app notification types:

- `DISCOUNT_CODE_ASSIGNED`
- `ORDER_CONFIRMED`
- `PAYMENT_RECEIVED`
- `ORDER_PREPARING`
- `ORDER_SHIPPED`

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
