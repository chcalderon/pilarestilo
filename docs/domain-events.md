# Pilar Estilo - Domain Events

This file reflects the event contracts currently defined in code.

## Event Catalog

| Event | Payload Fields | Publisher Module | Current Subscribers |
|---|---|---|---|
| `ProductCreated` | `productId`, `productName`, `occurredAt` | `product` | none |
| `ProductUpdated` | `productId`, `productName`, `occurredAt` | `product` | none |
| `OrderCreated` | `orderId`, `customerId`, `occurredAt` | `order` | payment registration, order confirmation notification |
| `OrderStatusChanged` | `orderId`, `customerId`, `previousStatus`, `newStatus`, `occurredAt` | `order` | order preparing/shipped notifications, cash-register sale registration on `PAID`, dispatch creation on `PAID` |
| `PaymentRegistered` | `paymentId`, `orderId`, `occurredAt` | `payment` | none |
| `PaymentSubmitted` | `paymentId`, `proofReference`, `occurredAt` | `payment` | none |
| `PaymentConfirmed` | `paymentId`, `orderId`, `occurredAt` | `payment` | `OrderInventorySaga` (status progression), payment notification |
| `PaymentRejected` | `paymentId`, `orderId`, `reviewerId`, `occurredAt` | `payment` | `OrderInventorySaga` (cancel + stock compensation) |
| `DiscountCodeAssigned` | `discountId`, `code`, `assignedUserId`, `occurredAt` | `discount` | discount assignment notification (in-app + outbound) |
| `StoreCreditGranted` | `customerId`, `amount`, `occurredAt` | `customercredit` | none |
| `StoreCreditUsed` | `customerId`, `amount`, `occurredAt` | `customercredit` | none |
| `StockUpdated` | `productId`, `newStock`, `occurredAt` | `inventory` | none |
| `ReviewCreated` | `reviewId`, `productId`, `userId`, `rating`, `occurredAt` | `review` | product rating summary listener |
| `ReviewApproved` | `reviewId`, `productId`, `occurredAt` | `review` | product rating summary listener |
| `ReviewDeleted` | `reviewId`, `productId`, `occurredAt` | `review` | product rating summary listener |

---

## Publisher adapters

`DomainEventPublisher` now supports two runtime adapters:

- `shared/infrastructure/SpringDomainEventPublisher` (default, in-process)
- `shared/infrastructure/kafka/KafkaDomainEventPublisher` (enabled with `APP_DOMAIN_EVENTS_KAFKA_ENABLED=true`)

Kafka mode behavior:

- event-type topic routing (prefix + kebab-case event name, e.g. `pe.domain.order-created`)
- JSON payload with type headers
- synchronous publish acknowledgement from producer send

---

## Current listeners in code

- `payment/infrastructure/listeners/OrderEventListener`
  - listens `OrderCreated`
  - registers `PENDING` payment

- `payment/infrastructure/listeners/PaymentEventListener`
  - listens `PaymentConfirmed` and `PaymentRejected`
  - triggers `OrderInventorySaga` status progression / compensation

- `review/infrastructure/listeners/ReviewSummaryListener`
  - listens `ReviewCreated`, `ReviewApproved`, `ReviewDeleted`
  - recomputes product `avg_rating` + `review_count` in `REQUIRES_NEW`

- `notification/infrastructure/listeners/OrderNotificationListener`
  - listens `OrderCreated` and `OrderStatusChanged`
  - sends order confirmation + preparing + shipped notifications

- `notification/infrastructure/listeners/PaymentNotificationListener`
  - listens `PaymentConfirmed`

- `notification/infrastructure/listeners/DiscountNotificationListener`
  - listens `DiscountCodeAssigned`
  - sends assignment notification + persists in-app notification

- `cashregister/infrastructure/listeners/OrderPaidCashRegisterListener`
  - listens `OrderStatusChanged`
  - on `PAID`, records `SALE` movement in the open cash register (when available)

- `dispatch/infrastructure/listeners/OrderPaidDispatchListener`
  - listens `OrderStatusChanged`
  - on `PAID`, creates `PENDING` dispatch if one does not exist

Kafka listener equivalents are available under:

- `payment/infrastructure/listeners/kafka/*`
- `review/infrastructure/listeners/kafka/*`
- `notification/infrastructure/listeners/kafka/*`

---

## Retry and DLQ

When Kafka mode is enabled, listeners use `domainEventsKafkaListenerContainerFactory` with:

- `DefaultErrorHandler`
- fixed backoff retry (`APP_DOMAIN_EVENTS_KAFKA_RETRY_BACKOFF_MS`)
- max attempts (`APP_DOMAIN_EVENTS_KAFKA_RETRY_MAX_ATTEMPTS`)
- dead-letter topic suffix (`APP_DOMAIN_EVENTS_KAFKA_DLT_SUFFIX`, default `.dlt`)
- run Kafka broker with compose profile: `docker compose -f infra/docker-compose.yml --env-file infra/.env --profile kafka up -d`

## Saga note

Order/payment/inventory consistency is coordinated through `OrderInventorySaga`:

- on `PaymentConfirmed`: progresses order status toward `PAID` (idempotent guards)
- on `PaymentRejected`: cancels order and compensates by releasing reserved stock
