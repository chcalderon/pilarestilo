# Dashboard Stats — Design Spec

**Date:** 2026-04-27
**Status:** Approved

---

## Overview

A role-aware dashboard showing daily and weekly KPIs. Each worker role sees a tailored view — ADMIN sees everything, SELLER sees their own caja stats, DESPACHADOR sees dispatch queue stats, etc. Data is computed server-side and cached for performance.

---

## Role-Aware Stat Panels

### ADMIN / SUPERVISOR

| Panel | Metric |
|---|---|
| Ventas hoy | Total revenue today (sum of PAID orders) |
| Ventas semana | Revenue this week (Mon–today) |
| Órdenes hoy | Count of orders placed today |
| Tasa de conversión | Orders / sessions (if analytics available, else omit) |
| Cajas abiertas | Count of currently open cash registers |
| Despachos pendientes | Count of PENDING dispatches |
| Despachos en progreso | Count of IN_PROGRESS dispatches |
| Top productos | Top 5 products by units sold this week |
| Gráfico ventas | Bar chart: daily revenue for the last 7 days |

### SELLER

| Panel | Metric |
|---|---|
| Mi caja hoy | Current caja status (open/closed), expected balance |
| Ventas en mi caja | Count and total of SALE movements in open caja |
| Última venta | Time + amount of most recent sale movement |

### DESPACHADOR

| Panel | Metric |
|---|---|
| Pendientes | Count of PENDING dispatches |
| Mis despachos hoy | Count dispatched by this user today |
| En progreso | Count IN_PROGRESS claimed by this user |

### ADMINISTRACION

| Panel | Metric |
|---|---|
| Trabajadores activos | Count of workers with vigency covering today |
| Próximos vencimientos | Workers whose vigencyEnd is within 7 days |

---

## API Endpoints

```
GET /api/dashboard/stats          — returns stats object shaped by caller's role
```

Single endpoint, response shape varies by role. Backend determines which stats to compute based on JWT role.

Response example (ADMIN):
```json
{
  "role": "ADMIN",
  "dailySales": { "amount": 1250000, "orderCount": 12 },
  "weeklySales": { "amount": 8430000, "orderCount": 74 },
  "openCashRegisters": 3,
  "pendingDispatches": 8,
  "inProgressDispatches": 2,
  "topProducts": [
    { "productId": "...", "name": "Polera Blanca", "unitsSold": 18 }
  ],
  "dailyRevenueSeries": [
    { "date": "2026-04-21", "amount": 980000 },
    ...
  ]
}
```

Response example (SELLER):
```json
{
  "role": "SELLER",
  "currentCaja": {
    "status": "OPEN",
    "openedAt": "2026-04-27T09:00:00",
    "expectedBalance": 450000,
    "saleCount": 5,
    "saleTotal": 320000
  },
  "lastSale": { "amount": 45000, "recordedAt": "2026-04-27T11:30:00" }
}
```

Cache: stats computed fresh per request for now (no cache layer in this phase). TODO: add Redis cache with 60s TTL in future.

---

## Frontend Component: DashboardPage

Route: `/[locale]/dashboard`

Accessible to all worker roles + ADMIN (per permissions matrix).

### Layout

Grid of stat cards + one chart. Responsive (2-col mobile, 4-col desktop).

**Stat card:**
- Icon (lucide)
- Label
- Primary value (large, bold, `font-['Cormorant_Garamond',serif]`)
- Optional secondary value / change indicator

**Revenue chart (ADMIN/SUPERVISOR only):**
- 7-day bar chart using Recharts (`BarChart`)
- Bars: `--pe-rose` color
- Y-axis: CLP formatted (`$ {value / 1000}K`)
- Tooltip: full amount + date

### Loading state

Skeleton cards while fetching. No full-page spinner.

### Error state

If `/api/dashboard/stats` fails: inline error message per card, no crash.

---

## DB Queries

Stats computed via direct SQL aggregates — no new tables required:

```sql
-- Daily sales
SELECT COALESCE(SUM(total_amount), 0), COUNT(*)
FROM orders
WHERE status = 'PAID'
  AND DATE(paid_at) = CURRENT_DATE;

-- Weekly sales
SELECT COALESCE(SUM(total_amount), 0), COUNT(*)
FROM orders
WHERE status = 'PAID'
  AND paid_at >= DATE_TRUNC('week', CURRENT_DATE);

-- 7-day series
SELECT DATE(paid_at) as day, COALESCE(SUM(total_amount), 0) as revenue
FROM orders
WHERE status = 'PAID'
  AND paid_at >= CURRENT_DATE - INTERVAL '6 days'
GROUP BY day
ORDER BY day;
```

---

## Flyway

No new tables. Dashboard uses existing `orders`, `cash_registers`, `dispatches`, `users`.

---

## Out of Scope (Future)

- Redis cache layer (60s TTL for stats)
- Custom date range selector
- Export to CSV / PDF
- Realtime updates via WebSocket
- Goal/target tracking per seller
