# Pilar Estilo — Domain Events

## Event Catalog

All domain events are immutable value objects that extend `DomainEvent` (defined in `domain/events/`). They carry only the data needed by subscribers; no business logic lives inside an event.

| Event | Payload Fields | Publisher Module | Current Subscribers | Future Kafka Topic |
|---|---|---|---|---|
| `ProductCreated` | `productId`, `sku`, `nameEs`, `nameEn`, `price`, `categoryId`, `createdAt` | `product` | inventory (reserve initial stock slot) | `pe.product.created` |
| `ProductUpdated` | `productId`, `sku`, `changedFields`, `updatedAt` | `product` | _(none in v1)_ | `pe.product.updated` |
| `OrderCreated` | `orderId`, `customerId`, `lineItems[]`, `totalAmount`, `paymentMethod`, `createdAt` | `order` | payment (auto-create Payment record), inventory (reserve stock) | `pe.order.created` |
| `OrderStatusChanged` | `orderId`, `previousStatus`, `newStatus`, `changedAt`, `changedBy` | `order` | payment (cancel payment if order cancelled), notification | `pe.order.status-changed` |
| `PaymentRegistered` | `paymentId`, `orderId`, `amount`, `method`, `registeredAt` | `payment` | _(none in v1 — record only)_ | `pe.payment.registered` |
| `PaymentSubmitted` | `paymentId`, `orderId`, `proofReference`, `submittedAt` | `payment` | notification (alert admin) | `pe.payment.submitted` |
| `PaymentConfirmed` | `paymentId`, `orderId`, `confirmedBy`, `confirmedAt` | `payment` | order (mark PAID), inventory (confirm reservation), notification (email/WhatsApp customer) | `pe.payment.confirmed` |
| `PaymentRejected` | `paymentId`, `orderId`, `rejectedBy`, `reason`, `rejectedAt` | `payment` | order (mark PAYMENT_FAILED), inventory (release reservation), notification (alert customer) | `pe.payment.rejected` |
| `DiscountApplied` | `discountId`, `code`, `orderId`, `discountAmount`, `appliedAt` | `discount` | order (adjust total), audit log | `pe.discount.applied` |
| `StoreCreditGranted` | `creditId`, `customerId`, `amount`, `reason`, `grantedAt` | `customercredit` | notification (inform customer) | `pe.store-credit.granted` |
| `StoreCreditUsed` | `creditId`, `customerId`, `orderId`, `amountUsed`, `remainingBalance`, `usedAt` | `customercredit` | order (record credit usage) | `pe.store-credit.used` |
| `StockUpdated` | `productId`, `variantId`, `previousQty`, `newQty`, `reason`, `updatedAt` | `inventory` | product (mark out-of-stock if qty = 0), notification (low-stock alert if below threshold) | `pe.inventory.stock-updated` |
| `ReviewCreated` | `reviewId`, `productId`, `userId`, `rating`, `createdAt` | `review` | _(none — pending moderation)_ | `pe.review.created` |
| `ReviewApproved` | `reviewId`, `productId`, `userId`, `rating`, `approvedBy`, `approvedAt` | `review` | product (`ReviewSummaryListener` recomputes `avg_rating` + `review_count`) | `pe.review.approved` |
| `ReviewDeleted` | `reviewId`, `productId`, `deletedAt` | `review` | product (`ReviewSummaryListener` recomputes `avg_rating` + `review_count`) | `pe.review.deleted` |

---

## 1. How the In-Process Publisher Works

The current implementation uses Spring's `ApplicationEventPublisher` as the transport mechanism, wrapped behind the `DomainEventPublisher` port so domain code never imports Spring directly.

**Publishing an event (inside a use case):**

```java
// application/usecases/CreateOrderUseCase.java
public class CreateOrderUseCase {
    private final OrderRepository orderRepository;
    private final DomainEventPublisher eventPublisher;

    public Order execute(CreateOrderCommand cmd) {
        Order order = Order.create(cmd);
        orderRepository.save(order);
        eventPublisher.publish(new OrderCreated(order));  // fires event
        return order;
    }
}
```

**Subscribing to an event:**

```java
// payment/infrastructure/events/OrderCreatedListener.java
@Component
public class OrderCreatedListener {

    @EventListener
    @Transactional  // runs in the same transaction as the publisher by default
    public void on(OrderCreated event) {
        // auto-create a PENDING Payment record for this order
        createPendingPaymentUseCase.execute(event.orderId(), event.totalAmount(), event.paymentMethod());
    }
}
```

**Key characteristics of the in-process publisher:**

- **Synchronous** — the publisher's thread blocks until all listeners return.
- **Transactional** — by default, listeners run inside the originating transaction. If the transaction rolls back, the listener's side effects roll back with it.
- **No serialization** — events are plain Java objects; no serialization overhead.
- **Single JVM** — events cannot cross process boundaries.

---

## 2. Migrating to Kafka

When the platform needs async processing, cross-service fanout, or event replay capability, replace the in-process publisher with a Kafka adapter. The `DomainEventPublisher` port is the only seam that needs to change.

**Step 1 — Create the Kafka publisher adapter:**

```java
// infrastructure/kafka/KafkaDomainEventPublisher.java
@Component
@Primary  // replaces SpringDomainEventPublisher as the active bean
public class KafkaDomainEventPublisher implements DomainEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(DomainEvent event) {
        String topic = topicFor(event);
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(topic, event.aggregateId(), payload);
    }

    private String topicFor(DomainEvent event) {
        return switch (event) {
            case ProductCreated e    -> "pe.product.created";
            case OrderCreated e      -> "pe.order.created";
            case PaymentConfirmed e  -> "pe.payment.confirmed";
            case ReviewCreated e     -> "pe.review.created";
            case ReviewApproved e    -> "pe.review.approved";
            case ReviewDeleted e     -> "pe.review.deleted";
            // ... etc.
            default -> "pe.events.unknown";
        };
    }
}
```

**Step 2 — Update subscribers to `@KafkaListener`:**

```java
// payment/infrastructure/kafka/OrderCreatedKafkaListener.java
@Component
public class OrderCreatedKafkaListener {

    @KafkaListener(topics = "pe.order.created", groupId = "payment-service")
    public void on(String payload) {
        OrderCreated event = objectMapper.readValue(payload, OrderCreated.class);
        createPendingPaymentUseCase.execute(event.orderId(), event.totalAmount(), event.paymentMethod());
    }
}
```

**Step 3 — Remove `@Primary` from `SpringDomainEventPublisher`** (or delete the class).

No domain or application code changes are required.

---

## 3. Kafka Topic Naming Conventions

Topics follow the pattern: `pe.<module>.<event-name-kebab-case>`

| Domain Event | Kafka Topic |
|---|---|
| `ProductCreated` | `pe.product.created` |
| `ProductUpdated` | `pe.product.updated` |
| `OrderCreated` | `pe.order.created` |
| `OrderStatusChanged` | `pe.order.status-changed` |
| `PaymentRegistered` | `pe.payment.registered` |
| `PaymentSubmitted` | `pe.payment.submitted` |
| `PaymentConfirmed` | `pe.payment.confirmed` |
| `PaymentRejected` | `pe.payment.rejected` |
| `DiscountApplied` | `pe.discount.applied` |
| `StoreCreditGranted` | `pe.store-credit.granted` |
| `StoreCreditUsed` | `pe.store-credit.used` |
| `StockUpdated` | `pe.inventory.stock-updated` |
| `ReviewCreated` | `pe.review.created` |
| `ReviewApproved` | `pe.review.approved` |
| `ReviewDeleted` | `pe.review.deleted` |

**Conventions:**
- Prefix `pe.` scopes topics to this platform (avoids collisions in shared Kafka clusters).
- Module name is the second segment (matches the Java module package name).
- Event name is kebab-cased past-tense verb phrase (matches DDD event naming convention).
- Consumer group IDs follow `<module>-service` (e.g. `payment-service`, `inventory-service`).
