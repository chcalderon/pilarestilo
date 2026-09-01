# External Sale Intake Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second order-creation path — `RegisterExternalSaleUseCase` — for a sale that already happened off-platform (Instagram / Facebook / WhatsApp), recorded from the admin panel as a real paid `Order` that decrements stock.

**Architecture:** New use case in the existing `order` hexagonal module. Born `PAID`, free-text buyer snapshot, per-line editable price, blocking stock via `InventoryService.posSale`, `SHIPPING` (dispatch queue) or `PICKUP` (no dispatch — new `orders.delivery_method`). Channel-agnostic so POS and MercadoLibre later call the same engine. Publishes the same `OrderCreated` + `OrderStatusChanged(→PAID)` events the web path does, so the existing PAID hooks (dispatch, analytics) run.

**Tech Stack:** Java 25 / Spring Boot 4, Flyway, JPA/Testcontainers; Astro 5 + React islands, Zustand, vitest/RTL, Playwright.

**Spec:** `docs/superpowers/specs/2026-08-31-external-sale-intake-design.md` — the plan argues from it; executors read both.

## Global Constraints

- **CLP only.** No multi-currency anywhere in this feature.
- **The sale is born PAID.** `CREATED → PENDING_PAYMENT → PAID` via the domain `markAs*` methods; the use case publishes the events explicitly (the same way `CreateOrderUseCase` publishes `OrderCreated`). No `RegisterPaymentUseCase`, no `OrderInventorySaga`, no payment-review queue, no buyer notification.
- **Stock is blocking → HTTP 409.** `InventoryService.posSale` throws when a line would go short; nothing is written; the operator fixes stock and retries.
- **Buyer is free-text.** Name + contact stored as a snapshot on the order. `customer_id` is `null` for these orders.
- **Line price is editable.** Order total = Σ(line `unitPrice` × `quantity`). No discount codes on this path.
- **Boleta stays manual.** The intake only creates the paid order; `/admin/ventas` already flags a PAID order with no live `sales_documents` row as "Sin boleta".
- **Permission:** new `orders.create`, granted to `ADMIN` and `SELLER`. Endpoint guarded with `@PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).ORDERS_CREATE)")` — the house pattern (see `billing/.../SalesController.java`).
- **`add behaviour to the use case, never to a listener`** — the `PICKUP` dispatch skip goes in `CreateDispatchForPaidOrderUseCase`, which both the in-process and Kafka listeners already delegate to.
- Backend tests run with Testcontainers; frontend with vitest. `mvn -o test` for unit, `mvn -o verify` for the `*IT` set. Frontend: `npx vitest run`, `npx tsc --noEmit`, `npx eslint`, `npm run build`.

---

## File Structure

**Backend — new:**
- `order/domain/enums/DeliveryMethod.java`
- `order/application/commands/RegisterExternalSaleCommand.java`
- `order/application/usecases/RegisterExternalSaleUseCase.java` (returns `RegisterExternalSaleResult`)
- `order/application/usecases/RegisterExternalSaleResult.java` — `record(OrderDto dto, boolean replayed)`
- `order/infrastructure/web/requests/RegisterExternalSaleRequest.java`
- `order/infrastructure/web/controllers/ExternalSaleController.java` — mapped to `/api/admin/sales`
- `inventory/domain/InsufficientStockException.java`
- `src/main/resources/db/migration/V94__external_sale_intake.sql`
- Tests: `OrderExternalSaleTest`, `RegisterExternalSaleUseCaseTest`, `ExternalSaleControllerIT`

**Backend — modified:**
- `order/domain/model/Order.java` — fields + `createExternalSale` factory + `Order.create` gains a `DeliveryMethod` arg
- `order/infrastructure/persistence/entities/OrderEntity.java` — `customer_id` nullable, +4 columns
- `order/infrastructure/persistence/mappers/OrderEntityMapper.java` (or wherever entity↔domain lives) — new fields both directions
- `order/domain/ports/OrderRepository.java` + its JPA adapter + Spring Data interface — `findByExternalIdempotencyKey`
- `order/application/dto/OrderDto.java` + `order/application/mappers/OrderMapper.java` — expose `deliveryMethod`, `buyerName`, `buyerContact`
- `order/infrastructure/web/controllers/OrderController.java` — `getById` null-customer guard only
- `order/application/usecases/TrackOrderAnalyticsUseCase.java` — fold in null-customer orders
- `dispatch/application/usecases/CreateDispatchForPaidOrderUseCase.java` — `PICKUP` skip
- `inventory/application/InventoryService.java` — throw `InsufficientStockException` for the stock-short cases
- `shared/infrastructure/web/GlobalExceptionHandler.java` — `InsufficientStockException` → 409
- `shared/rbac/domain/PermissionRegistry.java` — `ORDERS_CREATE`
- `CreateOrderUseCase.java` — pass `DeliveryMethod.SHIPPING`
- every `Order.create(` caller under `src/test` — new arg
- `InventoryServiceConfirmTest` — assertion type
- `CreateDispatchForPaidOrderUseCaseTest`, `TrackOrderAnalyticsUseCaseTest` — new cases

**Frontend — new:**
- `src/islands/admin/RegisterSaleDrawer.tsx`
- `src/islands/admin/__tests__/RegisterSaleDrawer.test.tsx`
- `e2e/external-sale.spec.ts`

**Frontend — modified:**
- `src/lib/api.ts` — `registerExternalSale` + types; `OrderDto` type gains the new fields
- `src/islands/admin/VentasPage.tsx` — "Registrar venta" button + drawer

**Docs:** `docs/pos-channel.md`, `CLAUDE.md`, the spec's `Status:` line, memory `pending-work-queue.md`.

---

## Task 1: `DeliveryMethod` enum + `Order` external-sale support

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/order/domain/enums/DeliveryMethod.java`
- Modify: `backend/src/main/java/com/pilarestilo/order/domain/model/Order.java`
- Modify: `backend/src/main/java/com/pilarestilo/order/application/usecases/CreateOrderUseCase.java:~/Order.create(`
- Modify: every `Order.create(` caller under `backend/src/test`
- Test: `backend/src/test/java/com/pilarestilo/order/domain/model/OrderExternalSaleTest.java`

**Interfaces:**
- Produces: `DeliveryMethod { SHIPPING, PICKUP }`; `Order.createExternalSale(String buyerName, String buyerContact, List<OrderItem> items, PaymentMethod paymentMethod, DeliveryMethod deliveryMethod, String shippingAddressText, String notes, SalesChannel salesChannel, BigDecimal taxRate, String externalIdempotencyKey) -> Order` (status `CREATED`); `Order.getDeliveryMethod()`, `Order.getBuyerName()`, `Order.getBuyerContact()`, `Order.getExternalIdempotencyKey()`; `Order.create(...)` innermost overload gains `DeliveryMethod deliveryMethod` as its last argument.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/pilarestilo/order/domain/model/OrderExternalSaleTest.java`:

```java
package com.pilarestilo.order.domain.model;

import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderExternalSaleTest {

    private OrderItem line(String name, long price, int qty) {
        return new OrderItem(UUID.randomUUID(), UUID.randomUUID(), name,
                Money.of(BigDecimal.valueOf(price)), qty, null, null);
    }

    @Test
    void createExternalSale_shipping_snapshots_the_buyer_and_address_and_leaves_customer_null() {
        Order order = Order.createExternalSale(
                "Javiera Rojas", "+56 9 1111 2222",
                List.of(line("Vestido", 19990, 2)),
                PaymentMethod.TRANSFER, DeliveryMethod.SHIPPING,
                "Av. Siempre Viva 742, Providencia, RM",
                "por IG", SalesChannel.INSTAGRAM, new BigDecimal("19.00"), "idem-1");

        assertThat(order.getCustomerId()).isNull();
        assertThat(order.getBuyerName()).isEqualTo("Javiera Rojas");
        assertThat(order.getBuyerContact()).isEqualTo("+56 9 1111 2222");
        assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.SHIPPING);
        assertThat(order.getShippingAddressReference()).isEqualTo("Av. Siempre Viva 742, Providencia, RM");
        assertThat(order.getSalesChannel()).isEqualTo(SalesChannel.INSTAGRAM);
        assertThat(order.getExternalIdempotencyKey()).isEqualTo("idem-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount().amount()).isEqualByComparingTo("39980");
    }

    @Test
    void createExternalSale_pickup_has_no_address() {
        Order order = Order.createExternalSale(
                "Ana", "@ana", List.of(line("Aros", 8000, 1)),
                PaymentMethod.OTHER, DeliveryMethod.PICKUP,
                null, null, SalesChannel.WHATSAPP, new BigDecimal("19.00"), "idem-2");

        assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(order.getShippingAddressReference()).isNull();
    }

    @Test
    void createExternalSale_rejects_blank_buyer_name() {
        assertThatThrownBy(() -> Order.createExternalSale(
                "  ", "@x", List.of(line("Aros", 8000, 1)),
                PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                SalesChannel.WHATSAPP, new BigDecimal("19.00"), "k"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void createExternalSale_rejects_shipping_without_an_address() {
        assertThatThrownBy(() -> Order.createExternalSale(
                "Ana", "@ana", List.of(line("Aros", 8000, 1)),
                PaymentMethod.OTHER, DeliveryMethod.SHIPPING, "   ", null,
                SalesChannel.WHATSAPP, new BigDecimal("19.00"), "k"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void createExternalSale_rejects_empty_items() {
        assertThatThrownBy(() -> Order.createExternalSale(
                "Ana", "@ana", List.of(),
                PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                SalesChannel.WHATSAPP, new BigDecimal("19.00"), "k"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void regular_create_still_works_and_defaults_delivery_to_shipping() {
        Order order = Order.create(
                UUID.randomUUID(), List.of(line("Vestido", 19990, 1)), Money.zero(),
                PaymentMethod.TRANSFER, "RM", "starken", "Starken", "PREPAID",
                UUID.randomUUID(), "Depto 1", "web", SalesChannel.ECOMMERCE,
                new BigDecimal("19.00"), DeliveryMethod.SHIPPING);
        assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.SHIPPING);
    }
}
```

- [ ] **Step 2: Run it, verify it fails to compile** (`DeliveryMethod`, `createExternalSale`, the getters, and the new `create` arg do not exist).

Run: `cd backend && mvn -o test-compile`
Expected: compile errors on the symbols above.

- [ ] **Step 3: Create `DeliveryMethod`**

```java
package com.pilarestilo.order.domain.enums;

public enum DeliveryMethod {
    /** The garment ships to the buyer. Web orders and shipped external sales. */
    SHIPPING,
    /** The buyer collects it in person. No dispatch is created. */
    PICKUP
}
```

- [ ] **Step 4: Add the fields and factory to `Order`**

In `Order.java`, add private fields near the other shipping fields:

```java
    private DeliveryMethod deliveryMethod = DeliveryMethod.SHIPPING;
    private String buyerName;
    private String buyerContact;
    private String externalIdempotencyKey;
```

Add a `DeliveryMethod deliveryMethod` parameter to the innermost `create(...)` overload (the one
taking `SalesChannel salesChannel, BigDecimal taxRate`), set `order.deliveryMethod = deliveryMethod
!= null ? deliveryMethod : DeliveryMethod.SHIPPING;`, and make the two shorter `create` overloads
pass `DeliveryMethod.SHIPPING` when they delegate.

Add the external-sale factory next to `create`:

```java
    /**
     * A sale that already happened off-platform (Instagram / Facebook / WhatsApp, and later POS /
     * MercadoLibre). No registered customer, no courier or zone of ours, a free-text address (or
     * none, for pickup). Payment has already been received; the caller moves this straight to PAID.
     */
    public static Order createExternalSale(String buyerName, String buyerContact,
                                           List<OrderItem> items, PaymentMethod paymentMethod,
                                           DeliveryMethod deliveryMethod, String shippingAddressText,
                                           String notes, SalesChannel salesChannel, BigDecimal taxRate,
                                           String externalIdempotencyKey) {
        if (isBlank(buyerName)) {
            throw new DomainException("Buyer name is required");
        }
        if (isBlank(buyerContact)) {
            throw new DomainException("Buyer contact is required");
        }
        if (items == null || items.isEmpty()) {
            throw new DomainException("Order must have at least one item");
        }
        if (paymentMethod == null) {
            throw new DomainException("Payment method cannot be null");
        }
        if (deliveryMethod == null) {
            throw new DomainException("Delivery method cannot be null");
        }
        if (deliveryMethod == DeliveryMethod.SHIPPING && isBlank(shippingAddressText)) {
            throw new DomainException("A shipping address is required for a shipped sale");
        }

        Money subtotal = items.stream()
                .map(item -> item.getUnitPrice().multiply(item.getQuantity()))
                .reduce(Money.zero(), Money::add);

        Order order = new Order();
        order.id = UUID.randomUUID();
        order.publicReference = OrderReference.forOrderId(order.id);
        order.customerId = null;
        order.items = List.copyOf(items);
        order.subtotal = subtotal;
        order.discountAmount = Money.zero();
        order.totalAmount = subtotal;
        order.applyTaxRate(subtotal, taxRate);
        order.paymentMethod = paymentMethod;
        order.shippingZoneCode = null;
        order.shippingCourierId = null;
        order.shippingCourierName = null;
        order.shippingPaymentMode = null;
        order.shippingAddressId = null;
        order.shippingAddressReference = deliveryMethod == DeliveryMethod.SHIPPING
                ? shippingAddressText.trim() : null;
        order.notes = notes;
        order.salesChannel = salesChannel != null ? salesChannel : SalesChannel.MANUAL;
        order.deliveryMethod = deliveryMethod;
        order.buyerName = buyerName.trim();
        order.buyerContact = buyerContact.trim();
        order.externalIdempotencyKey = externalIdempotencyKey;
        order.status = OrderStatus.CREATED;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
```

Add getters: `getDeliveryMethod()`, `getBuyerName()`, `getBuyerContact()`, `getExternalIdempotencyKey()`.

- [ ] **Step 5: Fix `CreateOrderUseCase` and every test caller**

Run: `cd backend && grep -rn "Order.create(" src/main src/test`
For each call to the innermost overload (with `salesChannel`/`taxRate`), append `,
DeliveryMethod.SHIPPING`. `CreateOrderUseCase` passes `DeliveryMethod.SHIPPING`. The shorter
overloads need no change (they now delegate with the default).

- [ ] **Step 6: Run the test + the order module tests**

Run: `cd backend && mvn -o test -Dtest='OrderExternalSaleTest,CreateOrderUseCaseTest,OrderTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/order backend/src/test/java/com/pilarestilo/order
git commit -m "feat(order): DeliveryMethod + Order.createExternalSale factory"
```

---

## Task 2: Persistence — V94 migration, `OrderEntity`, mapper, idempotency lookup

**Files:**
- Create: `backend/src/main/resources/db/migration/V94__external_sale_intake.sql`
- Modify: `backend/src/main/java/com/pilarestilo/order/infrastructure/persistence/entities/OrderEntity.java`
- Modify: the order entity↔domain mapper (find it: `grep -rln "OrderEntity" backend/src/main/java/com/pilarestilo/order/infrastructure/persistence` — likely `repositories/OrderRepositoryJpaAdapter.java` or a `mappers/OrderEntityMapper.java`)
- Modify: `backend/src/main/java/com/pilarestilo/order/domain/ports/OrderRepository.java`
- Modify: the JPA adapter + its Spring Data `JpaRepository` interface
- Modify: `backend/src/main/java/com/pilarestilo/order/application/dto/OrderDto.java`
- Modify: `backend/src/main/java/com/pilarestilo/order/application/mappers/OrderMapper.java`
- Test: `backend/src/test/java/com/pilarestilo/order/infrastructure/persistence/OrderRepositoryExternalSaleIT.java`

**Interfaces:**
- Consumes: `Order.createExternalSale` (Task 1).
- Produces: `OrderRepository.findByExternalIdempotencyKey(String) -> Optional<Order>`; `OrderDto` gains `DeliveryMethod deliveryMethod`, `String buyerName`, `String buyerContact` (appended to the record, after `salesChannel`, before `status` — pick a position and keep it consistent; **appending at the very end** is safest for the many `new OrderDto(...)` test callers — put them last, after `updatedAt`).

- [ ] **Step 1: Write the failing IT**

`OrderRepositoryExternalSaleIT.java` (mirror an existing repository IT's Testcontainers setup —
find one with `grep -rln "AbstractSharedStackIT\|@SpringBootTest.*Test.*postgres" backend/src/test`):

```java
// package + imports per the sibling IT
class OrderRepositoryExternalSaleIT extends AbstractSharedStackIT {   // or the repo's base class

    @Autowired OrderRepository orderRepository;

    @Test
    void round_trips_an_external_sale_with_no_customer() {
        Order order = Order.createExternalSale(
                "Javiera", "+56911112222",
                List.of(new OrderItem(UUID.randomUUID(), aRealProductId(), "Vestido",
                        Money.of(new BigDecimal("19990")), 1, null, null)),
                PaymentMethod.TRANSFER, DeliveryMethod.PICKUP, null, "por IG",
                SalesChannel.INSTAGRAM, new BigDecimal("19.00"), "idem-abc");
        order.markAsPendingPayment();
        order.markAsPaid();

        Order saved = orderRepository.save(order);
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getCustomerId()).isNull();
        assertThat(reloaded.getBuyerName()).isEqualTo("Javiera");
        assertThat(reloaded.getBuyerContact()).isEqualTo("+56911112222");
        assertThat(reloaded.getDeliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(reloaded.getExternalIdempotencyKey()).isEqualTo("idem-abc");
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void finds_by_external_idempotency_key() {
        // save one as above with key "idem-xyz"
        assertThat(orderRepository.findByExternalIdempotencyKey("idem-xyz")).isPresent();
        assertThat(orderRepository.findByExternalIdempotencyKey("nope")).isEmpty();
    }
}
```

(`aRealProductId()` — seed or reuse a product the migration set provides; copy the pattern from a
sibling order IT.)

- [ ] **Step 2: Run it, verify it fails** (column does not exist / mapper NPE / method missing).

Run: `cd backend && mvn -o verify -Dit.test=OrderRepositoryExternalSaleIT -DfailIfNoTests=false`
Expected: FAIL.

- [ ] **Step 3: Write `V94__external_sale_intake.sql`**

```sql
-- External sale intake (Fase 2, Increment F). A sale made off-platform (Instagram / Facebook /
-- WhatsApp, later POS / MercadoLibre) becomes a real paid order with no registered customer.

-- customer_id is NOT NULL from V1. An external sale has no account behind it.
ALTER TABLE orders ALTER COLUMN customer_id DROP NOT NULL;

ALTER TABLE orders
    ADD COLUMN delivery_method VARCHAR(16) NOT NULL DEFAULT 'SHIPPING',
    ADD COLUMN buyer_name VARCHAR(160),
    ADD COLUMN buyer_contact VARCHAR(160),
    ADD COLUMN external_idempotency_key VARCHAR(64);

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_delivery_method CHECK (delivery_method IN ('SHIPPING', 'PICKUP'));

-- One order per client-supplied key. NULL for web orders, so a partial index.
CREATE UNIQUE INDEX uq_orders_external_idempotency_key
    ON orders (external_idempotency_key)
    WHERE external_idempotency_key IS NOT NULL;

INSERT INTO permissions (code, name, description, module, category) VALUES
    ('orders.create', 'Registrar venta',
     'Registrar una venta hecha fuera del sitio (redes, mostrador)', 'orders', 'write')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission_grants (role, permission_code, source) VALUES
    ('ADMIN', 'orders.create', 'SYSTEM'),
    ('SELLER', 'orders.create', 'SYSTEM')
ON CONFLICT DO NOTHING;
```

(Check the exact `role_permission_grants` column list against `V64` vs `V81` — V64 has a `source`
column, V81 omits it and relies on a default. Use whichever the current schema requires; run
`\d role_permission_grants` mentally from the latest migration that altered it.)

- [ ] **Step 4: Update `OrderEntity`**

- `@Column(name = "customer_id")` — drop `nullable = false`.
- Add:

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 16)
    private DeliveryMethod deliveryMethod = DeliveryMethod.SHIPPING;

    @Column(name = "buyer_name", length = 160)
    private String buyerName;

    @Column(name = "buyer_contact", length = 160)
    private String buyerContact;

    @Column(name = "external_idempotency_key", length = 64)
    private String externalIdempotencyKey;
```

Add getters/setters to match the entity's existing style (Lombok `@Getter/@Setter` or explicit).

- [ ] **Step 5: Update the entity↔domain mapper**

Map the four new fields both directions. For the domain→entity direction of an external sale,
`customerId` will be `null` — make sure the mapper does not NPE. For entity→domain, the domain
`Order` has no public setters; check how the mapper currently reconstructs an `Order` (likely
reflection, a package-private constructor, or a builder) and extend that path for the new fields.

- [ ] **Step 6: Add `findByExternalIdempotencyKey`**

- `OrderRepository` (port): `Optional<Order> findByExternalIdempotencyKey(String key);`
- Spring Data `JpaRepository<OrderEntity, UUID>` interface: `Optional<OrderEntity>
  findByExternalIdempotencyKey(String externalIdempotencyKey);`
- JPA adapter: delegate + map.

- [ ] **Step 7: Update `OrderDto` + `OrderMapper`**

`OrderDto` — append three components after `updatedAt`:

```java
        Instant updatedAt,
        DeliveryMethod deliveryMethod,
        String buyerName,
        String buyerContact
```

`OrderMapper.toDto` — append `order.getDeliveryMethod(), order.getBuyerName(),
order.getBuyerContact()`. Then fix every other `new OrderDto(...)` (tests, other mappers): run
`grep -rn "new OrderDto(" backend/src` and append `, <deliveryMethod>, null, null` (or real values
in tests that assert on them).

- [ ] **Step 8: Run the IT + the order persistence + `ReadOnlyMappingIT`**

Run: `cd backend && mvn -o verify -Dit.test='OrderRepositoryExternalSaleIT,ReadOnlyMappingIT,*OrderRepository*IT' -DfailIfNoTests=false`
Expected: PASS. `ReadOnlyMappingIT` proves the notification-service RO entities still validate
against the migrated schema (they don't map the new columns, and that is fine).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/db/migration/V94__external_sale_intake.sql \
  backend/src/main/java/com/pilarestilo/order backend/src/test/java/com/pilarestilo/order
git commit -m "feat(order): V94 external-sale columns, entity, mapper, idempotency lookup"
```

---

## Task 3: `InsufficientStockException` + HTTP 409

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/inventory/domain/InsufficientStockException.java`
- Modify: `backend/src/main/java/com/pilarestilo/inventory/application/InventoryService.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/infrastructure/web/GlobalExceptionHandler.java`
- Modify: `backend/src/test/java/com/pilarestilo/inventory/application/InventoryServiceConfirmTest.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/infrastructure/web/GlobalExceptionHandlerStockTest.java`

**Interfaces:**
- Produces: `InsufficientStockException extends DomainException`; the HTTP layer maps it to `409 CONFLICT`. `InventoryService.posSale` / `reserve` / `confirm` throw it (subclass of `DomainException`, so existing `DomainException` catchers are unaffected) for the "row would go short" cases only — "product not found" / "variant not found" stay plain `DomainException`.

- [ ] **Step 1: Write the failing handler test**

```java
package com.pilarestilo.shared.infrastructure.web;

import com.pilarestilo.inventory.domain.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerStockTest {

    @Test
    void insufficient_stock_maps_to_409() {
        ProblemDetail pd = new GlobalExceptionHandler()
                .handleInsufficientStock(new InsufficientStockException("Stock insuficiente para Rojo / M"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).contains("Stock insuficiente");
    }
}
```

- [ ] **Step 2: Run it, verify it fails to compile** (`InsufficientStockException` and
`handleInsufficientStock` do not exist).

- [ ] **Step 3: Create the exception**

```java
package com.pilarestilo.inventory.domain;

import com.pilarestilo.shared.domain.DomainException;

/**
 * A stock line would go short. A subclass of {@link DomainException} so existing callers that
 * catch the parent keep working, but the HTTP layer maps it to 409 rather than 400 — the request
 * was well formed, it just lost a race with the shelf.
 */
public class InsufficientStockException extends DomainException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Throw it from `InventoryService`**

In `posSale`, `reserveLocal`, `confirmLocal`, change the `if (updated == 0) throw new
DomainException("Stock insuficiente …")` / `"Stock reservado insuficiente …"` lines to
`throw new InsufficientStockException(...)`. Leave `throw new DomainException(PRODUCT_NOT_FOUND_PREFIX + ...)`
and `"Variante no encontrada …"` as plain `DomainException`.

- [ ] **Step 5: Add the handler**

In `GlobalExceptionHandler`, above `handleDomainException` (more specific first):

```java
    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Insufficient stock");
        return problem;
    }
```

- [ ] **Step 6: Update `InventoryServiceConfirmTest`**

The `posSale_withVariant_throwsDomainException_whenNoRowsUpdated` assertion: if it uses
`isInstanceOf(DomainException.class)` it still passes (subclass). If `isExactlyInstanceOf`, change
to `InsufficientStockException`. Same for any `reserve`/`confirm` insufficiency assertions.

- [ ] **Step 7: Run inventory + handler tests**

Run: `cd backend && mvn -o test -Dtest='InventoryService*Test,GlobalExceptionHandler*Test'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/inventory backend/src/main/java/com/pilarestilo/shared/infrastructure/web \
  backend/src/test/java/com/pilarestilo/inventory backend/src/test/java/com/pilarestilo/shared
git commit -m "feat(inventory): InsufficientStockException maps to HTTP 409"
```

---

## Task 4: Analytics — fold in null-customer (external) orders

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/order/application/usecases/TrackOrderAnalyticsUseCase.java`
- Modify: `backend/src/test/java/com/pilarestilo/order/application/usecases/TrackOrderAnalyticsUseCaseTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `order_created` / `order_paid` now fire for orders with `customerId == null`, keyed to
  `distinct_id = "order:" + orderId`, with a `customer_type` property (`"registered"` |
  `"external"`).

- [ ] **Step 1: Add the failing test cases to `TrackOrderAnalyticsUseCaseTest`**

```java
    @Test
    void an_external_sale_with_no_customer_is_tracked_keyed_to_the_order() {
        UUID orderId = UUID.randomUUID();
        when(getOrderUseCase.execute(orderId)).thenReturn(order(orderId, OrderStatus.PENDING_PAYMENT));

        useCase.onOrderCreated(new OrderCreated(orderId, null, Instant.now()));

        ArgumentCaptor<Map<String, Object>> props = captor();
        verify(analyticsTracker).track(eq("order_created"), eq("order:" + orderId), props.capture());
        assertThat(props.getValue()).containsEntry("customer_type", "external");
    }
```

Update `an_event_with_no_customer_is_dropped` — that test asserted the old behaviour and must be
**deleted** (it is the behaviour we are changing), replaced by the case above. Keep
`a_status_change_that_is_not_to_paid_tracks_nothing` (still valid — the `newStatus != PAID` guard
is untouched).

Also add `customer_type` assertions to the two existing happy-path tests:
`.containsEntry("customer_type", "registered")`.

- [ ] **Step 2: Run it, verify it fails** (`order:` key not used; `customer_type` absent; the
deleted test's old expectation).

Run: `cd backend && mvn -o test -Dtest=TrackOrderAnalyticsUseCaseTest`

- [ ] **Step 3: Update `emit`**

```java
    private void emit(String eventName, UUID orderId, UUID customerId, Map<String, Object> extra) {
        String distinctId = customerId != null ? customerId.toString() : "order:" + orderId;
        Map<String, Object> properties = new HashMap<>(extra);
        properties.put("order_id", orderId.toString());
        properties.put("customer_type", customerId != null ? "registered" : "external");
        try {
            OrderDto order = getOrderUseCase.execute(orderId);
            properties.putAll(orderProperties(order));
        } catch (RuntimeException ex) {
            log.warn("analytics event {} for order {} sent without order detail: {}",
                    eventName, orderId, ex.getMessage());
        }
        analyticsTracker.track(eventName, distinctId, properties);
    }
```

- [ ] **Step 4: Run the test**

Run: `cd backend && mvn -o test -Dtest=TrackOrderAnalyticsUseCaseTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/order/application/usecases/TrackOrderAnalyticsUseCase.java \
  backend/src/test/java/com/pilarestilo/order/application/usecases/TrackOrderAnalyticsUseCaseTest.java
git commit -m "feat(analytics): fold external (no-customer) sales into order_created/order_paid"
```

---

## Task 5: `RegisterExternalSaleCommand` + `RegisterExternalSaleUseCase`

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/order/application/commands/RegisterExternalSaleCommand.java`
- Create: `backend/src/main/java/com/pilarestilo/order/application/usecases/RegisterExternalSaleUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/rbac/domain/PermissionRegistry.java`
- Test: `backend/src/test/java/com/pilarestilo/order/application/usecases/RegisterExternalSaleUseCaseTest.java`

**Interfaces:**
- Consumes: `Order.createExternalSale` (T1), `OrderRepository.findByExternalIdempotencyKey` (T2),
  `InsufficientStockException` (T3), `InventoryService.posSale`, `SystemSettingsRepository.get()`,
  `ProductRepository.findById`, `DomainEventPublisher.publish`, `OrderMapper.toDto`.
- Produces: `RegisterExternalSaleUseCase.execute(RegisterExternalSaleCommand) ->
  RegisterExternalSaleResult` where `RegisterExternalSaleResult` is
  `record(OrderDto dto, boolean replayed)` — `replayed == true` when an idempotency-key match
  returned an existing order. `PermissionRegistry.ORDERS_CREATE`.

- [ ] **Step 1: Write the command**

```java
package com.pilarestilo.order.application.commands;

import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RegisterExternalSaleCommand(
        String idempotencyKey,
        String buyerName,
        String buyerContact,
        SalesChannel salesChannel,
        PaymentMethod paymentMethod,
        DeliveryMethod deliveryMethod,
        String shippingAddress,   // required iff deliveryMethod == SHIPPING
        String notes,
        List<Line> items
) {
    public record Line(UUID productId, String variantColor, String variantSize,
                       int quantity, BigDecimal unitPrice) {}
}
```

- [ ] **Step 2: Write the failing test**

`RegisterExternalSaleUseCaseTest.java` (`@ExtendWith(MockitoExtension.class)`):

```java
    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock InventoryService inventoryService;
    @Mock DomainEventPublisher eventPublisher;
    @Mock SystemSettingsRepository systemSettingsRepository;

    RegisterExternalSaleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterExternalSaleUseCase(orderRepository, productRepository,
                inventoryService, eventPublisher, systemSettingsRepository);
        SystemSettings s = mock(SystemSettings.class, RETURNS_DEEP_STUBS);
        when(s.getTax().vatRate()).thenReturn(new BigDecimal("19.00"));
        when(systemSettingsRepository.get()).thenReturn(s);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Product product(UUID id, long price) {
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(id);
        when(p.getName()).thenReturn("Vestido");
        when(p.getPrice()).thenReturn(Money.of(BigDecimal.valueOf(price)));
        return p;
    }

    @Test
    void shipping_sale_creates_a_paid_order_and_sells_stock_and_publishes_both_events() {
        UUID pid = UUID.randomUUID();
        when(productRepository.findById(pid)).thenReturn(Optional.of(product(pid, 19990)));
        var cmd = new RegisterExternalSaleCommand("k1", "Javiera", "+56911112222",
                SalesChannel.INSTAGRAM, PaymentMethod.TRANSFER, DeliveryMethod.SHIPPING,
                "Av. Siempre Viva 742", "por IG",
                List.of(new RegisterExternalSaleCommand.Line(pid, "Rojo", "M", 2, new BigDecimal("15000"))));

        OrderDto dto = useCase.execute(cmd).dto();

        assertThat(dto.status()).isEqualTo(OrderStatus.PAID);
        assertThat(dto.salesChannel()).isEqualTo(SalesChannel.INSTAGRAM);
        assertThat(dto.deliveryMethod()).isEqualTo(DeliveryMethod.SHIPPING);
        assertThat(dto.buyerName()).isEqualTo("Javiera");
        assertThat(dto.totalAmount().amount()).isEqualByComparingTo("30000"); // edited price, not 19990
        verify(inventoryService).posSale(eq(pid), eq(2), eq("Rojo"), eq("M"), any());
        verify(eventPublisher).publish(isA(OrderCreated.class));
        verify(eventPublisher).publish(isA(OrderStatusChanged.class));
    }

    @Test
    void pickup_sale_has_no_address_and_still_publishes_the_paid_event() {
        UUID pid = UUID.randomUUID();
        when(productRepository.findById(pid)).thenReturn(Optional.of(product(pid, 8000)));
        var cmd = new RegisterExternalSaleCommand("k2", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                List.of(new RegisterExternalSaleCommand.Line(pid, null, null, 1, new BigDecimal("8000"))));

        OrderDto dto = useCase.execute(cmd).dto();

        assertThat(dto.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(dto.shippingAddressReference()).isNull();
        verify(eventPublisher, times(2)).publish(any());
    }

    @Test
    void insufficient_stock_rolls_back_nothing_is_saved() {
        UUID pid = UUID.randomUUID();
        when(productRepository.findById(pid)).thenReturn(Optional.of(product(pid, 8000)));
        doThrow(new InsufficientStockException("Stock insuficiente para Rojo / M"))
                .when(inventoryService).posSale(eq(pid), anyInt(), any(), any(), any());
        var cmd = new RegisterExternalSaleCommand("k3", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                List.of(new RegisterExternalSaleCommand.Line(pid, "Rojo", "M", 5, new BigDecimal("8000"))));

        assertThatThrownBy(() -> useCase.execute(cmd)).isInstanceOf(InsufficientStockException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void unknown_product_throws() {
        UUID pid = UUID.randomUUID();
        when(productRepository.findById(pid)).thenReturn(Optional.empty());
        var cmd = new RegisterExternalSaleCommand("k4", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                List.of(new RegisterExternalSaleCommand.Line(pid, null, null, 1, new BigDecimal("8000"))));
        assertThatThrownBy(() -> useCase.execute(cmd)).isInstanceOf(DomainException.class);
        verify(inventoryService, never()).posSale(any(), anyInt(), any(), any(), any());
    }

    @Test
    void missing_address_for_a_shipping_sale_throws_before_any_write() {
        UUID pid = UUID.randomUUID();
        var cmd = new RegisterExternalSaleCommand("k5", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.SHIPPING, "  ", null,
                List.of(new RegisterExternalSaleCommand.Line(pid, null, null, 1, new BigDecimal("8000"))));
        assertThatThrownBy(() -> useCase.execute(cmd)).isInstanceOf(DomainException.class);
        verifyNoInteractions(inventoryService, orderRepository);
    }

    @Test
    void a_repeated_idempotency_key_returns_the_first_order_and_creates_nothing() {
        Order existing = Order.createExternalSale("Ana", "@ana",
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "x",
                        Money.of(BigDecimal.TEN), 1, null, null)),
                PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                SalesChannel.WHATSAPP, new BigDecimal("19.00"), "dup");
        when(orderRepository.findByExternalIdempotencyKey("dup")).thenReturn(Optional.of(existing));
        var cmd = new RegisterExternalSaleCommand("dup", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                List.of(new RegisterExternalSaleCommand.Line(UUID.randomUUID(), null, null, 1, BigDecimal.TEN)));

        var result = useCase.execute(cmd);

        assertThat(result.replayed()).isTrue();
        assertThat(result.dto().id()).isEqualTo(existing.getId());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void a_zero_price_line_is_accepted() {
        UUID pid = UUID.randomUUID();
        when(productRepository.findById(pid)).thenReturn(Optional.of(product(pid, 8000)));
        var cmd = new RegisterExternalSaleCommand("k6", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                List.of(new RegisterExternalSaleCommand.Line(pid, null, null, 1, BigDecimal.ZERO)));
        OrderDto dto = useCase.execute(cmd).dto();
        assertThat(dto.totalAmount().amount()).isEqualByComparingTo("0");
    }
```

- [ ] **Step 3: Run it, verify it fails** (`RegisterExternalSaleUseCase` does not exist).

- [ ] **Step 4: Write `RegisterExternalSaleResult`**

```java
package com.pilarestilo.order.application.usecases;

import com.pilarestilo.order.application.dto.OrderDto;

/** @param replayed true when an idempotency-key match returned an existing order. */
public record RegisterExternalSaleResult(OrderDto dto, boolean replayed) {}
```

- [ ] **Step 5: Write the use case**

```java
package com.pilarestilo.order.application.usecases;

import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.inventory.domain.model.StockMovementOrigin;
import com.pilarestilo.order.application.commands.RegisterExternalSaleCommand;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.mappers.OrderMapper;
import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RegisterExternalSaleUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final DomainEventPublisher eventPublisher;
    private final SystemSettingsRepository systemSettingsRepository;

    public RegisterExternalSaleUseCase(OrderRepository orderRepository,
                                       ProductRepository productRepository,
                                       InventoryService inventoryService,
                                       DomainEventPublisher eventPublisher,
                                       SystemSettingsRepository systemSettingsRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
        this.systemSettingsRepository = systemSettingsRepository;
    }

    @Transactional
    public RegisterExternalSaleResult execute(RegisterExternalSaleCommand cmd) {
        validate(cmd);

        if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) {
            var existing = orderRepository.findByExternalIdempotencyKey(cmd.idempotencyKey());
            if (existing.isPresent()) {
                return new RegisterExternalSaleResult(OrderMapper.toDto(existing.get()), true);
            }
        }

        UUID orderId = UUID.randomUUID();
        List<OrderItem> items = new ArrayList<>();
        for (RegisterExternalSaleCommand.Line line : cmd.items()) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new DomainException("Product not found: " + line.productId()));
            items.add(new OrderItem(
                    UUID.randomUUID(), product.getId(), product.getName(),
                    Money.of(line.unitPrice()), line.quantity(),
                    line.variantColor(), line.variantSize()));
        }

        // Sell stock first — blocking. posSale throws InsufficientStockException (→ 409) if a line
        // would go short, and @Transactional rolls the whole thing back.
        for (RegisterExternalSaleCommand.Line line : cmd.items()) {
            inventoryService.posSale(line.productId(), line.quantity(),
                    line.variantColor(), line.variantSize(), StockMovementOrigin.forOrder(orderId));
        }

        BigDecimal vatRate = systemSettingsRepository.get().getTax().vatRate();
        Order order = Order.createExternalSale(
                cmd.buyerName(), cmd.buyerContact(), items, cmd.paymentMethod(),
                cmd.deliveryMethod(), cmd.shippingAddress(), cmd.notes(),
                cmd.salesChannel(), vatRate, cmd.idempotencyKey());
        order.markAsPendingPayment();
        order.markAsPaid();

        Order saved = orderRepository.save(order);

        eventPublisher.publish(new OrderCreated(saved.getId(), null, Instant.now()));
        eventPublisher.publish(new OrderStatusChanged(
                saved.getId(), null, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, Instant.now()));

        return new RegisterExternalSaleResult(OrderMapper.toDto(saved), false);
    }

    private void validate(RegisterExternalSaleCommand cmd) {
        if (cmd.items() == null || cmd.items().isEmpty()) {
            throw new DomainException("Al menos un producto es obligatorio");
        }
        if (cmd.items().size() > 50) {
            throw new DomainException("Demasiadas lineas");
        }
        if (isBlank(cmd.buyerName()) || isBlank(cmd.buyerContact())) {
            throw new DomainException("Nombre y contacto del comprador son obligatorios");
        }
        if (cmd.salesChannel() == null || cmd.paymentMethod() == null || cmd.deliveryMethod() == null) {
            throw new DomainException("Canal, metodo de pago y entrega son obligatorios");
        }
        if (cmd.deliveryMethod() == DeliveryMethod.SHIPPING && isBlank(cmd.shippingAddress())) {
            throw new DomainException("La direccion es obligatoria para un envio");
        }
        for (RegisterExternalSaleCommand.Line line : cmd.items()) {
            if (line.productId() == null) {
                throw new DomainException("Falta el producto en una linea");
            }
            if (line.quantity() < 1 || line.quantity() > 999) {
                throw new DomainException("Cantidad invalida");
            }
            if (line.unitPrice() == null || line.unitPrice().signum() < 0) {
                throw new DomainException("Precio invalido");
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
```

**Channel / payment-method allow-lists:** the command carries the enums directly, so an invalid
value fails at deserialization (400) before the use case. The use case does not need to re-check
which `SalesChannel` / `PaymentMethod` values are "external" — the web request POJO (Task 7)
restricts the accepted strings.

- [ ] **Step 6: Add `PermissionRegistry.ORDERS_CREATE`**

```java
    public static final PermissionDefinition ORDERS_CREATE = define(
            "orders.create", "Registrar venta",
            "Registrar una venta hecha fuera del sitio (redes, mostrador)",
            PermissionModule.ORDERS, PermissionCategory.WRITE);
```

Add `ORDERS_CREATE` to the aggregate list (the `ORDERS_READ, ORDERS_UPDATE` line).

- [ ] **Step 7: Run the test**

Run: `cd backend && mvn -o test -Dtest=RegisterExternalSaleUseCaseTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/order/application \
  backend/src/main/java/com/pilarestilo/shared/rbac/domain/PermissionRegistry.java \
  backend/src/test/java/com/pilarestilo/order/application/usecases/RegisterExternalSaleUseCaseTest.java
git commit -m "feat(order): RegisterExternalSaleUseCase — born-PAID off-platform sale"
```

---

## Task 6: Dispatch — skip `PICKUP` orders

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/dispatch/application/usecases/CreateDispatchForPaidOrderUseCase.java`
- Modify: `backend/src/test/java/com/pilarestilo/dispatch/application/usecases/CreateDispatchForPaidOrderUseCaseTest.java`

**Interfaces:**
- Consumes: `OrderDto.deliveryMethod()` (Task 2).
- Produces: no new interface — a `PICKUP` order produces no dispatch.

- [ ] **Step 1: Add the failing test cases**

```java
    @Test
    void does_not_create_a_dispatch_for_a_pickup_order() {
        UUID orderId = UUID.randomUUID();
        when(dispatchRepository.existsByOrderId(orderId)).thenReturn(false);
        when(getOrderUseCase.execute(orderId)).thenReturn(orderWithDelivery(orderId, DeliveryMethod.PICKUP));

        useCase.onOrderStatusChanged(new OrderStatusChanged(
                orderId, null, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, Instant.now()));

        verify(dispatchRepository, never()).save(any());
    }

    @Test
    void still_creates_a_dispatch_for_a_shipping_order() {
        UUID orderId = UUID.randomUUID();
        when(dispatchRepository.existsByOrderId(orderId)).thenReturn(false);
        when(getOrderUseCase.execute(orderId)).thenReturn(orderWithDelivery(orderId, DeliveryMethod.SHIPPING));

        useCase.onOrderStatusChanged(new OrderStatusChanged(
                orderId, UUID.randomUUID(), OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, Instant.now()));

        verify(dispatchRepository).save(any());
    }
```

(`orderWithDelivery` — the existing `order(orderId)` helper with the `deliveryMethod` arg set; add
the arg to the helper and to the existing tests' expectations — they pass `SHIPPING`.)

- [ ] **Step 2: Run it, verify the pickup test fails** (a dispatch is still saved).

- [ ] **Step 3: Add the guard**

In `onOrderStatusChanged`, after `if (dispatchRepository.existsByOrderId(event.orderId())) return;`,
load the order once and check delivery:

```java
        OrderDto order;
        try {
            order = getOrderUseCase.execute(event.orderId());
        } catch (RuntimeException ex) {
            log.warn("Dispatch for order {} created without its shipping snapshot: {}",
                    event.orderId(), ex.getMessage());
            dispatchRepository.save(Dispatch.create(event.orderId()));
            log.info("Created PENDING dispatch for order {}", event.orderId());
            return;
        }

        if (order.deliveryMethod() == DeliveryMethod.PICKUP) {
            log.info("Order {} is PICKUP — no dispatch created", event.orderId());
            return;
        }

        dispatchRepository.save(Dispatch.create(
                event.orderId(), order.shippingZoneCode(), order.shippingCourierId(),
                order.shippingCourierName(), order.shippingAddressReference()));
        log.info("Created PENDING dispatch for order {}", event.orderId());
```

Remove the now-redundant `buildDispatch(...)` private method (its logic is inlined above), or keep
it and add the `PICKUP` check inside `onOrderStatusChanged` before calling it — pick the smaller
diff. Either way `getOrderUseCase.execute` is called **once**.

- [ ] **Step 4: Run dispatch tests**

Run: `cd backend && mvn -o test -Dtest='CreateDispatchForPaidOrderUseCaseTest,*Dispatch*Test'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/dispatch backend/src/test/java/com/pilarestilo/dispatch
git commit -m "feat(dispatch): PICKUP orders do not enter the dispatch queue"
```

---

## Task 7: Web endpoint `POST /api/admin/sales/external`

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/order/infrastructure/web/requests/RegisterExternalSaleRequest.java`
- Create: `backend/src/main/java/com/pilarestilo/order/infrastructure/web/controllers/ExternalSaleController.java`
- Modify: `backend/src/main/java/com/pilarestilo/order/infrastructure/web/controllers/OrderController.java` (`getById` null guard only)
- Test: `backend/src/test/java/com/pilarestilo/order/infrastructure/web/ExternalSaleControllerIT.java`

**Interfaces:**
- Consumes: `RegisterExternalSaleUseCase.execute` → `RegisterExternalSaleResult` (T5),
  `PermissionRegistry.ORDERS_CREATE` (T5).
- Produces: `POST /api/admin/sales/external` → `201` + `OrderDto` (or `200` + existing on
  idempotent replay).

- [ ] **Step 1: Write the request POJO**

```java
package com.pilarestilo.order.infrastructure.web.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RegisterExternalSaleRequest(
        @NotBlank String idempotencyKey,
        @NotBlank @Size(max = 160) String buyerName,
        @NotBlank @Size(max = 160) String buyerContact,
        @NotBlank String salesChannel,   // INSTAGRAM | FACEBOOK | WHATSAPP | MANUAL
        @NotBlank String paymentMethod,  // TRANSFER | OTHER
        @NotBlank String deliveryMethod, // SHIPPING | PICKUP
        @Size(max = 500) String shippingAddress,
        @Size(max = 1000) String notes,
        @NotEmpty @Size(max = 50) @Valid List<Line> items
) {
    public record Line(
            @NotNull UUID productId,
            String variantColor,
            String variantSize,
            @Min(1) @Max(999) int quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice
    ) {}
}
```

- [ ] **Step 2: Write the failing IT**

`ExternalSaleControllerIT.java` (mirror an existing controller IT — `grep -rln
"MockMvc.*perform.*api/orders\|@AutoConfigureMockMvc" backend/src/test` — reuse its
Testcontainers base, its token minting for a SELLER and an unauthorized user, and a seeded
product with variant stock):

```java
    @Test
    void requires_orders_create_permission() throws Exception {
        mockMvc.perform(post("/api/admin/sales/external")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyFor(seededProductId, "Rojo", "M", 1, "15000", "PICKUP", null)))
            .andExpect(status().isForbidden());
    }

    @Test
    void seller_registers_a_shipping_sale_and_it_is_readable() throws Exception {
        String json = mockMvc.perform(post("/api/admin/sales/external")
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyFor(seededProductId, "Rojo", "M", 2, "15000", "SHIPPING", "Av. Siempre Viva 742")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.deliveryMethod").value("SHIPPING"))
            .andExpect(jsonPath("$.buyerName").value("Javiera"))
            .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");

        mockMvc.perform(get("/api/orders/" + id).header("Authorization", "Bearer " + sellerToken))
            .andExpect(status().isOk());
    }

    @Test
    void insufficient_stock_is_409() throws Exception {
        mockMvc.perform(post("/api/admin/sales/external")
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyFor(seededProductId, "Rojo", "M", 99999, "15000", "PICKUP", null)))
            .andExpect(status().isConflict());
    }

    @Test
    void unknown_product_is_404() throws Exception {
        mockMvc.perform(post("/api/admin/sales/external")
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyFor(UUID.randomUUID(), null, null, 1, "1000", "PICKUP", null)))
            .andExpect(status().isNotFound());
    }

    @Test
    void a_repeated_idempotency_key_returns_the_same_order() throws Exception {
        String body = bodyForWithKey("dup-key-1", seededProductId, "Rojo", "M", 1, "15000", "PICKUP", null);
        String first = mockMvc.perform(post("/api/admin/sales/external")
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/admin/sales/external")
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
    }
```

- [ ] **Step 3: Run it, verify it fails** (endpoint 404s / method missing).

- [ ] **Step 4: Create `ExternalSaleController`**

`OrderController` is mapped to `/api/orders`; the external-sale path is `/api/admin/sales/external`,
so this is a **new controller**, not an edit.

```java
package com.pilarestilo.order.infrastructure.web.controllers;

import com.pilarestilo.order.application.commands.RegisterExternalSaleCommand;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.usecases.RegisterExternalSaleResult;
import com.pilarestilo.order.application.usecases.RegisterExternalSaleUseCase;
import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.infrastructure.web.requests.RegisterExternalSaleRequest;
import com.pilarestilo.shared.domain.DomainException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/sales")
public class ExternalSaleController {

    private static final Set<String> CHANNELS = Set.of("INSTAGRAM", "FACEBOOK", "WHATSAPP", "MANUAL");
    private static final Set<String> PAYMENTS = Set.of("TRANSFER", "OTHER");

    private final RegisterExternalSaleUseCase useCase;

    public ExternalSaleController(RegisterExternalSaleUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/external")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).ORDERS_CREATE)")
    public ResponseEntity<OrderDto> registerExternalSale(@Valid @RequestBody RegisterExternalSaleRequest req) {
        RegisterExternalSaleCommand cmd = new RegisterExternalSaleCommand(
                req.idempotencyKey(), req.buyerName(), req.buyerContact(),
                channel(req.salesChannel()), payment(req.paymentMethod()), delivery(req.deliveryMethod()),
                req.shippingAddress(), req.notes(),
                req.items().stream().map(l -> new RegisterExternalSaleCommand.Line(
                        l.productId(), l.variantColor(), l.variantSize(), l.quantity(), l.unitPrice())).toList());

        RegisterExternalSaleResult result = useCase.execute(cmd);
        return ResponseEntity
                .status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.dto());
    }

    private static SalesChannel channel(String raw) {
        String v = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(v)) {
            throw new DomainException("Canal invalido: " + raw);
        }
        return SalesChannel.valueOf(v);
    }

    private static PaymentMethod payment(String raw) {
        String v = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        if (!PAYMENTS.contains(v)) {
            throw new DomainException("Metodo de pago invalido: " + raw);
        }
        return PaymentMethod.valueOf(v);
    }

    private static DeliveryMethod delivery(String raw) {
        try {
            return DeliveryMethod.valueOf(raw == null ? "" : raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainException("Metodo de entrega invalido: " + raw);
        }
    }
}
```

`RegisterExternalSaleResult`:

```java
package com.pilarestilo.order.application.usecases;

import com.pilarestilo.order.application.dto.OrderDto;

/** @param replayed true when an idempotency-key match returned an existing order. */
public record RegisterExternalSaleResult(OrderDto dto, boolean replayed) {}
```

- [ ] **Step 5: `getById` null-customer guard**

In `OrderController.getById`, change
`if (currentUser.role() == UserRole.CUSTOMER && !dto.customerId().equals(currentUser.id()))`
to `!java.util.Objects.equals(dto.customerId(), currentUser.id())` (a CUSTOMER can never own a
null-customer order, so this correctly denies without NPE).

- [ ] **Step 6: Run the IT**

Run: `cd backend && mvn -o verify -Dit.test=ExternalSaleControllerIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 7: Full backend suite (regression gate)**

Run: `cd backend && mvn -o test`
Expected: PASS (all ~560+).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/order backend/src/test/java/com/pilarestilo/order
git commit -m "feat(order): POST /api/admin/sales/external endpoint"
```

---

## Task 8: Frontend — API client, drawer, wire-in

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Create: `frontend/src/islands/admin/RegisterSaleDrawer.tsx`
- Modify: `frontend/src/islands/admin/VentasPage.tsx`
- Test: `frontend/src/islands/admin/__tests__/RegisterSaleDrawer.test.tsx`

**Interfaces:**
- Consumes: `POST /api/admin/sales/external`, `searchProducts(...)` (`lib/api.ts`), `Overlay`
  (`islands/admin/Overlay`).
- Produces: `registerExternalSale(body: ExternalSaleRequest, token: string): Promise<OrderDto>`;
  `RegisterSaleDrawer` React component with props `{ token: string; onClose: () => void; onCreated:
  () => void }`.

- [ ] **Step 1: `lib/api.ts` — types + function**

Add to the `OrderDto` TS type: `deliveryMethod: 'SHIPPING' | 'PICKUP'; buyerName: string | null;
buyerContact: string | null;`. Add:

```ts
export interface ExternalSaleLine {
  productId: string;
  variantColor?: string | null;
  variantSize?: string | null;
  quantity: number;
  unitPrice: number;
}
export interface ExternalSaleRequest {
  idempotencyKey: string;
  buyerName: string;
  buyerContact: string;
  salesChannel: 'INSTAGRAM' | 'FACEBOOK' | 'WHATSAPP' | 'MANUAL';
  paymentMethod: 'TRANSFER' | 'OTHER';
  deliveryMethod: 'SHIPPING' | 'PICKUP';
  shippingAddress?: string;
  notes?: string;
  items: ExternalSaleLine[];
}

export async function registerExternalSale(
  body: ExternalSaleRequest,
  token: string,
): Promise<OrderDto> {
  return apiFetch<OrderDto>('/admin/sales/external', {
    method: 'POST',
    token,
    body: JSON.stringify(body),
  });
}
```

(Match `apiFetch`'s actual signature — check a neighbouring `POST` call in `api.ts`. The 409 body
is a `ProblemDetail`; `apiFetch` should already surface `error.message` from `detail` — verify and,
if not, thread it through so the drawer can show the stock message.)

- [ ] **Step 2: Write the failing drawer test**

`RegisterSaleDrawer.test.tsx` (vitest + RTL, `import '@testing-library/jest-dom/vitest'`, mock
`../../../lib/api`):

```tsx
vi.mock('../../../lib/api', () => ({
  searchProducts: vi.fn().mockResolvedValue({ content: [
    { id: 'p1', name: 'Vestido', price: { amount: 19990, currency: 'CLP' }, variants: [
      { color: 'Rojo', size: 'M', stockAvailable: 5 } ] } ] }),
  registerExternalSale: vi.fn().mockResolvedValue({ id: 'o1' }),
}));

// helper: render <RegisterSaleDrawer token="t" onClose={onClose} onCreated={onCreated} />

it('recalculates the live total when a line price is edited', async () => { /* add a product, edit unitPrice input, assert the total text */ });
it('requires an address only when Envío is selected', async () => { /* toggle to Envío, submit, expect validation; toggle to Retiro, address field gone */ });
it('shows the stock message on a 409', async () => {
  vi.mocked(registerExternalSale).mockRejectedValueOnce(new Error('Stock insuficiente para Rojo / M'));
  /* fill a valid form, submit, assert the message renders */
});
it('closes and calls onCreated on success', async () => { /* submit valid, expect onCreated + onClose called */ });
```

- [ ] **Step 3: Run it, verify it fails** (component missing).

- [ ] **Step 4: Build `RegisterSaleDrawer.tsx`**

Follow `[[admin-overlays-need-a-portal]]`: wrap in `<Overlay>`, use a `<dialog>` opened via a
**ref callback** (`(node) => { if (node && !node.open) node.showModal(); }`), colour tokens only,
`type="button"` on every button, labelled inputs. Structure:

- Local state: `lines: {product, variantColor, variantSize, quantity, unitPrice}[]`, `buyerName`,
  `buyerContact`, `channel`, `paymentMethod`, `delivery` (`'SHIPPING'|'PICKUP'`), `address`,
  `notes`, `submitting`, `error`.
- `idempotencyKey` = `useRef(crypto.randomUUID())` — one per mount.
- Product search: a debounced input calling `searchProducts`, a results list; picking one appends
  a line with `unitPrice` preset from `product.price.amount`. If the product has variants, a
  color/size select on the line; quantity and unit-price are `<input type="number">`.
- Live total: `lines.reduce((s, l) => s + l.unitPrice * l.quantity, 0)`, formatted with the
  existing CLP formatter (`formatPrice` / `lib/formatPrice`).
- Validation before submit: ≥1 line, buyer name + contact non-blank, address non-blank when
  `delivery === 'SHIPPING'`, every line has a variant selected if its product has variants.
- Submit: `registerExternalSale({...}, token)`; on success `onCreated(); onClose();`; on error
  `setError(err.message)`.

- [ ] **Step 5: Wire into `VentasPage.tsx`**

Add a "Registrar venta" `<button>` above the `DataTable` (right-aligned in the existing header
row). State `showDrawer`. Render `{showDrawer && <RegisterSaleDrawer token={token}
onClose={() => setShowDrawer(false)} onCreated={() => { setShowDrawer(false); reload(); }} />}`
where `reload` is whatever `VentasPage` already calls to refetch the list (find it — likely a
`useEffect` dep bump or an explicit fetch function). The permission gate for showing the button:
`VentasPage` is already behind `orders.read`; show the button unconditionally (the API enforces
`orders.create`) or, if the page has the user's permissions in scope, gate on `orders.create`.

- [ ] **Step 6: Run frontend checks**

Run: `cd frontend && npx tsc --noEmit && npx vitest run src/islands/admin/__tests__/RegisterSaleDrawer.test.tsx && npx eslint src/islands/admin/RegisterSaleDrawer.tsx src/islands/admin/VentasPage.tsx src/lib/api.ts`
Then: `npx vitest run` (full) and `npm run build`.
Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/islands/admin/RegisterSaleDrawer.tsx \
  frontend/src/islands/admin/VentasPage.tsx frontend/src/islands/admin/__tests__/RegisterSaleDrawer.test.tsx
git commit -m "feat(admin): Registrar venta drawer for off-platform sales"
```

---

## Task 9: e2e, docs, memory

**Files:**
- Create: `frontend/e2e/external-sale.spec.ts`
- Modify: `docs/pos-channel.md`, `CLAUDE.md`, `docs/superpowers/specs/2026-08-31-external-sale-intake-design.md`
- Modify: `C:\Users\chcal\.claude\projects\e--dev-PilarEstilo\memory\pending-work-queue.md`

- [ ] **Step 1: Playwright e2e**

`frontend/e2e/external-sale.spec.ts` — against the local Docker stack (`TEST_BASE_URL`), log in as
admin (`admin@pilarestilo.com` / `admin2026`), go to `/admin/ventas`, click "Registrar venta",
add a product with a known variant + quantity, fill buyer + channel + payment, choose "Retiro en
persona", submit. Assert: the drawer closes, a new row appears in the sales table, and it shows
the "Sin boleta" indicator. Then open `/admin/products` (or the product API) and assert the
variant's stock dropped by the sold quantity.

- [ ] **Step 2: Run it against the running stack**

Run (stack already up): `cd frontend && TEST_BASE_URL=https://localhost npx playwright test e2e/external-sale.spec.ts`
Expected: PASS.

- [ ] **Step 3: Docs**

- `docs/pos-channel.md`: add a line under "Implementation Plan" — "The order-creation engine now
  exists (`RegisterExternalSaleUseCase`, `POST /api/admin/sales/external`, Fase 2 F). The POS
  endpoint just needs to build a `RegisterExternalSaleCommand` with `salesChannel = POS` and call
  it, then hand the created order to `RegisterPosSaleUseCase` for the cash movement."
- `CLAUDE.md`: bump "Current highest: **V94**" and add the V94 line to the recent-migrations list.
- The spec's `**Status:**` line → `implemented <date> (master <sha>)`.

- [ ] **Step 4: Memory**

Update `pending-work-queue.md`: Fase 2 F first-half done (social-sale intake), outbox still
Increment I, POS is now a thin wrapper over the new engine.

- [ ] **Step 5: Commit**

```bash
git add frontend/e2e/external-sale.spec.ts docs/ CLAUDE.md
git commit -m "test(e2e): external sale intake; docs + migration index"
```

---

## Self-Review

**Spec coverage:**
- Decisions 1–8 → Tasks 1, 5 (born PAID, free-text buyer, editable price, no discount, no
  notification), 2 (`delivery_method`), 6 (PICKUP no dispatch), 5+7 (`orders.create`), boleta =
  no code (VentasPage already flags it — verified in exploration).
- §1 migration → T2. §1 domain → T1. §1 application → T5. §1 dispatch → T6. §1 web → T7.
  §1 frontend → T8. Analytics note → T4 (recommendation taken). §2 data flow → T5+T6+T7.
  §3 error handling → T3 (409), T5 (validation), T7 (parse errors). §4 testing → each task's tests
  + T9 e2e. §5 out of scope → respected (no outbox, no POS wiring, no customer linking, no edit UI).

**Placeholder scan:** the "find it / check it during planning" notes remaining are genuine
lookups an implementer does in-repo (the entity↔domain mapper location, the IT base class, the
`role_permission_grants` column list, `apiFetch` signature, `VentasPage`'s reload hook) — each
names exactly what to grep for. No vague "add error handling" / "write tests" without code.

**Type consistency:** `Order.createExternalSale` signature is identical in T1 (definition), T2
(IT), T5 (use case + test). `RegisterExternalSaleCommand` identical in T5 and T7.
`RegisterExternalSaleUseCase.execute` returns `RegisterExternalSaleResult(OrderDto dto, boolean
replayed)` — defined in T5 step 4, used in T5's tests (`.dto()`, `.replayed()`) and in T7's
controller. `OrderDto` gains `deliveryMethod, buyerName, buyerContact` appended after `updatedAt`
— T2 defines, T5/T7 assert, T8 mirrors in TS.

**Fixes applied during review:**
1. `RegisterExternalSaleUseCase.execute` returns `RegisterExternalSaleResult`, not bare `OrderDto`,
   from the start (T5 step 4 creates the record; the controller maps `replayed ? 200 : 201`).
2. The endpoint lives on a **new `ExternalSaleController`** mapped to `/api/admin/sales`, not on
   `OrderController` (`/api/orders`). `OrderController` only gets the `getById` null-customer guard.
