# Pilar Estilo - Domain Events

This file reflects the event contracts currently defined in code.

## Event Catalog

| Event | Payload Fields | Publisher Module | Current Subscribers |
|---|---|---|---|
| `ProductCreated` | `productId`, `productName`, `occurredAt` | `product` | none |
| `ProductUpdated` | `productId`, `productName`, `occurredAt` | `product` | none |
| `OrderCreated` | `orderId`, `customerId`, `occurredAt` | `order` | payment registration, order confirmation notification |
| `OrderStatusChanged` | `orderId`, `customerId`, `previousStatus`, `newStatus`, `occurredAt` | `order` | shipped notification |
| `PaymentRegistered` | `paymentId`, `orderId`, `occurredAt` | `payment` | none |
| `PaymentSubmitted` | `paymentId`, `proofReference`, `occurredAt` | `payment` | none |
| `PaymentConfirmed` | `paymentId`, `orderId`, `occurredAt` | `payment` | order status update to `PAID`, payment notification |
| `PaymentRejected` | `paymentId`, `orderId`, `reviewerId`, `occurredAt` | `payment` | none (currently) |
| `DiscountApplied` | `discountId`, `discountCode`, `orderId`, `discountAmount`, `occurredAt` | `discount` | none |
| `StoreCreditGranted` | `customerId`, `amount`, `occurredAt` | `customercredit` | none |
| `StoreCreditUsed` | `customerId`, `amount`, `occurredAt` | `customercredit` | none |
| `StockUpdated` | `productId`, `newStock`, `occurredAt` | `inventory` | none |
| `ReviewCreated` | `reviewId`, `productId`, `userId`, `rating`, `occurredAt` | `review` | product rating summary listener |
| `ReviewApproved` | `reviewId`, `productId`, `occurredAt` | `review` | product rating summary listener |
| `ReviewDeleted` | `reviewId`, `productId`, `occurredAt` | `review` | product rating summary listener |

---

## In-process publisher

Current implementation uses Spring events behind the `DomainEventPublisher` port:

- `shared/domain/DomainEventPublisher` (port)
- `shared/infrastructure/SpringDomainEventPublisher` (adapter)

Behavior:

- synchronous dispatch
- same-process delivery
- transaction-aware with Spring listener semantics

---

## Current listeners in code

- `payment/infrastructure/listeners/OrderEventListener`
  - listens `OrderCreated`
  - registers `PENDING` payment

- `payment/infrastructure/listeners/PaymentEventListener`
  - listens `PaymentConfirmed`
  - updates order status to `PAID`

- `review/infrastructure/listeners/ReviewSummaryListener`
  - listens `ReviewCreated`, `ReviewApproved`, `ReviewDeleted`
  - recomputes product `avg_rating` + `review_count` in `REQUIRES_NEW`

- `notification/infrastructure/listeners/OrderNotificationListener`
  - listens `OrderCreated` and `OrderStatusChanged`

- `notification/infrastructure/listeners/PaymentNotificationListener`
  - listens `PaymentConfirmed`

---

## Kafka migration seam

The system is still ready for an adapter swap:

1. Create `KafkaDomainEventPublisher implements DomainEventPublisher`
2. Make it `@Primary`
3. Replace/augment listeners with `@KafkaListener`

Domain and use-case code can stay unchanged.
