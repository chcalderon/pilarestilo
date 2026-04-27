# Dispatch System — Design Spec

**Date:** 2026-04-27
**Status:** Approved

---

## Overview

The dispatch system manages the outbound shipping workflow. Once an order is paid, a `DESPACHADOR` worker picks it up, packs it, assigns it to a carrier, and marks it dispatched. ADMIN and SUPERVISOR can view all dispatches. Customers receive status updates via the existing notification system.

---

## Domain Model

### Dispatch (domain aggregate)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `orderId` | UUID | FK → orders (1:1) |
| `dispatcherId` | UUID \| null | FK → users (DESPACHADOR who claimed it) |
| `status` | `PENDING` \| `IN_PROGRESS` \| `DISPATCHED` \| `DELIVERED` \| `FAILED` | |
| `carrier` | String \| null | e.g. "Chilexpress", "Starken" |
| `trackingCode` | String \| null | Carrier tracking number |
| `scheduledDate` | LocalDate \| null | Target dispatch date |
| `dispatchedAt` | LocalDateTime \| null | Actual dispatch timestamp |
| `deliveredAt` | LocalDateTime \| null | |
| `notes` | String \| null | Internal notes |
| `createdAt` | LocalDateTime | Auto |

**Status transitions:**
```
PENDING → IN_PROGRESS (dispatcher claims it)
IN_PROGRESS → DISPATCHED (dispatcher marks as shipped + carrier + tracking)
DISPATCHED → DELIVERED (confirmed received) | FAILED (delivery failed)
```

---

## Rules

- A dispatch record is created automatically when an order reaches `PAID` status (same `OrderPaidEvent` used by caja).
- Only one DESPACHADOR can claim a dispatch (`IN_PROGRESS`) at a time — first claim wins.
- A dispatcher can unclaim (return to `PENDING`) if they haven't dispatched yet.
- ADMIN can reassign or override any dispatch status.
- When status changes to `DISPATCHED` or `DELIVERED`, a push notification is sent to the customer via the existing notification service.

---

## API Endpoints

```
GET    /api/despachos                        — DESPACHADOR: list pending + own in-progress
POST   /api/despachos/{id}/claim             — DESPACHADOR: claim a PENDING dispatch
POST   /api/despachos/{id}/unclaim           — DESPACHADOR: release back to PENDING
POST   /api/despachos/{id}/dispatch          — DESPACHADOR: mark DISPATCHED (body: carrier, trackingCode, scheduledDate?, notes?)
POST   /api/despachos/{id}/deliver           — DESPACHADOR/ADMIN: mark DELIVERED
POST   /api/despachos/{id}/fail              — DESPACHADOR/ADMIN: mark FAILED (body: notes)

GET    /api/admin/despachos                  — ADMIN/SUPERVISOR: all dispatches (filterable)
GET    /api/admin/despachos/{id}             — ADMIN/SUPERVISOR: detail
PUT    /api/admin/despachos/{id}             — ADMIN: force-update any field
```

All secured via `@PreAuthorize`. Dispatcher routes require `DESPACHADOR` or `ADMIN`.

---

## Frontend Views

### Dispatcher — Despachos View (`/[locale]/despachos`)

**Pending queue** (left panel or top section):
- Cards showing: order number, customer name, items summary, scheduled date
- "Tomar" button per card → claims it, moves to "En progreso"

**In progress** (own active dispatches):
- Order details, customer address
- Form fields: carrier dropdown, tracking code input, notes
- "Marcar despachado" button → submits + status changes
- "Liberar" button → unclaim

**Completed today** (DISPATCHED today by this dispatcher):
- Read-only list

### Admin/Supervisor — Despachos View (`/[locale]/admin/despachos`)

- Tabs or filter: All / Pending / In Progress / Dispatched / Delivered / Failed
- Date range filter
- Table: order #, customer, dispatcher, carrier, tracking, status, dispatched at
- Row click → detail modal with full history + admin override actions

---

## DB Schema

```sql
CREATE TABLE dispatches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    dispatcher_id UUID REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    carrier VARCHAR(100),
    tracking_code VARCHAR(200),
    scheduled_date DATE,
    dispatched_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ON dispatches(status);
CREATE INDEX ON dispatches(dispatcher_id);
CREATE INDEX ON dispatches(order_id);
```

Flyway: **V39** (dispatches table)

---

## Notifications

On `DISPATCHED`: send customer notification "Tu pedido #{orderNumber} fue despachado vía {carrier}. Código de seguimiento: {trackingCode}."

On `DELIVERED`: send customer notification "Tu pedido #{orderNumber} fue entregado."

Uses existing `NotificationService`. Triggered from `DispatchStatusChangedEvent` domain event.

---

## Out of Scope (Future)

- QR code or label printing
- Carrier API integration (auto-generate tracking)
- Return/reverse logistics flow
- Bulk dispatch actions
- Customer-facing tracking page
