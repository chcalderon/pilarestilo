# Load test — data map

**Everything below is simulated test data.** No real customer, sale, or media file is
created or touched. The load test drives the real purchase → dispatch → delivery flow
(`frontend/e2e/checkout-dispatch-flow.spec.ts` is the reference) from 9 concurrent buyers
plus 1 admin, for ~10 minutes.

Order data is disposable here — the ecommerce has not launched, every existing order/payment
is already test data (see the `shop-has-not-launched-yet` memory). The catalogue and users
are the parts that matter, so the stock bump is snapshotted and restored exactly.

---

## 1. Prep — before the run (reversible)

| Change | Table / target | From | To | Restore |
|---|---|---|---|---|
| Bump stock so the run isn't stock-limited | `products.stock` | ~36 total across 16 rows | `100000` each | from snapshot file |
| | `product_variants.stock_on_hand` | ~38 total across 24 rows | `100000` each | from snapshot file |
| | `product_variants.stock_reserved` | 2 (one variant) | `0` | from snapshot file |
| Drop the products Redis cache so the new stock is seen | Redis (`pe_redis`) | populated | `FLUSHALL` | repopulates from DB on next read |
| Silence notification email during the run | `system_settings.notification_providers` | `EMAIL_SMTP` | `LOG` | back to `EMAIL_SMTP` |

`product_size_stocks` (the V56 fossil) is **not** touched — nothing reads it.
No product is created or edited; no `image_url` changes. No file is uploaded anywhere.

Snapshot taken first: `products(id, stock)` + `product_variants(id, stock_on_hand, stock_reserved)`
→ `scripts/loadtest/.stock-snapshot.sql` (gitignored).

---

## 2. During the run — rows created (all disposable)

Per **completed purchase** (≈ 90–200 total over 10 min, depending on throughput):

| Table | Rows | Notable values |
|---|---|---|
| `users` | 1 | `email = load_<vu>_<iter>_<ts>@loadtest.local`, `full_name = "LoadTest Buyer <n>"`, `role = CUSTOMER`, fixed password hash, consent = true |
| `customer_addresses` | 1 | region/city/comuna from the real catalog, `line1 = "LT Calle <n>"`, `phone = +56912345678` |
| `orders` | 1 | written by **order-service** (`APP_ORDER_REMOTE_WRITE_ENABLED=true`); status walks CREATED → PAYMENT_UNDER_REVIEW → PAID → PREPARING_ORDER → SHIPPED → DELIVERED; `public_reference = PE-…` |
| `order_items` | 1 | qty 1, one existing product + variant, price snapshot |
| `inventory_movements` | ~2 | reserve + confirm (audit) |
| `payments` | 1 | TRANSFER; PENDING → SUBMITTED → APPROVED; `proof_reference = "https://placehold.co/400x600/png"` (placeholder URL, no upload); `reviewed_by = <admin id>` |
| `sales_documents` | 1 | `document_type = BOLETA`, `folio = "LT-<n>"` (invented, exactly like the manual SII flow), net+tax+total, live |
| `dispatches` | 1 | CREATED → CLAIMED → SHIPPED → DELIVERED; `claimed_by = <admin id>`, `carrier = <real courier id>`, `tracking_code = "LT-TRK-<ts>"` |
| `pilarestilo_notifications.notifications` | ~7 | WELCOME, ORDER_CONFIRMATION, TRANSFER_INSTRUCTIONS, PAYMENT_RECEIVED, ORDER_PREPARING, ORDER_SHIPPED, SALES_DOCUMENT_ISSUED — in-app rows only; **no email** (provider = LOG) |
| Kafka topics | events | OrderCreated / PaymentConfirmed / etc. — transient, retained per topic config, offsets advance, no cleanup needed |

`product_variants.stock_reserved` briefly ++ then `stock_on_hand` -- on confirm — ~1 unit per
order, ≤ 200 total, absorbed by the 100000 bump.

**Not touched:** `cash_registers` / `cash_movements` (ecommerce-paid orders never reach the
register), `system_settings` beyond the provider toggle, navigation, categories, permissions,
roles, the real admin/test-customer accounts, media & document storage on disk.

---

## 3. Who does what

| Actor | k6 scenario | Calls |
|---|---|---|
| **9 buyers** | `buyers` (0→9 ramp, hold 8 min) | `POST /auth/register` → `GET /products` → `POST /auth/me/addresses` → `POST /orders` → poll `GET /payments/order/{id}` → `PATCH /payments/{id}/proof` → poll `GET /orders/{id}` until SHIPPED → `PATCH /orders/{id}/confirm-delivery` → poll DELIVERED |
| **1 admin** | `admin` (1 VU, whole run) | loop: `GET /payments?status=SUBMITTED` → `PATCH /payments/{id}/review APPROVE` · `GET /despachos` → `POST /admin/sales-documents` (folio) → `POST /despachos/{id}/claim` → `POST /despachos/{id}/dispatch` · sleep 1.5s |

The admin is deliberately **one** VU — if 9 buyers outrun 1 admin a backlog builds, which is
the realistic thing to observe.

---

## 4. Cleanup — after the run

1. Restore stock from `.stock-snapshot.sql`; `FLUSHALL` Redis; `notification_providers` → `EMAIL_SMTP`.
2. Delete test data, FK order:
   `dispatches` → `sales_documents` → `payments` → `order_items` → `inventory_movements` → `orders`
   → `customer_addresses` → `users` (`WHERE email LIKE 'load\_%@loadtest.local'`) →
   `pilarestilo_notifications.notifications` (`WHERE user_id` in that set).
3. Verify counts back to the baseline in §0 below.

Scripted in `scripts/loadtest/cleanup.sh`.

---

## 0. Baseline (local, before any prep) — 2026-08-30

```
products              16   (stock sum 36)
product_variants      24   (on-hand sum 38, 1 variant with 2 reserved)
users                 21   (19 CUSTOMER)
orders                 2
payments               2
dispatches             0
sales_documents        0
customer_addresses     4
inventory_movements    2
system_settings.notification_providers = EMAIL_SMTP
```
