# Notification Service Extraction — Design Spec

**Date:** 2026-08-28
**Status:** approved by user; **revised 2026-08-28 after reading the actual module** — the first
approved version rested on assumptions the code contradicts. This revision is what the implementation
plan is written from.

---

## 0. What changed in this revision, and why

The first version of this spec said notification-service would "touch exactly one database"
(`pilarestilo_notifications`), with the only cross-service dependency being a Redis-cached lookup of
`system_settings.notification_providers` through a new internal backend endpoint.

Reading the module showed every one of those assumptions is wrong:

1. **The events carry only IDs.** `OrderCreated(orderId, customerId, occurredAt)` and nothing more;
   the same for `OrderStatusChanged`, `PaymentConfirmed`, `PaymentRegistered`, `PaymentSubmitted`,
   `PaymentRejected`, `SalesDocumentIssued`, `ReturnRequested`, `ReturnApproved`, `RefundRegistered`,
   `UserRegistered`, `DiscountCodeAssigned`. Every dispatcher **re-reads the aggregate** from the
   main database to compose a message.

2. **Six of seven dispatchers read main-DB aggregates:**

   | Dispatcher | Reads from `pilarestilo` |
   |---|---|
   | `OrderNotificationDispatcher` | `OrderRepository`, `UserRepository` |
   | `PaymentNotificationDispatcher` | `OrderRepository`, `UserRepository`, `PaymentRepository` |
   | `BillingNotificationDispatcher` | `SalesDocumentRepository`, `OrderRepository`, `UserRepository` |
   | `ReturnNotificationDispatcher` | `ReturnRequestRepository`, `OrderRepository`, `UserRepository`, `findByRoleIn` |
   | `UserNotificationDispatcher` | `UserRepository` |
   | `DiscountNotificationDispatcher` | `UserRepository` |
   | `PaymentRegisteredNotificationListener` (logic in the listener, not a dispatcher) | `PaymentRepository`, `OrderRepository`, `UserRepository`, `SystemSettingsRepository`, `BankTransferDeadline` |

   `NotificationComposer` renders full order lines, per-item variant + unit price, subtotal /
   discount / net / tax / total, shipping courier and zone, the payment's bank-transfer snapshot,
   the sales document's folio and amounts, and the return's kind / reason / deadline / refund block.

3. **The "settings lookup" is not one provider list.** `SmtpEmailNotificationSender`,
   `SendGridEmailNotificationSender`, `TwilioWhatsAppNotificationSender`,
   `SimulatedWhatsAppNotificationSender` and `N8nWebhookNotificationSender` each read the **entire
   messaging configuration block** from `system_settings` (`smtp_*`, `sendgrid_*`,
   `whatsapp_twilio_*`, `whatsapp_simulated_*`, `n8n_*`, `notification_providers`, `updated_by`),
   including **encrypted secrets** decrypted through `SystemSettingsCryptoService`. An internal
   endpoint here would mean streaming decrypted SMTP passwords and Twilio tokens as plaintext JSON.

4. **A domain write is buried in a dispatcher.** `PaymentNotificationDispatcher.onPaymentSubmitted`
   calls `updateOrderStatusUseCase.execute(order.getId(), OrderStatus.PAYMENT_UNDER_REVIEW)`. That
   is order-domain behaviour, not notification behaviour, and it cannot travel to a read-only
   service.

**The decision that follows (section 3): notification-service connects to the shared `pilarestilo`
database read-only — exactly like the four existing services — and maps only the columns it needs.
No internal API. No Redis.**

---

## 1. Context and motivation

The backend monolith holds two full `DataSource` / `EntityManagerFactory` / `TransactionManager`
triples in one process: the main `pilarestilo` database and `pilarestilo_notifications` (split off
2026-08-20, `notification-database-split` memory). That is what forces the hand-built
`PersistenceConfig` + `EntityScanConfig` + `NotificationsPersistenceConfig` +
`NotificationsFlywayMigrator` machinery, and the `EntityScanCoversEveryModuleTest` guard.

Only one class, `NotificationRepositoryAdapter`, touches the notifications database, behind the
`InAppNotificationRepository` / `InAppNotificationPort` domain ports. Triggering is already 100%
event-driven (a `Kafka*NotificationListener` per dispatcher). The database-per-service half of the
split is done; the process boundary is not.

This extraction is the first concrete step of a stated longer-term direction — the monolith
disaggregating module by module until nothing is left. The order and boundaries of the other 16+
modules are **out of scope**; that is its own brainstorming session (`monolith-dissolution-direction`
memory).

---

## 2. Prior art — how the four existing services are built

`services/{product,inventory,order,payment}-service` are each:

- An independent Maven project (Spring Boot 4.1.1, Java 25, own `pom.xml`, `groupId
  com.pilarestilo`, own package namespace e.g. `com.pilarestilo.orderservice` — **not**
  `com.pilarestilo.order`). No compiled module is shared with `backend/`; extracting code means
  porting it, not moving a package.
- Multi-stage `Dockerfile` (`maven:3.9-eclipse-temurin-25` build → `eclipse-temurin:25-jre-alpine`
  runtime), own `EXPOSE` port (8081–8084 used).
- **Connected to the same shared `pilarestilo` database as the monolith**
  (`SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-pilarestilo}`),
  `ddl-auto: validate`, `open-in-view: false`. None has its own database. Each maps a **hand-picked
  subset** of tables and columns as JPA entities — e.g. `order-service`'s `SystemSettingsEntity`
  maps 8 of the ~90 columns of `system_settings`, only the bank-transfer and shipping ones it reads.
- Wired into observability: `/actuator/prometheus` scraped as a new `job_name` in
  `infra/monitoring/prometheus/prometheus.yml`; `APP_TRACING_ENABLED` /
  `APP_TRACING_OTLP_ENDPOINT` / `APP_TRACING_SAMPLING_PROBABILITY` to the OTel Collector → Tempo.
- Behind Caddy, routed by HTTP method + path prefix, with `backend:8080` as a `lb_policy first`
  fallback when the `microservices` profile is off.
- `profiles: ["microservices"]` in `infra/docker-compose.yml`; `depends_on: postgres:
  condition: service_healthy`.
- `sonar.coverage.jacoco.xmlReportPaths` in the pom + a jacoco `check` execution at `verify` with a
  **0.50 line-coverage** bundle minimum. Part of the existing CI services matrix.
- Internal service-to-service calls use a shared-secret header token
  (`APP_ORDER_INTERNAL_TOKEN` etc.); `SecurityConfig` is stateless JWT, CSRF off, and a
  `JwtAuthenticationFilter` reads the credential from the `Authorization` header only.

**None of the four consume Kafka.** notification-service is the first service to be a Kafka consumer
— but the whole consumer stack (`KafkaDomainEventsConfiguration`, `DomainEventTopics`,
`KafkaDomainEventsProperties`) already exists in `backend/src/main/java/com/pilarestilo/shared/` and
is transport-only; it ports cleanly.

**None of the four use Redis.** With this decision, neither does notification-service.

---

## 3. Decision: shared read-only database

notification-service connects to the shared `pilarestilo` database **read-only**, exactly as the
four existing services do, and maps only the columns it needs. It owns `pilarestilo_notifications`
read-write. It ports `SystemSettingsCryptoService` (a pure AES-GCM helper — no database, keyed off
the existing `SYSTEM_SETTINGS_CRYPTO_SECRET` env) so it decrypts stored secrets itself, identically
to the monolith today.

### Why not an internal read API on the monolith

- The senders need the full `system_settings` messaging block **with decrypted secrets**. An
  internal endpoint means SMTP passwords, the Twilio auth token, the SendGrid key and the n8n key
  crossing the wire as plaintext JSON on every refresh. Reading the ciphertext column and decrypting
  locally is what the monolith does now and is strictly better.
- It introduces a **service → monolith** call direction that nothing in the codebase does today.
- Total cost is higher: ~6 internal endpoints + 6 HTTP clients + 6 contract tests, *and* the
  settings/crypto problem still has to be solved on top.

### Why not enrich the events

- ~11 event records, published by `order` / `payment` / `billing` / `returns` / `user` /
  `discount` and consumed by other listeners, would have to carry every field the composer renders,
  plus the customer's email and phone — PII onto Kafka topics.
- The role-based recipient lists (`findByRoleIn([ADMIN, ADMINISTRACION])` for payment reviewers and
  return handlers) are a query, not an event payload.
- Largest blast radius of the three options for the least architectural gain.

### Accepted cost — cross-codebase schema coupling

notification-service's `ddl-auto: validate` will now also validate its mapped subsets of `orders`,
`order_items`, `users`, `payments`, `sales_documents`, `return_requests` and `system_settings`. A
monolith migration that renames or drops a column notification-service maps breaks its startup until
its entity is updated **in the same deploy**. This is the same coupling the four existing services
already carry, and the reads here are of settled-state rows (an order that exists, a payment that
confirmed, a document that issued), not fast-changing derived values. Mitigations in section 10.

`orders` / `order_items` is the one table with a real write-from-two-places hazard already
(`order-service` performs the INSERT in production). notification-service only **reads** it — it
adds a reader, not a third writer — but the CLAUDE.md "any change to `orders` / `order_items` …
must be applied in the same commit" rule now extends to `services/notification-service/` as well.

---

## 4. Architecture

New independent project **`services/notification-service/`**, port **8085**, following the exact
skeleton of the other four.

### 4.1 Moves out of the backend completely (no copy left behind)

Ported into `com.pilarestilo.notificationservice.*` (thin event DTOs, view entities and composer
signatures change; message-building bodies do not):

- **Dispatchers (7):** `OrderNotificationDispatcher`, `PaymentNotificationDispatcher`,
  `BillingNotificationDispatcher`, `DiscountNotificationDispatcher`, `ReturnNotificationDispatcher`,
  `UserNotificationDispatcher`, and a **new** `PaymentRegisteredNotificationDispatcher` (see 6.2).
- **Composition:** `NotificationComposer`, `EmailLayout` (pure — ports verbatim), `EmailFormat`,
  the `email/pilar-estilo-logo.png` classpath resource.
- **Senders (8):** `SmtpEmailNotificationSender`, `SendGridEmailNotificationSender`,
  `TwilioWhatsAppNotificationSender`, `SimulatedWhatsAppNotificationSender`,
  `N8nWebhookNotificationSender`, `InAppNotificationSender`, `LogNotificationSender`,
  `SystemSettingsNotificationSender` (`@Primary`, fans out over every configured channel).
- **Kafka listeners (7):** one per dispatcher, transport-only. The in-process `@EventListener`
  twins are **not** ported (Kafka-only, section 7).
- **In-app read side:** `NotificationController` (`GET /api/notifications`,
  `GET /api/notifications/unread-count`, `PUT /api/notifications/{id}/read`,
  `PUT /api/notifications/read-all`), its four use cases, `InAppNotificationDto`, the domain models
  (`InAppNotification`, `NotificationMessage`, `NotificationRecipient`, `NotificationType`) and
  ports (`InAppNotificationRepository`, `InAppNotificationPort`, `NotificationSender`).
- **Persistence:** `NotificationEntity`, `NotificationJpaRepository`, `NotificationRepositoryAdapter`
  → pointed at `pilarestilo_notifications`. **Simplification:** standalone, the "a migrator that is
  deliberately not a `Flyway` bean" workaround is gone — a normal `spring-boot-flyway` bean
  migrates the one database. The `@Transactional(NotificationsPersistenceConfig.TRANSACTION_MANAGER)`
  qualifiers drop to plain `@Transactional`.

> **Note on the controller verbs:** the current controller uses `@PutMapping` for
> `/{id}/read` and `/read-all` (the first spec said PATCH). Caddy routes must match the real verbs:
> **`GET`, `HEAD`, `PUT`** on `/api/notifications*`.

### 4.2 Read-only view of the shared `pilarestilo` database

New JPA entities in `com.pilarestilo.notificationservice.persistence.readonly.*`, `insertable =
false, updatable = false` throughout, mapping the **minimum** columns the composer and dispatchers
read. Several are copyable from `order-service`:

| Entity (table) | Columns mapped (minimum) |
|---|---|
| `system_settings` | `id`, `notification_providers`, `updated_by`, all `smtp_*`, all `sendgrid_*`, all `whatsapp_twilio_*`, `whatsapp_simulated_to/sender`, all `n8n_*`, `bank_transfer_auto_cancel_enabled/timeout_minutes` (for the transfer deadline) |
| `orders` | `id`, `public_reference`, `customer_id`, `status`, `subtotal_amount`+currency, `discount_amount`, `net_amount`, `tax_amount`, `tax_rate`, `total_amount`, `shipping_courier_id`, `shipping_courier_name`, `shipping_zone_code` |
| `order_items` | `order_id`, `product_name`, `variant_color`, `variant_size`, `quantity`, `unit_price_amount`+currency |
| `users` | `id`, `email`, `phone`, `full_name`, `role`, `active`, `notification_channel_preference` |
| `payments` | `id`, `order_id`, `method`, `status`, `reviewer_id`, `rejection_reason`, `proof_reference`, `created_at`, all `transfer_account_*` snapshot columns |
| `sales_documents` | `id`, `type`, `folio`, `net_amount`, `tax_amount`, `tax_rate`, `total_amount` |
| `return_requests` | `id`, `order_id`, `kind`, `reason`, `deadline_at`, `refund_amount`+currency, `refund_method`, `refund_reference`, `refunded_at` |

Repositories: Spring Data `JpaRepository`, read methods only. Adapters implement the same domain
ports the dispatchers already depend on (`OrderReadPort`, `CustomerReadPort`, `PaymentReadPort`,
`SalesDocumentReadPort`, `ReturnReadPort`, `MessagingSettingsPort`), so the dispatcher bodies barely
change.

### 4.3 Uses the infrastructure that already runs

| Piece | Use |
|---|---|
| Kafka | Sole trigger. Consumes the same `pe.domain.*` topics as a new consumer group `pe-notification-service`. Config ported from `shared/infrastructure/kafka`. |
| Postgres | `pilarestilo_notifications` (owned, RW) + `pilarestilo` (shared, RO). |
| Prometheus | `/actuator/prometheus`; new `notification_service` job in `prometheus.yml`. |
| Tempo + OTel | Same tracing envs. First time an event's publish→send path is one traceable span. |
| Caddy | `GET`, `HEAD`, `PUT` on `/api/notifications*` → `notification-service:8085`, `backend:8080` fallback. |
| Sonar + Jacoco | Same pom pattern; 5th entry in the CI services matrix. 0.50 line-coverage gate. |

### 4.4 Stays in the backend

Nothing notification-specific. The backend keeps publishing domain events to Kafka
(`KafkaDomainEventPublisher`) unchanged, and gains one small listener for the relocated order-status
write (6.1). No internal endpoint.

---

## 5. Data flow

**Trigger (event → notification):**
```
backend (OrderCreated, PaymentConfirmed, …)  ->  Kafka topic (unchanged)
  ->  notification-service Kafka*NotificationListener  (group pe-notification-service)
  ->  *NotificationDispatcher
        reads what it needs from pilarestilo (RO):   order + items, customer, payment,
                                                     sales document, return request
        composes via NotificationComposer / EmailLayout
  ->  SystemSettingsNotificationSender:
        reads system_settings.notification_providers (RO) + messaging config,
        decrypts secrets locally with SystemSettingsCryptoService
        fans out to every enabled channel, each in its own try
  ->  NotificationRepositoryAdapter writes the in-app row to pilarestilo_notifications (RW)
```

**In-app reads (the bell, live):**
```
Frontend -> Caddy -> notification-service   GET /api/notifications, GET .../unread-count
Frontend -> Caddy -> notification-service   PUT /api/notifications/{id}/read, PUT .../read-all
```
Never touches the monolith. The new service fully owns this data.

No Redis, no internal HTTP. Every lookup is a local DB read; volume is a handful of events per day.

---

## 6. Monolith-side changes

### 6.1 Relocate the order-status write

`PaymentNotificationDispatcher.onPaymentSubmitted` moves an order to `PAYMENT_UNDER_REVIEW` when a
proof is uploaded. Extract that transition into a **backend** listener on `PaymentSubmitted`
(e.g. `MarkOrderUnderReviewOnPaymentSubmittedListener` in `payment/` or `order/`), with both an
in-process and a Kafka transport like every other event handler, calling the existing
`UpdateOrderStatusUseCase`. notification-service's ported `PaymentNotificationDispatcher.onPaymentSubmitted`
then only emails the reviewers.

### 6.2 Give `PaymentRegistered` a dispatcher first

`PaymentRegisteredNotificationListener` holds its logic directly in the listener, against the
CLAUDE.md "add behaviour to the dispatcher, never a listener" rule. Before porting, refactor it in
the monolith into a `PaymentRegisteredNotificationDispatcher` with transport-only listeners
(matches the other six), verify green, **then** port. The `BankTransferDeadline.forPayment(payment,
settings)` calculation ports alongside it (needs `payments.created_at` +
`bank_transfer_auto_cancel_*` settings).

### 6.3 A flag to silence the monolith's notification Kafka listeners (cutover deploy 1)

Add `app.notification.kafka-listeners.enabled` (default `true`) as a **second**
`@ConditionalOnProperty` on the 7 `Kafka*NotificationListener` classes (they already carry
`app.domain-events.kafka.enabled=true`). This is what lets the cutover disable *only* notification
consumption on the monolith without touching any other Kafka listener, and — crucially — makes the
cutover reversible by a flag flip rather than a deploy revert. The in-process `@EventListener`
twins are already dead when Kafka is on, so they need no guard.

Small, safe, verifiable on its own: the monolith boots with the flag both ways.

### 6.4 Delete the module, collapse back to one datasource (cutover deploy 2, later)

Done only once notification-service has run in production and been verified (§7), as a separate,
low-risk dead-code removal:

- Delete `com.pilarestilo.notification.**` and `com.pilarestilo.notifications.**` (including the
  Kafka listeners and their new flag from 6.3).
- Delete `NotificationsPersistenceConfig`, `NotificationsFlywayMigrator`, and the
  `com.pilarestilo.notifications` carve-outs: the `excludeFilters` in `PersistenceConfig`'s
  `@EnableJpaRepositories`, the `NotificationsPersistenceConfig.ROOT_PACKAGE` line in
  `EntityScanCoversEveryModuleTest`.
- With the second `EntityManagerFactory` gone, `PersistenceConfig` and `EntityScanConfig` can revert
  to Boot's auto-configuration (`HibernateJpaAutoConfiguration` + default `com.pilarestilo` entity
  scan + `spring-boot-flyway` autoconfig). Delete `EntityScanCoversEveryModuleTest`
  (its reason for existing — "the main factory must not scan `com.pilarestilo.notifications`" — is
  gone). Keep `spring-boot-flyway` on the classpath (CLAUDE.md: without it migrations never run).
- Delete the notifications datasource envs from the backend service in `docker-compose.yml`
  (`APP_NOTIFICATION_DATASOURCE_*`) and `.env.example`; and now remove the messaging envs from the
  backend block too (they were kept there through deploy 1 as the rollback safety net).
- Delete backend-only notification tests / `NotificationsTestDatabase` / `NotificationsUseTheirOwnDatabaseIT`.
- Drop `spring-boot-starter-mail` from `backend/pom.xml` if nothing else uses `JavaMailSender`
  (grep first).
- **Keep** `infra/postgres/init/01-notifications-database.sh` (creates `pilarestilo_notifications`)
  and **do not** drop the old `notifications` table on the main DB yet — it is still the rollback
  path (`notification-database-split` memory).

### 6.4 Infra

- **Caddy:** a `@notification_reads` block (`method GET HEAD PUT`, `path /api/notifications
  /api/notifications/*`) → `reverse_proxy notification-service:8085 backend:8080` with the same
  `lb_policy first` fallback shape as the others. Placed before the generic `handle /api/*`.
- **prometheus.yml:** `job_name: notification_service`, target `notification-service:8085`,
  `metrics_path: /actuator/prometheus`.
- **docker-compose.yml:** `notification-service` under `profiles: ["microservices"]`, `SERVER_PORT
  "8085"`, shared datasource envs, `APP_NOTIFICATION_DATASOURCE_*`, `SYSTEM_SETTINGS_CRYPTO_SECRET`,
  `KAFKA_BOOTSTRAP_SERVERS`, `APP_DOMAIN_EVENTS_KAFKA_*`, `APP_NOTIFICATION_LISTENERS_ENABLED`, all
  `NOTIFICATION_PROVIDER` / `EMAIL_SMTP_*` / `SENDGRID_*` / `WHATSAPP_*` / `NOTIFICATION_N8N_*` env
  fallbacks (**copied** to this block — kept on the backend block through deploy 1 as the rollback
  path, removed from it in deploy 2), tracing envs, healthcheck on `/actuator/health`,
  `depends_on: postgres: service_healthy`. **Operational:** it needs the `kafka` profile up
  alongside `microservices` or it cannot resolve the broker — same failure mode already documented
  for the backend.
- **CI:** 5th entry in the services build/test matrix.
- **CLAUDE.md:** extend the "two codebases write the `orders` table" section to name
  `services/notification-service/` as a reader that must be kept in lockstep on `orders` /
  `order_items` / `users` / `payments` / `sales_documents` / `return_requests` / `system_settings`
  column changes. Add the module to the services list and the Caddy routing table.

---

## 7. Cutover plan — two deploys, reversible by flags

The first version of this spec chose a single atomic deploy (delete the module *and* enable the
service in one commit) because concurrent Kafka consumption on both sides would double-send every
notification, and there was no idempotency layer to absorb it. That is still true — but Kafka's own
guarantees plus a consumer-group offset pre-seed make the cutover safe **without** deleting the
monolith code in the same step, which buys back a fast flag-flip rollback.

### Why this is safe

- **No message loss.** Kafka retains every `pe.domain.*` event for 7 days regardless of consumers.
  A consumer that is briefly absent resumes from its committed offset and catches up. The only
  loss window is the *first* connection of the `pe-notification-service` group, where
  `auto.offset.reset` decides the start position — closed by pre-seeding (step 2 below).
- **No history replay.** `auto.offset.reset` is set to `latest` on the service's consumer, and the
  pre-seed commits offsets at the current log-end *before* the group ever has an active member, so
  the service processes only events published after the cutover — never the 7 days of retained
  `OrderCreated` behind it (which would mass-send confirmations for old orders).
- **Duplication is the one thing Kafka does not solve** (it is at-least-once — a listener that
  sends then dies before committing its offset already double-sends today, in the monolith too).
  The sequencing below keeps the double-consume window to the sub-second gap between two flag
  flips; a permanent fix (`sent_notifications` dedup table) is deferred, section 11.

### Deploy 1 — the cutover (reversible)

1. `notification-service` built and green locally against the full stack (`kafka` + `microservices`
   + `cache` + `observability` profiles), Kafka listeners still **disabled** by
   `APP_NOTIFICATION_LISTENERS_ENABLED=false`.
2. **Pre-seed the consumer group**, with the monolith still consuming notifications:
   ```
   docker compose exec kafka kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
     --group pe-notification-service --reset-offsets --to-latest --all-topics --execute
   ```
   The group now has committed offsets at the current log-end and no active members.
3. Deploy, in this order:
   a. new backend image with `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED=false` (6.3) — the 7
      `Kafka*NotificationListener` beans do not register. From here, nobody consumes notification
      events; they pile up in Kafka, retained.
   b. immediately after, notification-service with `APP_NOTIFICATION_LISTENERS_ENABLED=true` — it
      joins the pre-seeded group, drains the pile-up and every new event, **exactly once**, with a
      few minutes' delay at most (acceptable for a confirmation email).
   c. Caddy routes `/api/notifications*` (GET/HEAD/PUT) to `notification-service:8085 backend:8080`.
   d. the messaging envs (`EMAIL_SMTP_*` etc.) are **copied** to the notification-service compose
      block and **left on the backend block** as the rollback path.
4. **Rollback** (if the service misbehaves): flip both flags back —
   `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED=true` on the backend,
   `APP_NOTIFICATION_LISTENERS_ENABLED=false` on the service. The monolith code is still present, so
   it resumes consuming from its own group's committed offset. Seconds, no deploy revert.

### Deploy 2 — cleanup (after verification, low risk)

Once §7's verification passes in production and the service has run clean for a day: delete the
monolith notification module and collapse to one datasource (6.4). Pure dead-code removal — the
monolith already stopped using any of it in deploy 1.

### Mandatory verification (before deploy 1 reaches production)

A real order against the full local stack with the fixed test customer
(`test_estilo@pilarestilo.com`), confirming **exactly one** confirmation email arrives — not zero,
not two — and the in-app bell works end to end. Ley 21.398 requires that written confirmation:
zero emails is a compliance failure (the customer's right of withdrawal runs 90 days instead of
10), two emails means the cutover was not clean. Restated in section 9.

---

## 8. Error handling

Today's silent-by-design failure mode must not be inherited:

- **Senders:** each stays in its own try (an SMTP outage must not take WhatsApp down). A failure now
  increments `notification_send_failures_total{channel=...}` (Micrometer) as well as logging, so a
  dead channel is visible in Grafana instead of guessed at. If **no** channel accepts, that stays an
  ERROR log (as today).
- **Kafka consumer:** reuse the shared `DefaultErrorHandler` +
  `DeadLetterPublishingRecoverer` config verbatim — retry `retryMaxAttempts` (3) with
  `FixedBackOff` (1500 ms), then publish to `<topic>.dlt` (the `DomainEventTopics.deadLetterTopicFor`
  convention). A listener exception must not auto-ack. **This resolves open item #2 of the first
  spec** — the convention already exists, it is not invented here.
- **In-app write** (`NotificationRepositoryAdapter`): failures propagate — Kafka redelivers. This is
  the one database the service owns; there is no "the rest still works".
- **Read of a shared-DB aggregate that isn't there yet:** the dispatchers already handle this
  (`log.warn` + return, or send to `NotificationRecipient.unknown()`), because the same race exists
  today between a Kafka event and its aggregate's commit. Ported behaviour, not new.

---

## 9. Testing

- **Unit:** dispatchers, `NotificationComposer` / `EmailLayout`, every sender — ported with their
  existing backend tests, same coverage and style. No test hits real SMTP / SendGrid / Twilio.
- **Read-only mapping guard (Testcontainers):** one integration test that boots the service against
  a real `pilarestilo` schema (the monolith's Flyway output) and asserts every read-only entity
  validates — this is what turns a monolith column rename into a red local test instead of a
  production restart loop.
- **Kafka listeners:** unit-tested for delegation (each is a one-line delegate). An embedded-Kafka
  end-to-end IT was intended but `spring-kafka-test` is not in the offline build cache; the full
  publish→send→persist path is covered instead by the mandatory compose-stack verification below,
  against a real broker.
- **In-app own-database IT:** port `NotificationsUseTheirOwnDatabaseIT`'s intent — a saved row lands
  in `pilarestilo_notifications` and reads come back from it.
- **CI coverage gate:** same 0.50 line-coverage bundle minimum as the other four services.
- **Pre-production verification** (restated from section 7): real order, fixed test customer,
  exactly one email, working bell — non-negotiable before that deploy.

---

## 10. Schema-coupling risk and mitigation

1. **Map the minimum.** Only the columns in the 4.2 table, `insertable=false, updatable=false`.
   Fewer mapped columns, smaller target for a breaking migration.
2. **The 9.2 mapping guard test** makes a break a local red test.
3. **CLAUDE.md rule extended** (6.4) so `orders` / `users` / etc. changes are a known
   two-repo (now three-repo) change, same as `orders` already is for `order-service`.
4. **Reads are of settled state** — an order/payment/document/return that already reached a terminal
   or near-terminal status. Not stock, not price, not anything a second writer is racing.
5. **Deploy them together.** notification-service ships in the same pipeline run as the backend, so
   a coordinated migration + entity change lands as one deploy.

---

## 11. Open items resolved / remaining

**Resolved by this revision:**

- *Data access mechanism* → shared read-only DB (section 3).
- *DTO shape for an internal settings endpoint* → **no endpoint**; the service maps
  `system_settings` messaging columns directly and decrypts locally.
- *Dead-letter topic naming + retry threshold* → reuse `DomainEventTopics` /
  `KafkaDomainEventsProperties`: `<topic>.dlt`, 3 attempts, 1500 ms backoff.
- *Redis dependency* → dropped entirely.

**Deferred — its own future task (not in this extraction):**

- **`sent_notifications` idempotency table.** Kafka is at-least-once: a listener that sends a
  message then dies before committing its offset re-sends on redelivery — true in the monolith
  today, tolerated. A dedup table in `pilarestilo_notifications` keyed on
  `(reference_id, template_key, recipient)` with `INSERT … ON CONFLICT DO NOTHING` before each send
  would make every path exactly-once and also make any future cutover timing-proof. Left out here
  because it is a real feature with real edge cases: the fan-out templates
  (`PAYMENT_PROOF_SUBMITTED`, `RETURN_REQUESTED_STAFF` go to N reviewers — the key must include the
  recipient) and the null-`referenceId` templates (`WELCOME`, `DISCOUNT_CODE_ASSIGNED` — key on the
  user id instead). Needs its own design pass.

**Remaining for the implementation plan / later:**

- Whether `notification_send_failures_total` gets a Grafana panel now or later (not required for v1
  function).
- Exact package/name for the relocated order-status listener (6.1) — decide in the plan against
  where `UpdateOrderStatusUseCase` and `PaymentSubmitted` already live.
- The `notification` domain enum `NotificationChannelPreference` currently lives on
  `NotificationRecipient`; confirm `users.notification_channel_preference` column name during the
  read-entity task.
- Whether to keep `SendGridEmailNotificationSender` / `TwilioWhatsAppNotificationSender` at all, or
  drop them as unused (the shop's direction is SMTP + n8n + WhatsApp-simulated). Not required for
  the extraction; a separate cleanup.
