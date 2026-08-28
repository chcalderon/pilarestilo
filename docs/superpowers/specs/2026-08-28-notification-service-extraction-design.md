# Notification Service Extraction — Design Spec

**Date:** 2026-08-28
**Status:** approved by user, pending implementation plan

## Context and motivation

The backend monolith currently holds two full `DataSource`/`EntityManagerFactory`/`TransactionManager`
triples in one Spring Boot process: the main `pilarestilo` database and `pilarestilo_notifications`
(split into its own database on 2026-08-20, see the `notification-database-split` memory). This is
what produces the two `@Primary @ConfigurationProperties` beans in `PersistenceConfig.java`.

Despite living in one process, the split was done with real hexagonal discipline: only one class,
`NotificationRepositoryAdapter`, touches the notifications database, sitting behind domain ports
(`InAppNotificationRepository`, `InAppNotificationPort`). Triggering is already 100% event-driven
(a `Kafka*NotificationListener` per dispatcher). The database-per-service half of extraction is
already done — only the process boundary is missing.

This is the safest extraction candidate available in the codebase: unlike `order-service` (which
writes to the shared `orders` table alongside the monolith — the source of 5 documented bugs),
nothing else touches `pilarestilo_notifications`. There is no shared-table hazard here.

This extraction is also stated to be the first step of a longer-term direction: the monolith
disaggregating into microservices, module by module, until nothing is left. The order and
boundaries for the other 16+ hexagonal modules are explicitly **out of scope** for this spec — a
separate brainstorming session, when picked up. See the `monolith-dissolution-direction` memory.

## Prior art — how the four existing services are built

`services/product-service`, `inventory-service`, `order-service`, `payment-service` are each:
- An independent Maven project (own `pom.xml`, `groupId com.pilarestilo`, own package namespace —
  e.g. `com.pilarestilo.orderservice`, **not** `com.pilarestilo.order`). No shared compiled module
  with `backend/`. Extracting code into one of these means porting/reimplementing it, not moving a
  package.
- Spring Boot 4.1.1, Java 25, multi-stage `Dockerfile` (`maven:3.9-eclipse-temurin-25` build stage →
  `eclipse-temurin:25-jre-alpine` runtime stage), own `EXPOSE` port (8081-8084 used so far).
- Connected to the **same shared** `pilarestilo` database as the monolith (`SPRING_DATASOURCE_URL:
  jdbc:postgresql://postgres:5432/${POSTGRES_DB:-pilarestilo}`) — none of the four have their own
  database. This is different from notification, which already has a dedicated database.
- Wired into the full observability stack: `/actuator/prometheus` scraped by
  `infra/monitoring/prometheus/prometheus.yml` as a new `job_name`; `APP_TRACING_ENABLED` /
  `APP_TRACING_OTLP_ENDPOINT` / `APP_TRACING_SAMPLING_PROBABILITY` sending spans through the OTel
  Collector to Tempo, same as the backend.
- Behind Caddy, routed by HTTP method and path prefix (`GET/HEAD /api/products*` →
  `product-service:8081`, etc.); everything else (`POST/PATCH/DELETE /api/*`) still goes to
  `backend:8080`.
- Internal service-to-service calls use a shared-secret header token
  (`APP_ORDER_INTERNAL_TOKEN` / `APP_PAYMENT_INTERNAL_TOKEN`), set from the same env var the
  monolith uses to call out (`APP_ORDER_REMOTE_SERVICE_TOKEN` etc.).
- `profiles: ["microservices"]` in `infra/docker-compose.yml`; only started when that profile is
  active. `depends_on: postgres: condition: service_healthy`.
- Sonar/Jacoco: `sonar.coverage.jacoco.xmlReportPaths` property in the pom, feeds the same
  `sonar-scan.sh` sweep as everything else; part of the CI matrix already covering four services.

None of the four use Redis today. Notification-service would be the first.

## 1. Architecture and components

New independent project: `services/notification-service/`, following the exact skeleton above.
Port **8085** (next free in the 8081-8084 sequence).

**Moves out of the backend, completely, with no copy left behind:**
- The 7 `*NotificationDispatcher` classes (`OrderNotificationDispatcher`,
  `PaymentNotificationDispatcher`, `BillingNotificationDispatcher`, `DiscountNotificationDispatcher`,
  `ReturnNotificationDispatcher`, `UserNotificationDispatcher`) plus `NotificationComposer` and
  `EmailLayout`.
- All senders: `SmtpEmailNotificationSender`, `SendGridEmailNotificationSender`,
  `TwilioWhatsAppNotificationSender`, `SimulatedWhatsAppNotificationSender`,
  `N8nWebhookNotificationSender`, `InAppNotificationSender`, `LogNotificationSender`,
  `SystemSettingsNotificationSender`.
- The 7 `Kafka*NotificationListener` classes. The in-process (non-Kafka) `@EventListener` variants
  are **not** ported — see the Kafka-only decision below.
- `NotificationController` and its four use cases (`GetNotificationsUseCase`,
  `GetUnreadCountUseCase`, `MarkNotificationReadUseCase`, `MarkAllNotificationsReadUseCase`).
- All persistence: `NotificationEntity`, `NotificationJpaRepository`,
  `NotificationsPersistenceConfig`, `NotificationsFlywayMigrator` — pointed at
  `pilarestilo_notifications`, which already exists in production.

**Simplification available once standalone:** with only one database in this service, the custom
"a migrator bean that is deliberately not a `Flyway` bean" workaround (needed in the monolith only
to avoid `FlywayAutoConfiguration` backing off the *main* migrations) is no longer necessary — a
normal `spring-boot-flyway`-autoconfigured `Flyway` bean works, because there is no second
DataSource to protect.

**Stays in the backend:** nothing notification-specific. The backend continues publishing domain
events to Kafka exactly as today (`KafkaDomainEventPublisher`), and gains one new internal
read-only endpoint (see below).

**Using the infrastructure that already runs, not reinventing anything:**

| Existing piece | How notification-service uses it |
|---|---|
| Kafka | Sole trigger — consumes the same topics already published (OrderCreated, PaymentConfirmed, etc.) as another consumer group |
| Prometheus | Exposes `/actuator/prometheus`; new `notification_service` job added to `prometheus.yml`, same pattern as the other four |
| Tempo + OTel Collector | Same `APP_TRACING_ENABLED`/`APP_TRACING_OTLP_ENDPOINT` — an event's path from publish to send/store becomes a traceable span for the first time |
| Redis | First service to use it besides the backend: caches the `system_settings.notification_providers` lookup with a short TTL, gated by the existing `APP_CACHE_REDIS_ENABLED` flag. Optional — falls back to calling the backend directly if Redis is off |
| Postgres | Its own database, `pilarestilo_notifications`, already exists |
| Caddy | `GET`, `HEAD`, **and `PATCH`** on `/api/notifications*` route directly to the new service (not just reads — see routing note below) |
| SonarQube + Jacoco | Same `sonar.coverage.jacoco.xmlReportPaths` pattern, enters the existing scan |
| CI | Fifth entry in the existing services test/build matrix |

**Routing note:** product/inventory/order/payment only extracted *reads* — writes still land on
shared tables the monolith keeps governing. Notification, with a clean cut and its own database,
extracts both: `PATCH /api/notifications/:id/read` and `/read-all` go to the new service too, since
nothing else in the system ever needs to write there.

**Operational note:** because triggering is 100% Kafka, `notification-service` needs the `kafka`
profile active alongside `microservices`. Running `microservices` alone leaves it unable to resolve
the `kafka` host — the same failure mode already documented for the backend, now applying to a new
consumer.

## 2. Data flow

**Trigger (event → notification):**
```
Monolith (OrderCreated, PaymentConfirmed, etc.)
  -> Kafka topic (unchanged)
  -> notification-service: Kafka*NotificationListener
  -> *NotificationDispatcher composes the message (NotificationComposer/EmailLayout)
  -> looks up active channels (see below)
  -> each enabled sender, in its own try (an SMTP outage must not block WhatsApp)
  -> NotificationRepositoryAdapter persists the in-app record to pilarestilo_notifications
```

**Active-channel lookup (new, on every event dispatched):**
```
notification-service -> Redis: GET notif:providers
  hit  -> use that list
  miss -> GET http://backend:8080/api/internal/notification-settings (header X-Service-Token)
          -> store in Redis with a 60s TTL
          -> use that list
```
60s TTL: an admin toggling this is rare (an occasional panel change), so up to a minute of lag
between "saved in the panel" and "the next event respects it" is acceptable, and avoids hitting the
backend on every notification.

**In-app reads (the bell, live):**
```
Frontend -> Caddy -> notification-service (GET /api/notifications, GET /unread-count)
Frontend -> Caddy -> notification-service (PATCH /:id/read, PATCH /read-all)
```
Never touches the monolith — the new service fully owns this data.

## 3. Cutover plan

Central risk of a clean cut: while both the monolith and the new service consume the same Kafka
topic at the same time, every event fires twice — a customer receives a duplicated email. This
cannot be gradual; it has to be atomic.

**Sequence within a single deploy:**
1. `notification-service` built and tested locally against the full stack (Kafka + microservices +
   cache profiles), with its Kafka listeners **disabled** until step 3.
2. In the same deploy: the backend gains the internal endpoint
   `GET /api/internal/notification-settings` (must exist before the new service's first event
   arrives, or that first lookup fails).
3. In the same deploy: the 7 `Kafka*NotificationListener` classes (and the rest of the module) are
   deleted from the backend **and** `notification-service`'s listeners are enabled — one commit, one
   deploy, no window where both listen at once.
4. Caddy's routing changes in the same deploy: `/api/notifications*` (GET and PATCH) now points at
   the new service.
5. Mandatory pre-production verification, not optional: a real order against the full local stack,
   using the fixed test customer (`test_estilo@pilarestilo.com`), confirming **exactly one** email
   arrives — not zero, not two — and that the in-app bell works end to end. This is not just UX:
   Ley 21.398 requires that written confirmation, so a botched cutover here carries legal weight,
   not only a cosmetic one.

**Accepted cost of "clean cut":** no toggle to fall back to instantly. If something breaks
post-cutover, rollback means reverting the whole deploy (the monolith regains the code, the new
container stops) — not flipping a flag. This was chosen deliberately over keeping dead code as a
safety net, to avoid two copies of the same logic drifting apart.

## 4. Error handling

Today's failure mode is silent by design and that should not be inherited: `InAppNotificationSender`
logs and swallows; if the transaction manager were ever misconfigured, a notification would simply
not exist, with nothing but a WARN. In the new service:

- Each sender stays in its own try (already true today — an SMTP outage must not take WhatsApp
  down), but a failure now increments a Prometheus counter
  (`notification_send_failures_total{channel=...}`), not just a log line. With Grafana already
  provisioned, a dead channel becomes visible instead of guessed at.
- **Active-channel lookup** (Redis → backend): if the internal backend call fails and Redis has
  nothing cached, the code does not silently assume "no channels active" — it retries with a short
  backoff (2-3 attempts), and on continued failure the event is marked pending/retryable rather than
  dropped. Losing an event here means a customer never gets their purchase confirmation.
- **Kafka**: a listener exception must not auto-acknowledge the offset — Kafka redelivers. A retry
  limit plus a dead-letter topic is needed so a genuinely malformed message doesn't loop forever
  (unresolved today, since this code has never run as an isolated consumer before).
- **In-app write** (`NotificationRepositoryAdapter`): failures here must be loud — exception
  propagates, Kafka redelivers. This is the one database this service owns; there is no "the rest
  still works" to fall back on.

## 5. Testing

- **Unit**: dispatchers, `NotificationComposer`/`EmailLayout`, and every sender — ported with their
  existing backend tests, same coverage, same style. No test hits real SMTP/SendGrid/Twilio; they
  stay mocked exactly as today.
- **Integration (Testcontainers)**: real Postgres for `pilarestilo_notifications` and real Kafka
  consuming a test event end to end (publish `OrderCreated` → assert the right sender was invoked
  and the in-app row landed). The `system_settings` lookup is stubbed with WireMock — no need to run
  the full backend just for this.
- **Internal-endpoint contract**: unlike every prior extraction (which has zero tests crossing the
  monolith/service boundary), this one has real HTTP between two independent codebases. A contract
  test on each side against the same `GET /api/internal/notification-settings` response shape
  doesn't eliminate drift risk, but it's strictly more than any other extraction has today.
- **CI coverage gate**: same as the other four services (Jacoco, the existing 0.50 line-coverage
  bar), joins the existing matrix rather than inventing a new one.
- **Pre-production verification** (restated from the cutover plan): a real order against the full
  local stack, fixed test customer, exactly one email, working bell — non-negotiable before that
  deploy.

## Open items for the implementation plan

- Exact DTO shape for `GET /api/internal/notification-settings`.
- Dead-letter topic naming convention and retry-count threshold for the Kafka consumer.
- Whether `notification_send_failures_total` needs a Grafana panel added now or later (not
  required for v1 functionality).
