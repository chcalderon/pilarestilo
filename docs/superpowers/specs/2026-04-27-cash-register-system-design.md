# Cash Register (Caja) System — Design Spec

**Date:** 2026-04-27
**Status:** Approved

---

## Overview

The cash register system allows a `SELLER` worker to operate a personal till (caja) — opening it with an initial float, recording sales and cash movements, then closing it with a reconciliation summary. Each caja belongs to one seller and one work session. ADMIN and SUPERVISOR can view any caja.

---

## Domain Model

### CashRegister (domain aggregate)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `sellerId` | UUID | FK → users |
| `openedAt` | LocalDateTime | When seller opened the till |
| `closedAt` | LocalDateTime \| null | null = still open |
| `openingBalance` | BigDecimal | Float given at open |
| `closingBalance` | BigDecimal \| null | Declared amount at close |
| `expectedBalance` | BigDecimal \| null | Computed: opening + net movements |
| `difference` | BigDecimal \| null | closingBalance - expectedBalance |
| `status` | `OPEN` \| `CLOSED` | |
| `notes` | String \| null | Free text at close |

### CashMovement (child entity)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `cashRegisterId` | UUID | FK → cash_registers |
| `type` | `SALE` \| `REFUND` \| `IN` \| `OUT` | |
| `amount` | BigDecimal | Always positive |
| `description` | String | Auto-filled for SALE/REFUND, manual for IN/OUT |
| `orderId` | UUID \| null | FK → orders (for SALE/REFUND) |
| `recordedAt` | LocalDateTime | |
| `recordedBy` | UUID | FK → users (seller or admin) |

---

## Rules

- A seller can have at most **one open caja at a time**. Opening a second throws a conflict.
- Only the owner seller (or ADMIN) can close a caja.
- Once `CLOSED`, a caja is immutable — no new movements.
- `SALE` movements are created automatically when an order is paid (event from order service).
- `IN` / `OUT` are manual cash adjustments (e.g., petty cash, withdrawals).
- `expectedBalance = openingBalance + SUM(SALE + IN) - SUM(REFUND + OUT)`

---

## API Endpoints

```
POST   /api/caja/open                    — SELLER opens their caja (body: openingBalance)
POST   /api/caja/close                   — SELLER closes their open caja (body: closingBalance, notes?)
GET    /api/caja/current                 — SELLER gets their currently open caja + movements
POST   /api/caja/{id}/movements          — SELLER adds IN/OUT movement (manual)
GET    /api/admin/caja                   — ADMIN/SUPERVISOR list all cajas (paginated, filterable by date/seller)
GET    /api/admin/caja/{id}              — ADMIN/SUPERVISOR detail view with movements
```

All endpoints secured via `@PreAuthorize`. `/api/caja/*` requires `SELLER`. `/api/admin/caja/*` requires `ADMIN` or `SUPERVISOR`.

---

## Frontend Views

### Seller — Caja Island (`/[locale]/caja`)

**State: No open caja**
- "Abrir caja" button
- Input for opening balance (moneda CLP)
- Confirm opens caja, transitions to open state

**State: Caja open**
- Header: opened time, seller name, opening balance
- Running total: expected balance (live, updates with movements)
- Movements list (chronological): type badge, description, amount, time
- "Agregar movimiento" button → inline form: type (IN/OUT), amount, description
- "Cerrar caja" button → confirmation modal with declared balance input + diff preview

**State: Caja closed (today)**
- Summary card: opening, closing declared, expected, difference (colored: green=0, yellow=small diff, red=large diff)
- Read-only movements list

### Admin/Supervisor — Caja List (`/[locale]/admin/caja`)

- Date range filter + seller filter
- Table: seller name, opened at, closed at, opening balance, closing balance, difference, status badge
- Row click → detail modal with full movements

---

## DB Schema

```sql
CREATE TABLE cash_registers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id UUID NOT NULL REFERENCES users(id),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    opening_balance NUMERIC(12,2) NOT NULL,
    closing_balance NUMERIC(12,2),
    expected_balance NUMERIC(12,2),
    difference NUMERIC(12,2),
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    notes TEXT
);

CREATE TABLE cash_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_register_id UUID NOT NULL REFERENCES cash_registers(id),
    type VARCHAR(10) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    order_id UUID REFERENCES orders(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX ON cash_registers(seller_id);
CREATE INDEX ON cash_registers(status);
CREATE INDEX ON cash_movements(cash_register_id);
```

Flyway: **V38** (cash_registers + cash_movements tables)

---

## Integration with Orders

When an order transitions to `PAID` status, the order service publishes an internal domain event `OrderPaidEvent { orderId, sellerId, amount }`. A `CashRegisterEventHandler` listens and creates a `SALE` movement on the seller's open caja. If seller has no open caja at payment time, movement is skipped (logged as warning — edge case for now).

---

## Out of Scope (Future)

- Multiple currency support
- Till float denomination tracking (coin/bill breakdown)
- Automated difference alerts / notifications
- Caja per physical terminal (currently one per seller per session)
