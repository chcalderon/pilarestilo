# External Sale Intake — Design Spec

**Date:** 2026-08-31
**Status:** implemented 2026-08-31, merged to `develop` (`3a32ddc`). Two changes from this design
during build:

1. Stock is sold with `InventoryService.reserve` + `confirm` in the same transaction, **not**
   `posSale` — `posSale`'s variant path calls `atomicConfirmVariantStock`, which requires a prior
   reservation (`stock_reserved >= qty`), so it cannot sell an un-reserved variant. Reserve+confirm
   is what the web checkout uses and is correct.
2. §1/§2 said "mint `orderId` up front" and reserve with `StockMovementOrigin.forOrder(orderId)`.
   That copied a latent bug in `CreateOrderUseCase`: the minted id is thrown away, `Order.create*`
   mints its own. Fixed on branch `fix/inventory-movement-order-ref` (`130319e`) — both use cases
   build the `Order` first and reserve with `order.getId()`, so `inventory_movements.reference_id`
   is the persisted order.
**Roadmap:** Fase 2, Increment F (first half — the intake pipeline; the transactional outbox is
deferred to Increment I when MercadoLibre stock-sync needs it).

## Context and motivation

The system only knows about a sale if it went through the web checkout
(`order/application/usecases/CreateOrderUseCase`). But the shop sells daily through
Instagram / Facebook / WhatsApp DMs, and those sales never enter the system. Consequences today:

- **Stock drifts.** A dress sold on Instagram still reads "in stock" in `product_variants`. A web
  buyer (or, once it exists, a MercadoLibre buyer) can then oversell it.
- **No tax document from the system.** The owner issues the boleta by hand, outside any record.
- **No revenue or analytics signal.** The PostHog C4 dashboard (`order_paid` revenue, funnels)
  only ever sees web orders. The shop's actual volume is invisible to its own tooling.
- **No customer history** for retention.

`CreateOrderUseCase` cannot serve these sales: it requires a registered `customerId` with an
entry in that customer's address book (`CustomerAddressBookService.resolveOwnedAddress`), a
shipping zone and courier drawn from `system_settings`, and a payment method enabled for the web
gateway. A social sale has a free-text buyer, an address typed from a DM, no courier of ours, and
money already received by bank transfer.

This spec adds a **second order-creation path** — `RegisterExternalSaleUseCase` — for a sale that
already happened off-platform. It is deliberately channel-agnostic: the planned POS counter sale
(`docs/pos-channel.md`, `POST /api/pos/sales` 501 stub) and a future MercadoLibre order webhook
are additional callers of the same engine, not rebuilds.

It extends the existing `order` hexagonal module. Nothing new is introduced structurally — the
`OrderRepository`, `InventoryService`, `SystemSettingsRepository`, and the domain event flow are
all reused as-is.

## Decisions from brainstorming

1. **Scope now = social-sale intake only.** POS counter sale and MercadoLibre are later callers of
   the same use case. The transactional outbox is a separate subsystem, deferred to Increment I.
2. **Buyer = free-text.** Name and contact (phone / @handle) are stored as a snapshot on the order.
   No customer account is created or looked up. The buyer does not log in and does not see the
   order in "Mi Cuenta". (A later "link to existing customer" option is possible but out of scope.)
3. **The sale is born PAID.** The owner records a social sale only once the transfer has landed, so
   the order goes straight to `PAID`. No payment-registration step, no transfer-instructions email,
   no payment-review queue entry. Payment method is `TRANSFER` or `OTHER`, chosen at intake.
4. **Delivery is chosen per sale:** `SHIPPING` (free-text address, enters the dispatch queue like a
   paid web order) or `PICKUP` (no address, does **not** create a dispatch). This introduces a
   `delivery_method` concept on the order that POS will reuse.
5. **Stock is blocking.** If the system shows insufficient stock for any line's variant, the intake
   is rejected (`409`) and nothing is written. The owner keeps stock accurate; the intake never
   drives a count negative or silently to zero.
6. **Line price is editable.** Each line preloads the product's current price but the operator can
   override it (negotiated price, combo, "precio de amiga"). The order total is the sum of the
   edited line prices. No discount code path in v1 — the editable price covers it.
7. **Boleta stays manual and separate.** The intake only creates the paid order. The existing
   `/admin/ventas` list already renders a "Sin boleta" indicator for any PAID order with no live
   `sales_documents` row (`VentasPage.tsx`), so a social sale is flagged there automatically with
   no new code. Issuing the boleta uses the flow that already exists.
8. **No buyer notification.** The owner is already in the DM conversation. Sending an automated
   "your order is confirmed" would be noise. (Can be added later behind a toggle.)

## 1. Architecture and components

### Backend — migration

**`V94__external_sale_intake.sql`** — one migration, these changes:

- `orders.delivery_method` — `VARCHAR(16) NOT NULL DEFAULT 'SHIPPING'`, backfilled to `'SHIPPING'`
  for every existing row. `CHECK (delivery_method IN ('SHIPPING','PICKUP'))`.
- `orders.buyer_name` — `VARCHAR(160) NULL` (a web order has `customer_id` instead; no backfill).
- `orders.buyer_contact` — `VARCHAR(160) NULL` (no backfill).
- `orders.external_idempotency_key` — `VARCHAR(64) NULL`, plus a partial unique index
  `CREATE UNIQUE INDEX uq_orders_external_idempotency_key ON orders (external_idempotency_key)
  WHERE external_idempotency_key IS NOT NULL`.
- Permission catalog: insert `('orders.create', 'Registrar venta', 'Registrar una venta hecha
  fuera del sitio (redes, mostrador)', 'orders', 'create')` into the modern permissions table
  (same shape as `V63__seed_modern_permissions.sql`).
- Role grants: `('ADMIN', 'orders.create', 'SYSTEM')` and `('SELLER', 'orders.create', 'SYSTEM')`
  (same shape as `V64__seed_role_permission_grants.sql`).

`notification-service`: these are **column additions only**, not renames or drops, so the
read-only `*RoEntity` mappings do not need touching — `ddl-auto: validate` checks that mapped
columns exist, not that every DB column is mapped. `ReadOnlyMappingIT` (runs the real migration
set) stays green. The plan should still run it to confirm.

### Backend — domain

- **`order/domain/enums/DeliveryMethod.java`** — `SHIPPING`, `PICKUP`.
- **`Order`** (`order/domain/model/Order.java`) gains:
  - `deliveryMethod` (`DeliveryMethod`, non-null) — every order has one; web orders are `SHIPPING`.
  - `buyerName`, `buyerContact` (String, nullable) — a web order has neither; it has `customerId`.
  - `externalIdempotencyKey` (String, nullable) — set only on the external path.
  The free-text shipping address reuses the existing `shippingAddressReference`; `notes` exists.
- **`Order.create(...)`** gains a `DeliveryMethod` parameter; the one existing caller
  (`CreateOrderUseCase`) passes `DeliveryMethod.SHIPPING`. The external path uses a separate
  factory `Order.createExternalSale(...)` or an overload (plan picks); its constraint is that it
  must **not** require `customerId`, `shippingZoneCode`, `shippingCourierId`, or
  `shippingAddressId`. **Every test that constructs an `Order` via `Order.create` needs the new
  argument** — the plan enumerates them (grep `Order.create(` under `src/test`).
- **`OrderDto` + `OrderMapper`**: expose `deliveryMethod`, `buyerName`, `buyerContact` so the
  admin sales list and any drawer can render them. `notification-service` does not read these.

- **`OrderStatus` transition:** the order is created at `PENDING_PAYMENT` (as web orders now are),
  then the use case moves it to `PAID` and publishes `OrderStatusChanged(PENDING_PAYMENT → PAID)`
  explicitly (the same way `CreateOrderUseCase` publishes `OrderCreated` explicitly). This drives
  the existing PAID-status hooks that matter here — the dispatch queue and the `order_paid`
  analytics event. Inventory is **not** handled via the PAID hook on this path (see below); the
  external path does not go through `OrderInventorySaga` or `RegisterPaymentUseCase`.

- **Inventory: one `InventoryService.posSale(...)` call per line** (not `reserve` + a later
  `confirm`). `posSale` already exists — it "decrements stock without a prior reservation step",
  atomically for variant products, and throws `DomainException("Stock insuficiente para venta POS
  de variante: …")` when the row would go short. This is exactly a born-PAID sale: no intermediate
  reserved state, blocking on insufficiency, and it was designed for the POS path this engine also
  serves. Each call passes `StockMovementOrigin.forOrder(orderId)` so the movement names the order.

### Backend — application

**`order/application/commands/RegisterExternalSaleCommand.java`** (record):

```
UUID          idempotencyKey        // client-generated, dedupes double-submit; see §3
String        buyerName             // required, trimmed, 1..160
String        buyerContact          // required, trimmed, 1..160
SalesChannel  salesChannel          // one of INSTAGRAM, FACEBOOK, WHATSAPP, MANUAL
PaymentMethod paymentMethod         // one of TRANSFER, OTHER
DeliveryMethod deliveryMethod       // SHIPPING | PICKUP
String        shippingAddress       // required iff deliveryMethod == SHIPPING; free text, 1..500
String        notes                 // optional, 0..1000
List<Line>    items                 // 1..50 lines

Line: UUID productId, String variantColor (nullable), String variantSize (nullable),
      int quantity (1..999), BigDecimal unitPrice (>= 0, 2dp)
```

**`order/application/usecases/RegisterExternalSaleUseCase.java`** — `@Transactional execute(cmd)`:

1. **Validate the command shape** (channels/methods in the allowed subsets, address present for
   `SHIPPING`, at least one line, quantities and prices in range). Fail → `DomainException`
   (→ `400`).
2. **Idempotency:** look up an order by `cmd.idempotencyKey()` via a new
   `OrderRepository.findByExternalIdempotencyKey(String)`; if one exists, return its `OrderDto`
   without creating anything.
3. **Resolve products.** For each line, `productRepository.findById` → `DomainException` (→ `404`)
   if missing. Build `OrderItem` with the **command's `unitPrice`**, not the product's price.
4. **Sell stock, blocking.** Mint `orderId` up front. For each line,
   `inventoryService.posSale(productId, qty, variantColor, variantSize,
   StockMovementOrigin.forOrder(orderId))`. If any line goes short, `posSale` throws — the
   `@Transactional` rolls everything back, and the use case maps it to `409` (see §3 on the
   exception detail). No stock ever goes negative or silently to zero.
5. **Create the order.** `salesChannel` from the command; `customerId = null`;
   `buyerName`/`buyerContact` snapshot; `deliveryMethod`; `externalIdempotencyKey =
   cmd.idempotencyKey()`; `shippingAddressReference = cmd.shippingAddress()` when `SHIPPING`, else
   `null`; `shippingZoneCode`/`shippingCourierId`/`shippingCourierName` all `null`; tax rate
   snapshot from `settings.getTax().vatRate()` (same as web); `discount = Money.zero()`.
   Total = Σ(unitPrice × qty).
6. **`order.markAsPendingPayment()`**, then move to **PAID** and `orderRepository.save`.
7. **Publish** `OrderCreated(orderId, null, now)` and `OrderStatusChanged(orderId, null,
   PENDING_PAYMENT, PAID, now)`.
8. Return `OrderMapper.toDto(saved)`.

**Analytics note:** `customerId` is `null`, so `TrackOrderAnalyticsUseCase.emit` returns early
(its guard: `if (customerId == null) return;`). A social sale therefore does **not** currently
reach PostHog. This is acceptable for v1 — decide in planning whether to (a) leave it, (b) key the
PostHog `distinct_id` off a synthetic `social:<orderId>` id so revenue still lands. Recommended:
**(b)** — small change in `emit`, and it is the whole point of wanting social revenue in the
dashboard. Flag as a plan task, not a blocker.

### Backend — dispatch

- **`CreateDispatchForPaidOrderUseCase.onOrderStatusChanged`**: after the existing
  `newStatus != PAID` guard, add `if (order.deliveryMethod() == DeliveryMethod.PICKUP) return;`.
  The use case already loads the order for its shipping snapshot, so the field is available.
  Both the in-process and Kafka listeners already delegate to this one method — no listener change.
- A `PICKUP` order stays at `PAID`. The owner can still move it to `DELIVERED` by hand from the
  admin if desired; that is existing behaviour, out of scope here.

### Backend — web

**`order/infrastructure/web/controllers/`** — extend `OrderController` or add
`ExternalSaleController` (plan picks; prefer extending `OrderController` since the resource is an
order):

- `POST /api/admin/sales/external`
- `@PreAuthorize("hasAuthority('orders.create')")` (matches how other modern-permission routes are
  guarded; confirm the exact expression against an existing `@PreAuthorize` in the codebase during
  planning).
- Request body POJO mirrors `RegisterExternalSaleCommand`.
- `201 Created` with the `OrderDto`. `Location: /api/orders/{id}`.
- Error mapping: shape validation → `400`; unknown product → `404`; insufficient stock → `409`;
  duplicate idempotency key → `200` with the existing order (idempotent replay, not an error).

### Frontend — admin

**`islands/admin/RegisterSaleDrawer.tsx`** (new) — opened from a "Registrar venta" button placed
above the table in `VentasPage.tsx`.

- **Product picker:** search products with the existing `searchProducts(...)` in `lib/api.ts`
  (backed by `GET /api/products/search`, already used elsewhere in the panel), pick a product,
  then pick a variant (color/size) if the product has variants, set quantity, set unit price
  (preloaded from the product, editable, CLP integer). Add to a line list. Lines are removable.
- **Buyer:** name, contact — two text inputs, both required.
- **Channel:** a chip/segmented control — Instagram / Facebook / WhatsApp / Manual.
- **Payment method:** Transferencia / Otro.
- **Delivery:** a toggle — "Envío" (reveals a free-text address textarea, required) or "Retiro en
  persona" (no address).
- **Notes:** optional textarea.
- **Live total:** sum of line `unitPrice × quantity`, formatted CLP, updates as lines/prices change.
- **Submit:** generates an `idempotencyKey` (`crypto.randomUUID()`) once per drawer-open, `POST`s,
  on success closes the drawer, refreshes the sales list, and (optional, plan decides) navigates to
  or highlights the new row.
- **Errors:** `409` insufficient stock → inline message naming the line; `400` → field-level or a
  form-level message; network → retryable.
- Follows the admin overlay conventions in `[[admin-overlays-need-a-portal]]` (portal, colour
  tokens, `showModal()` via ref callback) — the plan must reference that memory.

**`lib/api.ts`:** a `registerExternalSale(body, token)` function and the request/response types.

### Frontend — storefront

None. This is admin-only.

## 2. Data flow

```
Admin fills RegisterSaleDrawer
  → POST /api/admin/sales/external  (Bearer JWT, orders.create)
      → RegisterExternalSaleUseCase.execute  [@Transactional]
          → validate shape
          → idempotency check (return existing OrderDto if key seen)
          → resolve products (404 if missing)
          → inventoryService.posSale per line  (409 if short — throws, tx rolls back)  ← BLOCKING
          → Order.create (salesChannel, buyer snapshot, deliveryMethod, no customerId)
          → order.markAsPendingPayment(); order → PAID
          → orderRepository.save
          → publish OrderCreated + OrderStatusChanged(PENDING_PAYMENT→PAID)
      ← 201 OrderDto
  → drawer closes, sales list refreshes

OrderStatusChanged(→PAID) fan-out (existing listeners, unchanged except the PICKUP guard):
  → CreateDispatchForPaidOrderUseCase   → skips if deliveryMethod == PICKUP,
                                           else creates a PENDING dispatch
  → TrackOrderAnalyticsUseCase          → order_paid to PostHog
                                           (only if the null-customer analytics gap is closed)

Inventory is already settled by the posSale calls above — there is no reservation for a PAID hook
to confirm on this path.
```

## 3. Error handling and edge cases

- **Double-submit / network retry:** the client-generated `idempotencyKey` makes a replay return
  the already-created order (`200`), not a second order. The partial unique index is the backstop
  if two requests race.
- **Insufficient stock:** `posSale` throws `DomainException` and the `@Transactional` rolls
  everything back. The global exception handler currently maps `DomainException` to `400` (confirm
  in planning); the plan makes the stock case a `409` — either a dedicated
  `InsufficientStockException` subclass thrown by `posSale`'s callers, or a targeted mapping. The
  message names the product and variant so the operator knows what to fix.
- **Unknown product / variant:** `404` with the offending id.
- **Address missing for `SHIPPING`:** `400`, caught in shape validation before any write.
- **Zero-price line:** allowed (a gift, a replacement) — `unitPrice >= 0`, not `> 0`. A fully
  zero-total order is allowed for the same reason; the tax reconciliation check
  (`chk_orders_tax_reconciles`, V79) holds for a zero total.
- **Concurrent stock race:** `posSale`'s variant path is an atomic conditional `UPDATE`
  (`atomicConfirmVariantStock` — returns 0 rows when the row would go short), so two intake
  requests for the last unit cannot both succeed: one gets `updated == 0` and throws, its
  transaction rolls back, the operator retries. Social-sale volume makes a real race unlikely
  anyway.
- **`PICKUP` order later needs shipping:** out of scope. The owner edits nothing today; a future
  "convert to shipping" action is a separate request.
- **Kafka on vs off:** the two published events go through `DomainEventPublisher`. With Kafka on,
  the Kafka listeners run; with it off, the in-process ones. The `PICKUP` guard lives in the
  single use-case method both transports call, so it behaves identically either way — this is the
  rule from `[[monolith-dissolution-direction]]` / the CLAUDE.md "add behaviour to the dispatcher,
  never to a listener" note.

## 4. Testing

**Backend unit (`RegisterExternalSaleUseCaseTest`, Mockito):**

- happy path, `SHIPPING`: order created `PAID`, `salesChannel` set, buyer snapshot set,
  `deliveryMethod = SHIPPING`, address stored, `posSale` called per line, both events published.
- happy path, `PICKUP`: as above but `deliveryMethod = PICKUP`, no address, events still published.
- edited unit price: total = Σ(editedPrice × qty), not the product's list price.
- insufficient stock on one line → the exception propagates, `orderRepository.save` never called.
- unknown product → `DomainException`.
- missing address with `SHIPPING` → `DomainException`.
- idempotency: same key twice → one order, second call returns the first `OrderDto`.
- zero-price line accepted.

**Backend unit (`CreateDispatchForPaidOrderUseCaseTest`, extend existing):**

- `deliveryMethod = PICKUP` + `newStatus = PAID` → no dispatch saved.
- `deliveryMethod = SHIPPING` + `newStatus = PAID` → dispatch saved (existing test still passes).

**Backend integration (`ExternalSaleControllerIT`, Testcontainers):**

- `POST` without `orders.create` → `403`.
- `POST` with a SELLER token → `201`, order visible via `GET /api/orders/{id}`.
- insufficient stock → `409`.
- unknown product → `404`.
- duplicate idempotency key → `200`, same order id.
- `ReadOnlyMappingIT` still green after the `orders` column additions.

**Frontend (`RegisterSaleDrawer.test.tsx`, vitest + RTL):**

- add / remove lines, live total recalculates on price edit.
- delivery toggle shows/hides the address field; submit blocked without a required field.
- `409` response renders the inline stock message.
- successful submit closes the drawer and calls the list refresh.

**Frontend e2e (Playwright, local Docker):** register a social sale end to end, assert it appears
in `/admin/ventas` with "Sin boleta", assert the product's stock dropped by the sold quantity.

## 5. Out of scope (explicit)

- Transactional outbox / CDC — Increment I.
- POS counter sale (`/api/pos/sales`) — separate task, will call `RegisterExternalSaleUseCase`.
- MercadoLibre order webhook — Increment J.
- Linking a social sale to an existing customer account.
- Editing or cancelling an external sale after creation (existing order-status tooling applies;
  no new edit UI).
- Buyer notifications.
- Discount codes on the external path.
- Multi-currency — CLP only, like the rest of the system.

## 6. Open questions

None blocking. The one judgement call for the plan: whether to close the null-customer analytics
gap (§1 "Analytics note") in this increment (recommended) or leave social revenue out of PostHog
until a customer-linking feature exists.
