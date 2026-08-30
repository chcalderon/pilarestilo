# Notification Service Extraction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the whole notification module out of the backend monolith into a standalone
`services/notification-service` (Kafka-triggered, port 8085), so the monolith stops owning a second
database.

**Architecture:** notification-service is the 5th extracted service, built on the exact skeleton of
`services/order-service`. It **owns** `pilarestilo_notifications` (read-write, normal Flyway) and
reads the shared `pilarestilo` database **read-only** — mapping only the columns its dispatchers and
composer need, exactly as `order-service` maps a subset of `system_settings`. It consumes the
existing `pe.domain.*` Kafka topics as a new consumer group. No internal HTTP API, no Redis.
Cutover is two deploys, reversible by flags: deploy 1 enables the service and gates the monolith's
notification consumers off (its code stays); deploy 2, after production verification, deletes the
dead module. A consumer-group offset pre-seed prevents history replay.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Spring Kafka, Spring Data JPA, PostgreSQL, jjwt 0.12.6,
Micrometer/Prometheus, OpenTelemetry, Testcontainers, Maven. Docker Compose + Caddy for the stack.

**Spec:** `docs/superpowers/specs/2026-08-28-notification-service-extraction-design.md` — read it
first, especially §0 (why the first spec was wrong) and §3 (the read-only-DB decision). This plan
argues from that spec.

## Global Constraints

- **Package namespace:** `com.pilarestilo.notificationservice.*` — NOT `com.pilarestilo.notification`.
  No compiled module is shared with `backend/`; every ported class is re-created here.
- **Spring Boot 4.1.1**, `spring-boot-starter-parent`; Java `<release>25</release>`. Pin
  `netty.version` 4.2.17.Final, `postgresql.version` 42.7.13, `jackson-bom.version` 3.1.6,
  `jackson-2-bom.version` 2.21.6, `log4j2.version` 2.25.5, `opentelemetry.version` 1.62.0 (copy the
  `<properties>` block from `services/order-service/pom.xml` verbatim, including its CVE comment).
- **Jackson 3:** `tools.jackson.databind.*`, annotations on `com.fasterxml.jackson.annotation`.
- **Read-only shared DB:** every entity mapping a `pilarestilo` table is annotated
  `@Column(..., insertable = false, updatable = false)` on every field, no `@GeneratedValue`, no
  write methods on its repository. `spring.jpa.hibernate.ddl-auto: validate`, `open-in-view: false`.
- **The one owned DB** (`pilarestilo_notifications`) uses a normal `spring-boot-flyway`
  auto-configured `Flyway` bean — the "not a Flyway bean" workaround does NOT come across; it only
  existed to protect a second datasource in the monolith.
- **Port 8085.** `SERVER_PORT` env, default 8085.
- **Kafka consumer group:** `pe-notification-service` (distinct from the backend's
  `pe-backend-domain-events`). `USE_TYPE_INFO_HEADERS=false` on the consumer — the service declares
  its own thin event records and Jackson binds by field name, so it never needs the monolith's
  event classes on its classpath.
- **No Redis. No internal HTTP API.** Every cross-data lookup is a local DB read.
- **Coverage gate:** jacoco `check` at `verify`, BUNDLE LINE COVEREDRATIO minimum **0.50** (copy
  order-service's plugin config).
- **Do NOT drop** the old `notifications` table on the main DB, and **keep**
  `infra/postgres/init/01-notifications-database.sh`. Both are the rollback path.
- **Two-deploy reversible cutover:** Task 13 (monolith flag) ships anytime. Task 14 is deploy 1 —
  service on, monolith consumers gated off, code kept. Task 16 is deploy 2 — delete the dead module,
  only after Task 15 verifies deploy 1 in production. Rollback at any point = flip the two flags
  (`APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED`, `APP_NOTIFICATION_LISTENERS_ENABLED`), no deploy.
- Work on `develop`. Verify against the local Docker stack before any push to `master`
  (`verify-in-local-docker-before-pushing` memory). Frequent commits, one per task.

## File Structure

```
services/notification-service/
  pom.xml
  Dockerfile
  src/main/resources/
    application.yml
    db/migration/V1__notifications.sql          # copied from backend db/migration-notifications
    email/pilar-estilo-logo.png                 # copied from backend resources
  src/main/java/com/pilarestilo/notificationservice/
    NotificationServiceApplication.java
    config/
      SecurityConfig.java
      NotificationsDbConfig.java                # owned DB: DataSource + Flyway + EMF + tx manager
      ReadOnlyDbConfig.java                     # shared DB (RO): DataSource + EMF + tx manager
      KafkaConsumerConfig.java                  # ported from shared/infrastructure/kafka
    auth/                                        # copied from order-service/auth
      JwtAuthenticationFilter.java  JwtTokenProvider.java  AuthenticatedUser.java  UserRole.java
    events/                                      # thin event records (own package)
      OrderCreatedEvent.java  OrderStatusChangedEvent.java  PaymentConfirmedEvent.java
      PaymentRegisteredEvent.java  PaymentSubmittedEvent.java  PaymentRejectedEvent.java
      SalesDocumentIssuedEvent.java  ReturnRequestedEvent.java  ReturnApprovedEvent.java
      RefundRegisteredEvent.java  UserRegisteredEvent.java  DiscountCodeAssignedEvent.java
      DomainEventTopics.java
    domain/
      model/     InAppNotification  NotificationMessage  NotificationRecipient
      enums/     NotificationType
      ports/     InAppNotificationRepository  InAppNotificationPort  NotificationSender
                 OrderReadPort  CustomerReadPort  PaymentReadPort  SalesDocumentReadPort
                 ReturnReadPort  MessagingSettingsPort  PaymentReviewerReadPort
      view/      OrderView  OrderItemView  CustomerView  PaymentView  SalesDocumentView
                 ReturnView  MessagingSettings           # immutable records the composer renders
    application/
      NotificationComposer  EmailLayout
      OrderNotificationDispatcher  PaymentNotificationDispatcher  PaymentRegisteredNotificationDispatcher
      BillingNotificationDispatcher  DiscountNotificationDispatcher  ReturnNotificationDispatcher
      UserNotificationDispatcher
      BankTransferDeadline
      usecases/  GetNotificationsUseCase  GetUnreadCountUseCase
                 MarkNotificationReadUseCase  MarkAllNotificationsReadUseCase
      dto/       InAppNotificationDto
    infrastructure/
      web/       NotificationController
      adapters/  EmailFormat  SmtpEmailNotificationSender  SendGridEmailNotificationSender
                 TwilioWhatsAppNotificationSender  SimulatedWhatsAppNotificationSender
                 N8nWebhookNotificationSender  InAppNotificationSender  LogNotificationSender
                 SystemSettingsNotificationSender  SystemSettingsCryptoService
      persistence/
        owned/     NotificationEntity  NotificationJpaRepository  NotificationRepositoryAdapter
        readonly/  SystemSettingsRoEntity + repo + MessagingSettingsAdapter
                   OrderRoEntity  OrderItemRoEntity  + repo + OrderReadAdapter
                   UserRoEntity   + repo + CustomerReadAdapter + PaymentReviewerReadAdapter
                   PaymentRoEntity + repo + PaymentReadAdapter
                   SalesDocumentRoEntity + repo + SalesDocumentReadAdapter
                   ReturnRequestRoEntity + repo + ReturnReadAdapter
    metrics/     NotificationMetrics    # notification_send_failures_total{channel}
```

Monolith changes (Tasks 1, 2, 13, 16):
```
backend/src/main/java/com/pilarestilo/
  payment/  MarkOrderUnderReviewOnPaymentSubmittedHandler (+ 2 listeners)             # Task 1
  notification/application/  PaymentRegisteredNotificationDispatcher                  # Task 2
  notification/infrastructure/listeners/kafka/*  + app.notification.kafka-listeners.enabled flag  # Task 13
  notification/**, notifications/**                                                   # DELETED Task 16
  shared/infrastructure/config/PersistenceConfig.java, EntityScanConfig.java          # DELETED Task 16
```

---

## Task 1: Relocate the `PaymentSubmitted` → order-status write out of notifications (monolith)

**Why first:** `PaymentNotificationDispatcher.onPaymentSubmitted` calls
`updateOrderStatusUseCase.execute(order.getId(), OrderStatus.PAYMENT_UNDER_REVIEW)`. That is
order-domain behaviour and cannot travel to a read-only service. Moving it now, on its own, is a
pure internal refactor that de-risks the cutover.

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/payment/infrastructure/listeners/MarkOrderUnderReviewOnPaymentSubmittedListener.java`
- Create: `backend/src/main/java/com/pilarestilo/payment/infrastructure/listeners/kafka/KafkaMarkOrderUnderReviewOnPaymentSubmittedListener.java`
- Create: `backend/src/main/java/com/pilarestilo/payment/application/MarkOrderUnderReviewOnPaymentSubmittedHandler.java`
- Modify: `backend/src/main/java/com/pilarestilo/notification/application/PaymentNotificationDispatcher.java` (drop the `updateOrderStatusUseCase` field and the `execute(...)` call; keep the reviewer email)
- Modify: `backend/src/test/java/.../PaymentNotificationDispatcherTest.java` (drop the status-transition assertions)
- Test: `backend/src/test/java/com/pilarestilo/payment/application/MarkOrderUnderReviewOnPaymentSubmittedHandlerTest.java`

**Interfaces:**
- Consumes: `com.pilarestilo.payment.domain.events.PaymentSubmitted(paymentId, occurredAt)` (verify
  the exact record components), `com.pilarestilo.payment.domain.ports.PaymentRepository`,
  `com.pilarestilo.order.domain.ports.OrderRepository`,
  `com.pilarestilo.order.application.usecases.UpdateOrderStatusUseCase`,
  `com.pilarestilo.order.domain.enums.OrderStatus`.
- Produces: `MarkOrderUnderReviewOnPaymentSubmittedHandler.handle(UUID paymentId)` — reads the
  payment, resolves its order, and if the order status is `PENDING_PAYMENT` or `CREATED` moves it to
  `PAYMENT_UNDER_REVIEW` via `UpdateOrderStatusUseCase`.

- [ ] **Step 1: Write the failing test**

```java
// MarkOrderUnderReviewOnPaymentSubmittedHandlerTest.java
@Test
void moves_a_pending_payment_order_to_under_review() {
    UUID paymentId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Payment payment = mock(Payment.class);
    when(payment.getOrderId()).thenReturn(orderId);
    Order order = mock(Order.class);
    when(order.getId()).thenReturn(orderId);
    when(order.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);
    when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    handler.handle(paymentId);

    verify(updateOrderStatusUseCase).execute(orderId, OrderStatus.PAYMENT_UNDER_REVIEW);
}

@Test
void leaves_an_already_advanced_order_alone() {
    // order.getStatus() == OrderStatus.PAID -> verifyNoInteractions(updateOrderStatusUseCase)
}
```

- [ ] **Step 2: Run it, confirm it fails** — `cd backend && mvn test -Dtest=MarkOrderUnderReviewOnPaymentSubmittedHandlerTest` → FAIL (class not found).

- [ ] **Step 3: Implement the handler** — lift the exact guard + transition from
  `PaymentNotificationDispatcher.onPaymentSubmitted` (the `order.getStatus() == PENDING_PAYMENT ||
  == CREATED` check, then `updateOrderStatusUseCase.execute(order.getId(), PAYMENT_UNDER_REVIEW)`).
  `@Service`, constructor-injected ports.

- [ ] **Step 4: Add the two transport listeners** — copy the shape of an existing paired listener
  in `payment/infrastructure/listeners/` (in-process `@EventListener` + `@ConditionalOnProperty`
  Kafka twin using `containerFactory = "domainEventsKafkaListenerContainerFactory"` and
  `topics = "#{@domainEventTopics.topicFor('PaymentSubmitted')}"`). Both just call
  `handler.handle(event.paymentId())`.

- [ ] **Step 5: Strip the write from `PaymentNotificationDispatcher`** — remove the
  `UpdateOrderStatusUseCase` constructor arg + field + the `if (status...) updateOrderStatusUseCase...`
  block inside `onPaymentSubmitted`. Keep `notifyReviewers(composer.paymentProofSubmitted(...))`.
  Update `PaymentNotificationDispatcherTest` to drop the transition assertions.

- [ ] **Step 6: Run the payment + notification suites** —
  `cd backend && mvn test -Dtest='*Payment*,*Notification*'` → all PASS.

- [ ] **Step 7: Commit** — `git add -A && git commit -m "refactor(payment): move PaymentSubmitted order-status transition out of the notification dispatcher"`

---

## Task 2: Give `PaymentRegistered` a real dispatcher (monolith)

**Why:** `PaymentRegisteredNotificationListener` holds its logic in the listener, against the
CLAUDE.md rule "add behaviour to the dispatcher, never a listener". The port in Task 11 assumes
every event routes through a dispatcher; make that true in the monolith first, verify green, then
port cleanly.

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/notification/application/PaymentRegisteredNotificationDispatcher.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/infrastructure/listeners/kafka/KafkaPaymentRegisteredNotificationListener.java` (already exists — reduce it to transport-only)
- Modify: `backend/src/main/java/com/pilarestilo/notification/infrastructure/listeners/PaymentRegisteredNotificationListener.java` → transport-only, delegates to the dispatcher
- Modify: `KafkaPaymentRegisteredNotificationListener.java` → transport-only
- Test: `backend/src/test/java/com/pilarestilo/notification/application/PaymentRegisteredNotificationDispatcherTest.java`

**Interfaces:**
- Produces: `PaymentRegisteredNotificationDispatcher.onPaymentRegistered(PaymentRegistered event)` —
  identical behaviour to today's listener body (TRANSFER-only guard, `BankTransferDeadline`, bank
  snapshot from the payment, `composer.transferInstructions`).

- [ ] **Step 1: Write the failing test** — port the existing
  `PaymentRegisteredNotificationListenerTest` assertions onto a dispatcher-shaped test (mock
  `PaymentRepository`, `OrderRepository`, `UserRepository`, `SystemSettingsRepository`,
  `NotificationSender`, `NotificationComposer`; assert `transferInstructions` composed + sent for a
  TRANSFER payment, nothing for a non-TRANSFER).

- [ ] **Step 2: Run it, confirm it fails** — `mvn test -Dtest=PaymentRegisteredNotificationDispatcherTest` → FAIL.

- [ ] **Step 3: Move the body** — copy `onPaymentRegistered`'s body verbatim from the listener into
  `PaymentRegisteredNotificationDispatcher` (`@Service`), keeping `@Transactional(readOnly = true)`.

- [ ] **Step 4: Reduce both listeners to transport** — each becomes a one-line delegate
  (`dispatcher.onPaymentRegistered(event)`), matching `OrderNotificationListener` /
  `KafkaOrderNotificationListener`.

- [ ] **Step 5: Run** — `mvn test -Dtest='*PaymentRegistered*,*Notification*'` → PASS.

- [ ] **Step 6: Commit** — `git commit -m "refactor(notification): PaymentRegistered behaviour moves to a dispatcher, listeners become transport"`

---

## Task 3: Scaffold `services/notification-service`

**Files:**
- Create: `services/notification-service/pom.xml`, `Dockerfile`, `.dockerignore`
- Create: `src/main/java/com/pilarestilo/notificationservice/NotificationServiceApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/java/com/pilarestilo/notificationservice/config/SecurityConfig.java`
- Copy from `services/order-service/src/main/java/com/pilarestilo/orderservice/auth/` →
  `com/pilarestilo/notificationservice/auth/`: `JwtAuthenticationFilter`, `JwtTokenProvider`,
  `AuthenticatedUser`, `UserRole` (rename package, drop the `X-Service-Token` internal-token path —
  notification-service exposes only customer-facing reads, no internal endpoints; keep only the
  Bearer-JWT path). `AuthenticatedUser` keeps `id()`, `email()`, `role()`.
- Test: `src/test/java/com/pilarestilo/notificationservice/ContextLoadsTest.java`,
  `SecurityConfigTest.java`

**Interfaces:**
- Produces: a bootable Spring Boot app on 8085; `AuthenticatedUser` record `(UUID id, String email,
  UserRole role, boolean internal)`; `SecurityConfig` — `/actuator/**` + `/error` +
  `GET /api/notifications/_health` permitAll, everything else authenticated, stateless, CSRF off,
  `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`.

- [ ] **Step 1: pom.xml** — start from `services/order-service/pom.xml`. Change `<artifactId>` to
  `notification-service`, `<name>`, `<description>`. Keep every dependency it has, and ADD:
  `spring-boot-starter-mail` (JavaMailSender for SMTP), `spring-kafka`, `spring-boot-kafka`
  (Boot 4 needs the module or `KafkaProperties` is absent — see CLAUDE.md). Keep
  `spring-boot-restclient` (SendGrid/Twilio/n8n use `RestClient.Builder`). Keep the jacoco `check`
  0.50 rule and the `security` profile block verbatim.

- [ ] **Step 2: Dockerfile** — copy `services/order-service/Dockerfile`, change `EXPOSE 8083` → `EXPOSE 8085`.

- [ ] **Step 3: `NotificationServiceApplication`** — `@SpringBootApplication(exclude =
  UserDetailsServiceAutoConfiguration.class)`, `main` runs `SpringApplication.run`.

- [ ] **Step 4: `application.yml`** — `server.port: ${SERVER_PORT:8085}`,
  `spring.application.name: notification-service`, `spring.mvc.problemdetails.enabled: true`,
  the `management.*` + `management.tracing.export` + `management.opentelemetry.tracing.export.otlp`
  block copied from order-service, `app.jwt.secret: ${JWT_SECRET:...}`,
  `app.system-settings.crypto-secret: ${SYSTEM_SETTINGS_CRYPTO_SECRET:${JWT_SECRET:...}}`.
  **No** `spring.datasource` block yet (added in Tasks 4 and 6 — the two datasources are both
  hand-built beans). Add `management.endpoints.web.exposure.include: health,info,metrics,prometheus`.

- [ ] **Step 5: auth package** — copy the 4 files, rename package to
  `com.pilarestilo.notificationservice.auth`, delete `tryAuthenticateInternalToken` and the
  `@Value("${app.order.internal-token:}")` arg from `JwtAuthenticationFilter`. `JwtTokenProvider`
  reads `app.jwt.secret`.

- [ ] **Step 6: `SecurityConfig`** — copy order-service's, change the request matchers to:
  `.requestMatchers("/actuator/**", "/error").permitAll()`,
  `.requestMatchers(HttpMethod.GET, "/api/notifications/_health").permitAll()`,
  `.anyRequest().authenticated()`.

- [ ] **Step 7: `ContextLoadsTest`** — `@SpringBootTest` with datasource auto-config excluded for
  now (`@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.jpa.autoconfigure.HibernateJpaAutoConfiguration")`),
  assert the context starts. `SecurityConfigTest` — MockMvc, `GET /api/notifications` without a
  token → 401, `GET /api/notifications/_health` → 200 (add a trivial health `@RestController`).

- [ ] **Step 8: Run** — `cd services/notification-service && mvn test` → PASS.

- [ ] **Step 9: Commit** — `git commit -m "feat(notification-service): scaffold — Boot 4.1 app, JWT security, health, port 8085"`

---

## Task 4: Owned database — the in-app notification store

**Files:**
- Create: `src/main/resources/db/migration/V1__notifications.sql` — **copy verbatim** from
  `backend/src/main/resources/db/migration-notifications/V1__notifications.sql`
- Create: `config/NotificationsDbConfig.java`
- Create: `domain/model/InAppNotification.java`, `domain/enums/NotificationType.java`,
  `domain/ports/InAppNotificationRepository.java`
- Create: `infrastructure/persistence/owned/NotificationEntity.java`,
  `NotificationJpaRepository.java`, `NotificationRepositoryAdapter.java`
- Test: `src/test/java/.../NotificationStoreIT.java` (Testcontainers)

**Interfaces:**
- Produces: `InAppNotificationRepository` (same 7 methods as the monolith's port — `save`,
  `findByUserId`, `findRecentByUserId`, `countUnreadByUserId`, `findByIdAndUserId`, `markAsRead`,
  `markAllAsRead`); `InAppNotification` domain model (`create(userId, type, title, body, metadata)`
  + getters + `isRead()`); `NotificationType` enum (7 constants:
  `DISCOUNT_CODE_ASSIGNED, ORDER_CONFIRMED, PAYMENT_RECEIVED, ORDER_PREPARING, ORDER_SHIPPED,
  ORDER_DELIVERED, WELCOME`).

- [ ] **Step 1: Copy the domain + migration** — `InAppNotification`, `NotificationType`,
  `InAppNotificationRepository`, `V1__notifications.sql` — verbatim, package renamed.

- [ ] **Step 2: `NotificationsDbConfig`** — hand-built because there will be a second datasource
  (Task 6) and Boot backs off its auto-config once any `EntityManagerFactory` bean exists. Pattern:

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.pilarestilo.notificationservice.infrastructure.persistence.owned",
    entityManagerFactoryRef = "notificationsEntityManagerFactory",
    transactionManagerRef = "notificationsTransactionManager")
public class NotificationsDbConfig {

    @Bean @Primary @ConfigurationProperties("app.notification.datasource")
    DataSourceProperties notificationsDataSourceProperties() { return new DataSourceProperties(); }

    @Bean @Primary @ConfigurationProperties("app.notification.datasource.hikari")
    DataSource notificationsDataSource(@Qualifier("notificationsDataSourceProperties") DataSourceProperties p) {
        return p.initializeDataSourceBuilder().build();
    }

    // Standalone: a NORMAL Flyway bean is fine — there is no second migration history to protect.
    @Bean(initMethod = "migrate")
    Flyway notificationsFlyway(@Qualifier("notificationsDataSource") DataSource ds) {
        return Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
    }

    @Bean @Primary @DependsOn("notificationsFlyway")
    LocalContainerEntityManagerFactoryBean notificationsEntityManagerFactory(
            @Qualifier("notificationsDataSource") DataSource ds,
            JpaProperties jpaProperties, HibernateProperties hibernateProperties) {
        // same body as backend NotificationsPersistenceConfig.notificationsEntityManagerFactory,
        // packagesToScan = the owned package, persistenceUnitName = "notifications"
    }

    @Bean @Primary
    PlatformTransactionManager notificationsTransactionManager(
            @Qualifier("notificationsEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
```

  Config keys in `application.yml`:
  `app.notification.datasource.url: ${APP_NOTIFICATION_DATASOURCE_URL:jdbc:postgresql://localhost:5432/pilarestilo_notifications}`,
  `.username`, `.password`, plus `spring.jpa.hibernate.ddl-auto: validate`,
  `spring.jpa.open-in-view: false`, the `"[time_zone]": UTC` property from order-service.

- [ ] **Step 3: `NotificationEntity` + `NotificationJpaRepository`** — copy verbatim from
  `com.pilarestilo.notifications.persistence.*`, rename package. Import `NotificationType` from the
  new domain package. Keep `@JdbcTypeCode(SqlTypes.JSON)` on `metadata`.

- [ ] **Step 4: `NotificationRepositoryAdapter`** — copy from the monolith, but replace every
  `@Transactional(NotificationsPersistenceConfig.TRANSACTION_MANAGER)` with plain
  `@Transactional("notificationsTransactionManager")` (still qualified — there are two tx managers).

- [ ] **Step 5: Write `NotificationStoreIT`** — `@Testcontainers`, one `postgres:16` container,
  `@DynamicPropertySource` points `app.notification.datasource.*` at it. Assert: `save` then
  `countUnreadByUserId` == 1; `markAsRead` flips `isRead()`; `findByIdAndUserId` scopes by user.

- [ ] **Step 6: Run** — `mvn test -Dtest=NotificationStoreIT` → PASS.

- [ ] **Step 7: Commit** — `git commit -m "feat(notification-service): owned pilarestilo_notifications store with normal Flyway"`

---

## Task 5: In-app read API (`/api/notifications`)

**Files:**
- Create: `application/dto/InAppNotificationDto.java`, `application/usecases/{GetNotifications,
  GetUnreadCount,MarkNotificationRead,MarkAllNotificationsRead}UseCase.java`
- Create: `domain/ports/InAppNotificationPort.java`,
  `infrastructure/adapters/InAppNotificationSender.java` (implements the port, writes via the
  repository), `infrastructure/adapters/LogNotificationSender.java`
- Create: `domain/model/NotificationMessage.java`, `domain/model/NotificationRecipient.java`
  (needed by `NotificationSender`; port verbatim, rename package)
- Create: `domain/ports/NotificationSender.java`
- Create: `infrastructure/web/NotificationController.java`
- Test: `NotificationControllerTest.java` (MockMvc slice)

**Interfaces:**
- Consumes: `InAppNotificationRepository` (Task 4), `AuthenticatedUser` (Task 3).
- Produces: `GET /api/notifications?page&size&recentOnly`, `GET /api/notifications/unread-count`,
  `PUT /api/notifications/{id}/read`, `PUT /api/notifications/read-all` — all
  `@PreAuthorize("isAuthenticated()")`, scoped to `currentUser.id()`. `NotificationSender.send(
  NotificationMessage, NotificationRecipient)`. `InAppNotificationPort` — the 7 `notify*(...)`
  methods, all taking IDs only.

- [ ] **Step 1: Port the value types** — `NotificationMessage`, `NotificationRecipient`
  (with its nested `NotificationChannelPreference` enum), `NotificationType` already done,
  `InAppNotificationDto`, `NotificationSender`, `InAppNotificationPort` — verbatim, package renamed.

- [ ] **Step 2: Port the 4 use cases + `InAppNotificationSender` + `LogNotificationSender`** —
  verbatim, package renamed. `InAppNotificationSender` keeps its log-and-swallow `save(...)` (the
  Kafka redelivery in Task 12 is the real safety net; matching today's behaviour keeps the port
  faithful).

- [ ] **Step 3: Write `NotificationControllerTest`** — `@WebMvcTest(NotificationController.class)`
  + `@Import(SecurityConfig.class)` + mock use cases + a stub `AuthenticatedUser` principal. Assert
  each of the 4 routes: 200/204, correct verb (`PUT`, not PATCH), body scoped to the principal's id,
  401 without auth.

- [ ] **Step 4: Port `NotificationController`** — verbatim except `import` of `AuthenticatedUser`
  from the new `auth` package (the monolith used `com.pilarestilo.shared.auth.domain.AuthenticatedUser`;
  the field accessor `.id()` is the same).

- [ ] **Step 5: Run** — `mvn test -Dtest=NotificationControllerTest` → PASS.

- [ ] **Step 6: Commit** — `git commit -m "feat(notification-service): in-app read API — GET list/unread-count, PUT read/read-all"`

---

## Task 6: Read-only view of the shared `pilarestilo` database

**Files:**
- Create: `config/ReadOnlyDbConfig.java`
- Create: `infrastructure/persistence/readonly/` — 7 `*RoEntity` + 6 Spring Data repos + 7 port
  adapters (see structure above)
- Create: `domain/ports/` — `OrderReadPort`, `CustomerReadPort`, `PaymentReadPort`,
  `SalesDocumentReadPort`, `ReturnReadPort`, `MessagingSettingsPort`, `PaymentReviewerReadPort`
- Create: `domain/view/` — `OrderView`, `OrderItemView`, `CustomerView`, `PaymentView`,
  `SalesDocumentView`, `ReturnView`, `MessagingSettings` (immutable records)
- Test: `ReadOnlyMappingIT.java` (Testcontainers, against the monolith's Flyway output)

**Interfaces:**
- Produces the read ports the dispatchers (Task 11) and composer (Task 7) depend on. Each returns
  `Optional<XView>` or `List<XView>`. Field lists per §4.2 of the spec. Examples:
  - `OrderView(UUID id, String publicReference, UUID customerId, String status, Money subtotal,
    Money discount, Money net, Money tax, BigDecimal taxRate, Money total, String shippingCourierId,
    String shippingCourierName, String shippingZoneCode, List<OrderItemView> items)` where
    `OrderItemView(String productName, String variantColor, String variantSize, int quantity,
    Money unitPrice)` and `Money(BigDecimal amount, String currency)`.
  - `CustomerView(UUID id, String email, String phone, String fullName, String role, boolean active,
    String notificationChannelPreference)`.
  - `PaymentView(UUID id, UUID orderId, String method, String status, UUID reviewerId,
    String rejectionReason, String proofReference, Instant createdAt, String transferAccountHolderName,
    String transferBankName, String transferAccountType, String transferAccountNumber,
    String transferAccountEmail)`.
  - `SalesDocumentView(UUID id, String type, String folio, Money net, Money tax, BigDecimal taxRate,
    Money total)`.
  - `ReturnView(UUID id, UUID orderId, String kind, String reason, Instant deadlineAt,
    Money refundAmount, String refundMethod, String refundReference, Instant refundedAt)`.
  - `MessagingSettings` — a record holding every `system_settings` messaging column as read
    (ciphertext for secrets — decryption happens in the senders, Task 8), plus
    `List<String> notificationProviders` (split the comma-joined column), `String updatedBy`,
    `boolean bankTransferAutoCancelEnabled`, `int bankTransferAutoCancelTimeoutMinutes`.
  - `PaymentReviewerReadPort.findActiveByRoles(List<String> roles)` → `List<CustomerView>` (backs
    `findByRoleIn` for payment reviewers + return handlers).

- [ ] **Step 1: `ReadOnlyDbConfig`** — hand-built, NON-primary (the owned DB is `@Primary`):

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.pilarestilo.notificationservice.infrastructure.persistence.readonly",
    entityManagerFactoryRef = "sharedRoEntityManagerFactory",
    transactionManagerRef = "sharedRoTransactionManager")
public class ReadOnlyDbConfig {
    @Bean @ConfigurationProperties("app.shared-db.datasource")
    DataSourceProperties sharedRoDataSourceProperties() { return new DataSourceProperties(); }
    @Bean @ConfigurationProperties("app.shared-db.datasource.hikari")
    DataSource sharedRoDataSource(@Qualifier("sharedRoDataSourceProperties") DataSourceProperties p) {
        // .readOnly(true) on the Hikari config
        return p.initializeDataSourceBuilder().build();
    }
    @Bean LocalContainerEntityManagerFactoryBean sharedRoEntityManagerFactory(...) {
        // packagesToScan = the readonly package; persistenceUnitName = "shared-ro";
        // ddl-auto validate
    }
    @Bean PlatformTransactionManager sharedRoTransactionManager(...) { ... }
}
```

  Config keys: `app.shared-db.datasource.url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/pilarestilo}`,
  `.username: ${SPRING_DATASOURCE_USERNAME:pilar}`, `.password: ${SPRING_DATASOURCE_PASSWORD:pilar}`.

- [ ] **Step 2: Write `ReadOnlyMappingIT` first** — `@Testcontainers`; start `postgres:16`; run the
  **monolith's** Flyway migrations against it (point Flyway at
  `../../backend/src/main/resources/db/migration` — or, simpler and hermetic, add
  `org.testcontainers` + a fixture that applies `backend`'s migration SQL; if the relative path is
  fragile, copy the DDL the entities need into a test-only `schema.sql`). Boot only
  `ReadOnlyDbConfig` + the readonly repos (`@DataJpaTest`-style slice or a focused
  `@SpringBootTest`). Assert: `sharedRoEntityManagerFactory` builds with no
  `SchemaManagementException` (i.e. every `*RoEntity` validates against the real schema). This test
  is the guard that turns a future monolith column rename into a red local build.

- [ ] **Step 3: Run it, confirm it fails** — no entities yet / config missing → FAIL.

- [ ] **Step 4: Create the 7 `*RoEntity` classes** — `@Entity @Table(name="...")`, every field
  `@Column(name="...", insertable=false, updatable=false)`, no setters needed beyond JPA's
  reflection. Map **only** the columns in the §4.2 table. Cross-check column names against the
  monolith's `@Entity` classes (`order/infrastructure/persistence/entities/OrderEntity.java`,
  `user/...`, `payment/...`, `billing/...`, `returns/...`) and `systemsettings/.../SystemSettingsEntity.java`.
  For `orders` ↔ `order_items` use a `@OneToMany(fetch = FetchType.EAGER)` (mapping runs outside a
  tx in adapters — see `product-service-mapper-needs-eager` memory; EAGER avoids
  `LazyInitializationException` on a real request that unit tests never hit).

- [ ] **Step 5: Create the 6 repos + 7 adapters** — repos are `interface XRepo extends
  JpaRepository<XRoEntity, UUID>` with finder methods (`findWithItemsById`,
  `findByRoleInAndActiveTrue`, ...). Adapters (`@Component`) map entity → view record and implement
  the domain port. `MessagingSettingsAdapter` splits `notification_providers` on `,` and trims.

- [ ] **Step 6: Run** — `mvn test -Dtest=ReadOnlyMappingIT` → PASS.

- [ ] **Step 7: Commit** — `git commit -m "feat(notification-service): read-only view of orders/users/payments/documents/returns/settings on the shared DB"`

---

## Task 7: `NotificationComposer` + `EmailLayout`

**Files:**
- Create: `application/EmailLayout.java`, `infrastructure/adapters/EmailFormat.java` — **verbatim**
  ports (both are pure), package renamed. Copy `src/main/resources/email/pilar-estilo-logo.png`.
- Create: `application/NotificationComposer.java` — ported, signatures retargeted from monolith
  domain aggregates to the Task 6 view records.
- Test: `NotificationComposerTest.java` — port every existing case from the monolith's
  `NotificationComposerTest`.

**Interfaces:**
- Consumes: `OrderView`, `PaymentView`, `SalesDocumentView`, `ReturnView`, `Money`,
  `UserRegisteredEvent.WelcomeDiscount` (Task 10).
- Produces: `NotificationComposer` with the same method names as today —
  `transferInstructions(OrderView, PaymentView, Instant)`, `orderConfirmation(OrderView)`,
  `orderCancelled(OrderView, String)`, `paymentReceived(UUID)`, `orderPreparing/Shipped/Delivered(OrderView)`,
  `discountCodeAssigned(String)`, `paymentProofSubmitted(OrderView, PaymentView, String)`,
  `welcome(String[, WelcomeDiscount])`, `salesDocumentIssued(OrderView, SalesDocumentView)`,
  `returnRequested/returnApproved/refundRegistered(OrderView, ReturnView)`,
  `returnRequestedForStaff(OrderView, ReturnView, String)` — all returning `NotificationMessage`.

- [ ] **Step 1: Port `EmailLayout` + `EmailFormat` verbatim**, rename package. Run any existing
  `EmailLayoutTest`.

- [ ] **Step 2: Port `NotificationComposerTest`** — replace `Order`/`Payment`/`SalesDocument`/
  `ReturnRequest` builders with the view records. Keep every assertion string (the Spanish copy,
  the "10 días" retracto line, the "sube tu comprobante" wording, the credit-note vs boleta
  opening, the `KEY_*` data-map keys).

- [ ] **Step 3: Run it, confirm it fails** — `mvn test -Dtest=NotificationComposerTest` → FAIL.

- [ ] **Step 4: Port `NotificationComposer`** — body is unchanged prose-building; only the
  parameter types change (`order.getPublicReference()` → `order.publicReference()`, etc.). Give the
  view records accessors that read naturally, or accept the mechanical rename. `SalesDocumentType`
  and `ReturnKind` enum branches become `switch` on the view's `String type` / `String kind`.

- [ ] **Step 5: Run** — `mvn test -Dtest='NotificationComposerTest,EmailLayoutTest'` → PASS.

- [ ] **Step 6: Commit** — `git commit -m "feat(notification-service): NotificationComposer + EmailLayout, rendering from read-view records"`

---

## Task 8: `SystemSettingsCryptoService` + the channel senders

**Files:**
- Create: `infrastructure/adapters/SystemSettingsCryptoService.java` — **verbatim** port (pure
  AES-GCM, `@Value("${app.system-settings.crypto-secret:${JWT_SECRET:change-this-secret}}")`).
- Create: `metrics/NotificationMetrics.java` — wraps a `MeterRegistry`,
  `countSendFailure(String channel)` → `notification_send_failures_total{channel=...}`.
- Create: `infrastructure/adapters/{Smtp,SendGrid}EmailNotificationSender.java`,
  `{Twilio,Simulated}WhatsAppNotificationSender.java`, `N8nWebhookNotificationSender.java`
- Modify each ported sender: swap `SystemSettingsRepository systemSettingsRepository` +
  `settings.getSmtpHost()` etc. for `MessagingSettingsPort` returning a `MessagingSettings` record;
  keep `SystemSettingsCryptoService` exactly as-is. On the `catch (Exception ex)` send-failure
  branch, add `metrics.countSendFailure("EMAIL_SMTP")` (or the channel's name) alongside the log.
- Test: one `*Test` per sender — port the existing monolith tests.

**Interfaces:**
- Consumes: `MessagingSettingsPort` (Task 6), `SystemSettingsCryptoService`, `NotificationMetrics`,
  `RestClient.Builder`, `JavaMailSender` machinery (SMTP builds its own `JavaMailSenderImpl`).
- Produces: 5 `NotificationSender` beans + `LogNotificationSender` (Task 5). None `@Primary`
  (Task 9 provides the primary).

- [ ] **Step 1: Port `SystemSettingsCryptoService` + a `SystemSettingsCryptoServiceTest`**
  (encrypt→decrypt round-trip; blank → `""`; tampered ciphertext → `DomainException`). Need a
  `DomainException` class — create `com.pilarestilo.notificationservice.shared.DomainException`
  (copy the monolith's minimal one).

- [ ] **Step 2: Port each sender's test** — `SmtpEmailNotificationSenderTest` uses the
  package-private `buildSender` seam with a recording `JavaMailSenderImpl`; the RestClient-based
  ones use `RestClient.Builder` + `MockRestServiceServer` or a stub server. Assert: channel-pref
  skip, missing-config disable, address resolution, and that a thrown send bumps the failure
  counter.

- [ ] **Step 3: Run, confirm they fail** — `mvn test -Dtest='*NotificationSenderTest'` → FAIL.

- [ ] **Step 4: Port the 5 senders** — bodies unchanged except the settings source
  (`MessagingSettings` record instead of `systemSettingsRepository.get()`) and the added
  `metrics.countSendFailure(...)` line. `SimulatedWhatsAppNotificationSender` and
  `N8nWebhookNotificationSender` still read `MessagingSettings` for their config.

- [ ] **Step 5: Run** — `mvn test -Dtest='*NotificationSenderTest,SystemSettingsCryptoServiceTest'` → PASS.

- [ ] **Step 6: Commit** — `git commit -m "feat(notification-service): channel senders + crypto, failures counted on notification_send_failures_total"`

---

## Task 9: `SystemSettingsNotificationSender` (the `@Primary` fan-out)

**Files:**
- Create: `infrastructure/adapters/SystemSettingsNotificationSender.java` (`@Primary`,
  `implements NotificationSender`)
- Create: `domain/enums/NotificationProvider.java` — verbatim port from
  `com.pilarestilo.systemsettings.domain.enums.NotificationProvider` (with `fromRaw`)
- Test: `SystemSettingsNotificationSenderTest.java`

**Interfaces:**
- Consumes: `MessagingSettingsPort` (for `notificationProviders` + `updatedBy`), the 6 channel
  senders, `@Value("${app.notification.provider:LOG}")`.
- Produces: the single `@Primary NotificationSender` the dispatchers inject. Behaviour identical to
  the monolith's `resolveProviders()` — configured set wins; a system-seeded set equal to `{LOG}`
  defers to the env default; empty/unreadable → env default; every provider tried in its own try;
  all-failed → ERROR log.

- [ ] **Step 1: Write `SystemSettingsNotificationSenderTest`** — port the monolith cases: two
  providers both invoked; one throwing doesn't stop the other; `{LOG}` + `updatedBy` starting
  `system-` → env fallback used; `{LOG}` saved by a real admin → LOG used.

- [ ] **Step 2: Run, confirm fail** → FAIL.

- [ ] **Step 3: Port the class** — `senderFor(provider)` switch maps `NotificationProvider` →
  the Task 8 bean; `resolveProviders()` reads `MessagingSettings.notificationProviders()` +
  `.updatedBy()` instead of `systemSettingsRepository.get()`.

- [ ] **Step 4: Run** — `mvn test -Dtest=SystemSettingsNotificationSenderTest` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(notification-service): SystemSettingsNotificationSender fan-out over every enabled channel"`

---

## Task 10: Kafka consumer infrastructure + thin event records

**Files:**
- Create: `events/*.java` — 12 event records + `DomainEventTopics.java`
- Create: `config/KafkaConsumerConfig.java` — ported from
  `backend/.../shared/infrastructure/kafka/KafkaDomainEventsConfiguration.java`
- Create: `config/KafkaDomainEventsProperties.java` — ported verbatim
- Test: `KafkaConsumerConfigTest.java`, `DomainEventTopicsTest.java`

**Interfaces:**
- Produces: `@Bean("domainEventsKafkaListenerContainerFactory")` with the same
  `DeadLetterPublishingRecoverer` + `DefaultErrorHandler(FixedBackOff(1500, 2))` +
  `errorHandler.setCommitRecovered(true)` as the monolith; `DomainEventTopics` bean (name
  `domainEventTopics`) with `topicFor(String)` + `deadLetterTopicFor(String)`; 12 event records:

```java
public record OrderCreatedEvent(UUID orderId, UUID customerId, Instant occurredAt) {}
public record OrderStatusChangedEvent(UUID orderId, UUID customerId, String previousStatus,
                                      String newStatus, Instant occurredAt) {}
public record PaymentConfirmedEvent(UUID paymentId, UUID orderId, Instant occurredAt) {}
public record PaymentRegisteredEvent(UUID paymentId, UUID orderId, Instant occurredAt) {}   // verify components
public record PaymentSubmittedEvent(UUID paymentId, Instant occurredAt) {}                  // verify components
public record PaymentRejectedEvent(UUID paymentId, UUID orderId, UUID reviewerId, Instant occurredAt) {} // verify
public record SalesDocumentIssuedEvent(UUID documentId, UUID orderId, String folio, Instant occurredAt) {}
public record ReturnRequestedEvent(UUID returnId, UUID orderId, String kind, Instant occurredAt) {}
public record ReturnApprovedEvent(UUID returnId, UUID orderId, Instant occurredAt) {}       // verify
public record RefundRegisteredEvent(UUID returnId, UUID orderId, Instant occurredAt) {}     // verify
public record UserRegisteredEvent(UUID userId, Instant occurredAt, WelcomeDiscount welcomeDiscount) {
    public record WelcomeDiscount(String code, String type, BigDecimal value,
                                  BigDecimal minOrderAmount, LocalDate validUntil) {}
}
public record DiscountCodeAssignedEvent(UUID assignedUserId, String code, Instant occurredAt) {} // verify
```

  **Verify every record's components** against `backend/src/main/java/com/pilarestilo/*/domain/events/*.java`
  before writing — the JSON field names must match exactly (Jackson binds by name;
  `USE_TYPE_INFO_HEADERS=false`).

- [ ] **Step 1: Port `KafkaDomainEventsProperties` + `DomainEventTopics`** verbatim, package
  renamed. `DomainEventTopics` default prefix `pe.domain`, dlt suffix `.dlt`.

- [ ] **Step 2: `DomainEventTopicsTest`** — `topicFor("OrderCreated")` == `"pe.domain.order-created"`;
  `deadLetterTopicFor("pe.domain.order-created")` == `"pe.domain.order-created.dlt"`.

- [ ] **Step 3: Port `KafkaConsumerConfig`** — copy `KafkaDomainEventsConfiguration`, drop the
  producer beans (consumer-only service — but keep a `KafkaTemplate<String,Object>` for the DLT
  recoverer), set `GROUP_ID_CONFIG` to `${app.domain-events.kafka.consumer-group-id:pe-notification-service}`,
  set `JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS=false` and
  `VALUE_DEFAULT_TYPE` unset (target type comes from each `@KafkaListener` method parameter).
  Keep `@ConditionalOnProperty(prefix="app.domain-events.kafka", name="enabled", havingValue="true")`.

- [ ] **Step 4: Create the 12 event records** — verify each against the monolith source.

- [ ] **Step 5: `KafkaConsumerConfigTest`** — context slice with `app.domain-events.kafka.enabled=true`
  + embedded/`@MockBean` broker props; assert `domainEventsKafkaListenerContainerFactory` bean
  exists and its error handler is a `DefaultErrorHandler`.

- [ ] **Step 6: Run** — `mvn test -Dtest='KafkaConsumerConfigTest,DomainEventTopicsTest'` → PASS.

- [ ] **Step 7: Commit** — `git commit -m "feat(notification-service): Kafka consumer infra + thin event records, group pe-notification-service"`

---

## Task 11: The 7 dispatchers

**Files:**
- Create: `application/{Order,Payment,PaymentRegistered,Billing,Discount,Return,User}NotificationDispatcher.java`
- Create: `application/BankTransferDeadline.java` — ported (needs `PaymentView.createdAt()` +
  `MessagingSettings` auto-cancel fields)
- Test: one `*DispatcherTest` per dispatcher — port the monolith tests.

**Interfaces:**
- Consumes: the Task 6 read ports, `NotificationComposer` (Task 7), `NotificationSender` +
  `InAppNotificationPort` (Task 5), the Task 10 event records.
- Produces: 7 `@Service` dispatchers, one `on<Event>(<Event>Event)` method per event they handle.
  Method names match today's (`onOrderCreated`, `onOrderStatusChanged`, `onPaymentConfirmed`,
  `onPaymentSubmitted`, `onPaymentRejected`, `onPaymentRegistered`, `onSalesDocumentIssued`,
  `onReturnRequested`, `onReturnApproved`, `onRefundRegistered`, `onUserRegistered`,
  `onDiscountCodeAssigned`).

**Port rules for every dispatcher:**
1. `orderRepository.findById(id)` → `orderReadPort.findById(id)` returning `Optional<OrderView>`.
   Same for user → `customerReadPort`, payment → `paymentReadPort`, salesDocument →
   `salesDocumentReadPort`, returnRequest → `returnReadPort`.
2. `userRepository.findByRoleIn([ADMIN, ADMINISTRACION], PageRequest.of(0,50))` →
   `paymentReviewerReadPort.findActiveByRoles(List.of("ADMIN","ADMINISTRACION"))`.
3. `PaymentNotificationDispatcher` — **no** `UpdateOrderStatusUseCase` (relocated in Task 1). Its
   `onPaymentSubmitted` only emails reviewers.
4. The event parameter is the Task 10 record; read `.orderId()`, `.customerId()`, `.newStatus()`
   (a `String` now — compare against `"PREPARING_ORDER"` / `"SHIPPED"` / `"DELIVERED"` or map to a
   local enum), `.paymentId()`, `.reviewerId()` (compare against the ported
   `Payment.SYSTEM_REVIEWER_ID` constant — port that constant into a small `PaymentConstants`).
5. `user.getNotificationChannelPreference().name()` → `customerView.notificationChannelPreference()`.
6. Keep every `log.warn(...)` / `ifPresentOrElse(..., () -> ...unknown())` branch verbatim.

- [ ] **Step 1: Port `BankTransferDeadlineTest` + `BankTransferDeadline`** — the deadline is
  `payment.createdAt() + settings.bankTransferAutoCancelTimeoutMinutes` when
  `settings.bankTransferAutoCancelEnabled()`, else empty. Verify the exact rule against the monolith
  source first.

- [ ] **Step 2: Port `OrderNotificationDispatcherTest`, then `OrderNotificationDispatcher`** — run
  `mvn test -Dtest=OrderNotificationDispatcherTest` red → green.

- [ ] **Step 3: Same for `Payment`** (drop the status-transition test cases), **`PaymentRegistered`,
  `Billing`, `Discount`, `Return`, `User`** — one dispatcher per sub-step, each red → green →
  small commit is fine, or one commit at the end.

- [ ] **Step 4: Run all** — `mvn test -Dtest='*DispatcherTest,BankTransferDeadlineTest'` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(notification-service): 7 dispatchers reading from the shared-DB view"`

---

## Task 12: Kafka listeners (transport only) + the enable flag

**Files:**
- Create: `infrastructure/listeners/kafka/{Order,Payment,PaymentRegistered,Billing,Discount,Return,User}NotificationListener.java`
- Test: `KafkaNotificationFlowIT.java` (Testcontainers: Kafka + Postgres)

**Interfaces:**
- Consumes: the 7 dispatchers, `domainEventsKafkaListenerContainerFactory`, `domainEventTopics`.
- Produces: 7 `@Component` listeners, each `@ConditionalOnProperty(prefix =
  "app.notification.listeners", name = "enabled", havingValue = "true")`. Each `@KafkaListener`
  method: `groupId = "${app.domain-events.kafka.consumer-group-id:pe-notification-service}"`,
  `topics = "#{@domainEventTopics.topicFor('<EventSimpleName>')}"`,
  `containerFactory = "domainEventsKafkaListenerContainerFactory"`, body = one call to the
  dispatcher. Topic names must map to the **monolith's** event simple names
  (`OrderCreated`, `OrderStatusChanged`, `PaymentConfirmed`, `PaymentRegistered`, `PaymentSubmitted`,
  `PaymentRejected`, `SalesDocumentIssued`, `ReturnRequested`, `ReturnApproved`, `RefundRegistered`,
  `UserRegistered`, `DiscountCodeAssigned`) — NOT the local `*Event` names.

> **The `app.notification.listeners.enabled` flag stays `false` in `application.yml`.** It is
> flipped to `true` in Task 14 (cutover deploy 1), where the monolith's own consumers are
> simultaneously gated off by `app.notification.kafka-listeners.enabled=false` (Task 13) — so there
> is no lasting window where both consume (spec §7).

- [ ] **Step 1: Write `KafkaNotificationFlowIT`** — `@SpringBootTest` with
  `app.domain-events.kafka.enabled=true`, `app.notification.listeners.enabled=true`, Testcontainers
  Kafka + Postgres (both DBs). Publish a JSON `OrderCreated` to `pe.domain.order-created` with a
  matching key; seed the `orders` + `users` + `system_settings` rows in the shared test DB; assert
  (await) an in-app row lands in `pilarestilo_notifications` and `LogNotificationSender` fired
  (capture logs, or assert via a spy `NotificationSender`). Also publish a malformed message and
  assert it lands on `pe.domain.order-created.dlt` after the retries.

- [ ] **Step 2: Run, confirm fail** → FAIL.

- [ ] **Step 3: Create the 7 listeners** — one call each. Match the monolith's method fan-out
  (e.g. `KafkaOrderNotificationListener` has `onOrderCreated` + `onOrderStatusChanged`).

- [ ] **Step 4: Run** — `mvn test -Dtest=KafkaNotificationFlowIT` → PASS. Then full suite
  `mvn verify` → PASS and jacoco `check` ≥ 0.50.

- [ ] **Step 5: Commit** — `git commit -m "feat(notification-service): Kafka transport listeners (disabled until cutover) + end-to-end IT"`

---

## Task 13: A flag to silence the monolith's notification Kafka listeners (monolith prep)

**Ships on its own, before the cutover.** This is what makes the cutover reversible by a flag flip
instead of a deploy revert (spec §6.3, §7).

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/notification/infrastructure/listeners/kafka/`
  — all 7 `Kafka*NotificationListener` classes
- Modify: `backend/src/main/resources/application.yml` — add the property + metadata
- Modify: `backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- Modify: `infra/docker-compose.yml` — `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED` on the backend block
- Modify: `infra/.env.example`
- Test: `backend/src/test/java/.../KafkaNotificationListenersFlagTest.java` (or extend an existing
  slice) — with the flag `false` the beans are absent; with `true` they are present

**Interfaces:**
- Produces: `app.notification.kafka-listeners.enabled` (default `true`) — a second gate on the 7
  notification Kafka listeners, independent of `app.domain-events.kafka.enabled`.

- [ ] **Step 1: Write the failing test** — an `ApplicationContextRunner` (or a `@SpringBootTest`
  slice with `app.domain-events.kafka.enabled=true`) that asserts
  `context.getBeansOfType(KafkaOrderNotificationListener.class)` is empty when
  `app.notification.kafka-listeners.enabled=false` and non-empty when `true`.

- [ ] **Step 2: Run it, confirm it fails** — `cd backend && mvn test -Dtest=KafkaNotificationListenersFlagTest`.

- [ ] **Step 3: Add the second condition** — on each of the 7 classes, alongside the existing
  `@ConditionalOnProperty(prefix = "app.domain-events.kafka", name = "enabled", havingValue = "true")`
  add
  `@ConditionalOnProperty(prefix = "app.notification.kafka-listeners", name = "enabled", havingValue = "true", matchIfMissing = true)`.
  (Two `@ConditionalOnProperty` annotations are AND-ed. `matchIfMissing = true` keeps today's
  behaviour when the property is unset.)

- [ ] **Step 4: Config + metadata** — `application.yml`:
  `app.notification.kafka-listeners.enabled: ${APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED:true}`.
  Add the key to `additional-spring-configuration-metadata.json` with a one-line description.

- [ ] **Step 5: docker-compose + .env.example** —
  `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED: ${APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED:-true}` on
  the backend service block.

- [ ] **Step 6: Run** — `cd backend && mvn test -Dtest='KafkaNotificationListenersFlagTest,*Notification*'` → PASS.

- [ ] **Step 7: Commit** —
  `git commit -m "feat(notification): app.notification.kafka-listeners.enabled — a flag to silence the monolith's notification consumers for the cutover"`

---

## Task 14: Cutover — deploy 1 (reversible)

**One commit. Enables the service, wires the infra, silences the monolith's consumers — the
monolith notification code stays for now (deleted in Task 16, once verified).**

**Files:**
- Modify: `services/notification-service/src/main/java/.../config/KafkaConsumerConfig.java` —
  `props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")` (was `"earliest"`; the
  pre-seed in Step 2 makes this moot but it is the correct default for a service that must never
  replay history)
- Modify: `infra/Caddyfile` — add before the generic `handle /api/*`:

```
@notification_reads {
    method GET HEAD PUT
    path /api/notifications /api/notifications/*
}
handle @notification_reads {
    reverse_proxy notification-service:8085 backend:8080 {
        lb_policy first
        lb_try_duration 5s
        fail_duration 15s
        transport http {
            dial_timeout 2s
            response_header_timeout 30s
        }
    }
}
```

- Modify: `infra/monitoring/prometheus/prometheus.yml` — append:

```yaml
  - job_name: notification_service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["notification-service:8085"]
```

- Modify: `infra/docker-compose.yml` — add the `notification-service` block (copy the
  `order-service` block; `context: ../services/notification-service`, `container_name:
  pe_notification_service`, `SERVER_PORT "8085"`, healthcheck on `:8085/actuator/health`,
  `profiles: ["microservices"]`, `depends_on: postgres: service_healthy`). Env:
  `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (the service reads them for `app.shared-db.datasource.*`
  via `${SPRING_DATASOURCE_URL:...}` — name them directly),
  `APP_NOTIFICATION_DATASOURCE_URL/USERNAME/PASSWORD`, `SYSTEM_SETTINGS_CRYPTO_SECRET`, `JWT_SECRET`,
  `KAFKA_BOOTSTRAP_SERVERS`, `APP_DOMAIN_EVENTS_KAFKA_ENABLED` + the other `APP_DOMAIN_EVENTS_KAFKA_*`,
  `APP_NOTIFICATION_LISTENERS_ENABLED: ${APP_NOTIFICATION_LISTENERS_ENABLED:-true}`, and every
  `NOTIFICATION_PROVIDER` / `EMAIL_SMTP_*` / `SENDGRID_*` / `WHATSAPP_*` / `NOTIFICATION_N8N_*`
  fallback (**copied** — leave them on the backend block too, as the rollback path; removed there in
  Task 16), tracing envs.
- Modify: `infra/docker-compose.yml` backend block — flip
  `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED: ${APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED:-false}`
  (the flag from Task 13; default in the shipped `.env` becomes `false`).
- Modify: `infra/.env.example` — `APP_NOTIFICATION_LISTENERS_ENABLED=true`,
  `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED=false`.
- Modify: `.github/workflows/*` — add `notification-service` to the services build/test matrix.
- Modify: `CLAUDE.md` —
  - Monorepo layout / Modules: add `notification-service` to `services/`.
  - "Two codebases write the `orders` table": add a paragraph — `services/notification-service/`
    **reads** `orders`, `order_items`, `users`, `payments`, `sales_documents`, `return_requests`,
    `system_settings` read-only; any column rename/drop on those must update its `*RoEntity` in the
    same commit and deploy or the service fails `validate` on boot.
  - Caddy routing table: `GET/HEAD/PUT /api/notifications*` → `notification-service:8085`.
  - "Every in-process `@EventListener` is dead when Kafka is on" / notifications sections: note the
    dispatchers/senders now live in `services/notification-service`, Kafka-only. The monolith's
    twins stay until Task 16 but are gated off by `app.notification.kafka-listeners.enabled=false`.

- [ ] **Step 1: `AUTO_OFFSET_RESET` → `latest`** in `KafkaConsumerConfig`, run the service suite
  (`cd services/notification-service && mvn -o verify -Djacoco.skip=true`) → PASS.

- [ ] **Step 2: Caddyfile + prometheus.yml + docker-compose (both blocks) + .env.example + CI +
  CLAUDE.md** edits. `caddy validate` the Caddyfile
  (`docker run --rm -v "$PWD/infra/Caddyfile:/etc/caddy/Caddyfile" caddy caddy validate --config /etc/caddy/Caddyfile`).

- [ ] **Step 3: Commit** —
  `git add -A && git commit -m "feat: extract notification-service — cutover deploy 1 (service consumes, monolith consumers gated off, reversible)"`

- [ ] **Step 4: The deploy runbook** (goes in the PR description, executed at deploy time, not now):
  1. Pre-seed the consumer group while the current prod backend still consumes:
     `docker compose exec kafka kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group pe-notification-service --reset-offsets --to-latest --all-topics --execute`
  2. Deploy the new backend image (`APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED=false`) — its 7
     notification consumers do not register.
  3. Immediately deploy / start `notification-service` (`APP_NOTIFICATION_LISTENERS_ENABLED=true`)
     — it joins the pre-seeded group and drains the short pile-up plus every new event, once.
  4. Run Task 15 in production.
  5. **Rollback if needed:** set `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED=true` on the backend and
     `APP_NOTIFICATION_LISTENERS_ENABLED=false` on the service. No deploy revert.

---

## Task 15: Full-stack verification (mandatory before deploy 1 reaches production)

**Not code — the mandatory check from spec §7. Do not merge to `master` without it.**

> **Verified locally 2026-08-29** (partial-swap: rebuilt backend + notification-service, shared
> postgres/kafka untouched). Real order `PE-63EAA4E287` → exactly one `ORDER_CONFIRMATION` from
> notification-service, zero from the backend; bell GET/PUT via Caddy → 8085; in-app rows in
> `pilarestilo_notifications`; prometheus target up; rollback (flip both flags) puts the monolith
> back in charge. Bug found + fixed: `V1__notifications.sql` must be **byte-identical** to the
> monolith's or Flyway checksum validation crash-loops the service (commit `ea0ff64`). The steps
> below are the production re-run.

- [ ] **Step 1: Bring up the full stack** —
  `cd infra && docker compose --env-file .env --profile kafka --profile cache --profile microservices --profile observability up -d --build`
  In `.env`: `APP_DOMAIN_EVENTS_KAFKA_ENABLED=true`, `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED=false`,
  `APP_NOTIFICATION_LISTENERS_ENABLED=true`, messaging on `NOTIFICATION_PROVIDER=LOG` (or SMTP with
  the fixed test mailbox).

- [ ] **Step 2: Confirm exactly one consumer** — the backend startup log shows **no**
  `Kafka*NotificationListener` bean registered; `notification-service` shows its 7. Both consumer
  groups exist in `kafka-consumer-groups.sh --list` but only `pe-notification-service` has lag
  moving.

- [ ] **Step 3: Place a real order** as `test_estilo@pilarestilo.com`
  (`no-real-emails-from-tests` memory) through the storefront on `http://localhost`, pay it
  (STUB gateway), let it reach paid.

- [ ] **Step 4: Assert exactly one confirmation** — `docker compose logs notification-service`
  shows one `ORDER_CONFIRMATION` send; `docker compose logs infra-backend-1` shows none; the
  mailbox / `LOG` output has exactly one — **not zero, not two**. Ley 21.398 requires this message
  (zero → the customer's retracto window is 90 days not 10).

- [ ] **Step 5: Assert the bell works** — log in as the test customer, `GET /api/notifications`
  and `/unread-count` (served by `notification-service:8085` per Caddy), `PUT /{id}/read` marks it.
  `docker compose logs caddy` confirms the route hit 8085.

- [ ] **Step 6: DLT + metrics wired** —
  `docker compose exec notification-service wget -qO- localhost:8085/actuator/prometheus | grep notification_send_failures_total`
  exists (0 is fine). Prometheus targets page shows `notification_service` UP.

- [ ] **Step 7: Test the rollback path once** — flip the two flags back, restart both, place another
  order → the monolith sends the one confirmation, `notification-service` sends none. Flip forward
  again. This proves the rollback works before you need it.

- [ ] **Step 8: Record the result** in the PR description; update the
  `notification-service-extraction-analysis` + `pending-work-queue` memories. Merge to `master`
  (a push to `master` deploys — `spring-boot-4-deploy` memory), then run the Task 14 Step 4 runbook.

---

## Task 16: Cleanup — delete the monolith module (deploy 2, after verification)

**A separate, later deploy. Pure dead-code removal — the monolith stopped using any of this in
deploy 1. Do NOT start until `notification-service` has run clean in production for ~a day.**

**Files:**
- Delete: `backend/src/main/java/com/pilarestilo/notification/**` (incl. the Task 13 flag),
  `backend/src/main/java/com/pilarestilo/notifications/**`
- Delete: `backend/src/main/java/com/pilarestilo/shared/infrastructure/config/PersistenceConfig.java`,
  `EntityScanConfig.java`
- Delete: `backend/src/test/java/com/pilarestilo/shared/infrastructure/config/EntityScanCoversEveryModuleTest.java`,
  `backend/src/test/java/com/pilarestilo/support/NotificationsTestDatabase.java`,
  `backend/src/test/java/com/pilarestilo/notifications/**`, every `*NotificationDispatcherTest` /
  `*NotificationListenerTest` / `NotificationControllerTest` / composer / sender / `EmailLayout` test
- Delete: `backend/src/main/resources/db/migration-notifications/`
- Modify: `backend/src/main/resources/application.yml` + `application-local.yml` — delete the whole
  `app.notification:` block and the `app.notification.kafka-listeners` flag. **Keep**
  `app.system-settings.crypto-secret` (the monolith still uses `SystemSettingsCryptoService` for the
  payment-gateway and media secrets — grep to confirm).
- Modify: `backend/.../additional-spring-configuration-metadata.json` — drop the `app.notification.*`
  entries.
- Modify: `backend/pom.xml` — drop `spring-boot-starter-mail` if `grep -rn "JavaMailSender\|MimeMessage" backend/src/main`
  is empty. Keep `spring-boot-flyway`, `spring-boot-kafka`.
- Modify: `infra/docker-compose.yml` — remove `APP_NOTIFICATION_DATASOURCE_*`,
  `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED`, and the messaging envs from the **backend** block
  (they stay only on `notification-service`).
- Modify: `infra/.env.example` — same.
- **Keep** `infra/postgres/init/01-notifications-database.sh` and the old `notifications` table on
  the main DB — still the rollback path (`notification-database-split` memory).

- [x] **Step 1: `git rm -r`** the two packages + every listed test + `db/migration-notifications/`.
  Done on branch `chore/notification-t16-cleanup` (worktree off `develop`, 2026-08-30).

- [x] **Step 2: Delete `PersistenceConfig` + `EntityScanConfig` + the guard test.** Done. Stale
  naming-strategy comment in `application.yml` trimmed to one line; `ddl-auto: validate` + the two
  naming properties kept.

- [x] **Step 3: Config + compose + pom** edits.
  - `application.yml`: whole `app.notification:` block removed.
  - `additional-spring-configuration-metadata.json`: all 38 `app.notification.*` entries removed
    (script, deletion-only, valid JSON).
  - `docker-compose.yml` **backend block**: removed `APP_NOTIFICATION_DATASOURCE_*`,
    `NOTIFICATION_PROVIDER`, `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED`, all `WHATSAPP_*`/`TWILIO_*`,
    all `SENDGRID_*`, `EMAIL_SMTP_TO_FALLBACK`. **Kept `EMAIL_SMTP_{HOST,PORT,USERNAME,PASSWORD,
    FROM_EMAIL,SENDER_NAME,AUTH_ENABLED,STARTTLS_ENABLED,SSL_ENABLED}`** — `SmtpPasswordResetMailer`
    (shipped after this plan was written) reads them as the fallback after `system_settings`.
  - `.env.example`: dropped `APP_NOTIFICATION_KAFKA_LISTENERS_ENABLED` only; kept the messaging vars
    (notification-service consumes them) and `APP_NOTIFICATION_LISTENERS_ENABLED`.
  - `pom.xml`: **no change** — `spring-boot-starter-mail` stays (grep for `JavaMailSender` is
    non-empty: `SmtpPasswordResetMailer`). The dep's comment already said to keep it.
  - `CLAUDE.md`: module list, the "@EventListener dead when Kafka is on" section, and the
    "Notifications go out on every enabled channel" section updated.
  - **Not in the original plan:** 21 IT/unit classes outside `notification/` called
    `NotificationsTestDatabase.register(...)` in `@DynamicPropertySource` (mandatory while the 2nd
    datasource existed). Removed the import + the call from all 21.

- [x] **Step 4: Grep for danglers** — `com.pilarestilo.notification` in `src/` → empty.
  `NotificationsPersistenceConfig|NotificationsFlywayMigrator|NotificationsTestDatabase|
  EntityScanCoversEveryModule|PersistenceConfig|EntityScanConfig` in `src/` → empty. No
  `/api/notifications` matchers in monolith security config. `mvn -o clean test-compile` → SUCCESS.

- [x] **Step 5: `mvn clean verify`** → **BUILD SUCCESS** 2026-08-30 (whole compose stack stopped
  first). 560 unit + 79 IT, 0 failures/errors, jacoco gate met. Log confirms one datasource
  (`HikariPool-1` only), one EMF (`persistence unit 'default'`), main Flyway validates + runs all
  migrations, "Found 0 Redis repository interfaces". Pre-existing noise unrelated to T16:
  `ProductAiJobScheduler` logs `CannotCreateTransactionException` every 20s during ITs (its
  `@Scheduled` poll hitting a torn-down Testcontainer between classes) — build stays green.

- [x] **Step 6: Boot against local Docker** — T16 backend image run against the real prod-schema DB
  (main compose + image override). `Started PilarEstiloApplication in 43s`, health UP via Caddy,
  `Successfully validated 94 migrations` then applied the one pending (V92), one `HikariPool`, one
  EMF, **zero "notification" lines anywhere in the backend log**, no `pe-backend-domain-events-
  notification` consumer group (only `-dispatch`/`-payment`/`-review` remain). Registered a probe
  user → exactly one `WELCOME` composed + stored + SMTP-attempted by **notification-service only**
  (SMTP auth fails locally — no creds — but the pipeline ran); backend did nothing. Bell
  (`GET /api/notifications`, `unread-count`) returns it via Caddy → `notification-service:8085`.

- [ ] **Step 7: Commit + merge** to `develop`, then `develop → master` on the owner's go (deploy).

---

## Self-Review

**Spec coverage:**
- §3 read-only-DB decision → Tasks 6, 8 (crypto local), no internal API / no Redis anywhere. ✅
- §4.1 what moves → Tasks 4, 5, 7, 8, 9, 11, 12. ✅
- §4.2 read-only entity set → Task 6 (all 7 tables). ✅
- §4.3 infra (Kafka/Prometheus/Tempo/Caddy/Sonar/CI) → Tasks 3, 10, 14. ✅
- §4.1 Flyway simplification → Task 4 Step 2. ✅
- §6.1 relocate order-status write → Task 1. ✅
- §6.2 PaymentRegistered dispatcher → Task 2. ✅
- §6.3 monolith kafka-listeners flag → Task 13. ✅
- §6.4 delete module, one datasource, drop guard test → Task 16 (deploy 2). ✅
- §6.4 Caddy/prometheus/compose/CI/CLAUDE.md → Task 14. ✅
- §7 two-deploy reversible cutover (pre-seed offset, flags) → Task 12 note + Tasks 13 (flag), 14 (deploy 1 + runbook), 15 (verify), 16 (deploy 2 delete). ✅
- §8 error handling: failure counter → Task 8; DLT reuse → Task 10; loud in-app write → Task 4 (adapter propagates; `InAppNotificationSender` still swallows per port fidelity — **deviation noted**, Kafka redelivery is the net). Acceptable; revisit if §8 wants the swallow removed.
- §9 testing: mapping guard IT → Task 6; Kafka end-to-end → Task 15 compose verification (embedded-Kafka IT dropped — `spring-kafka-test` not in offline cache); own-DB IT → Task 4; coverage gate → Task 3 pom. ✅
- §10 schema-coupling mitigation → Task 6 (minimum columns + guard test), Task 14 (CLAUDE.md). ✅
- §11 deferred: `sent_notifications` idempotency table (own future task); Grafana panel; SendGrid/Twilio keep-or-drop. ✅

**Placeholder scan:** "verify the exact record components / column names against the monolith source"
appears in Tasks 1, 6, 10, 11 — these are genuine verification steps against real files the executor
has, not hand-waves; each names the file to check. No `TODO`/`TBD`. Code blocks given for every new
(non-ported) class; ported classes have explicit source paths + transform rules.

**Type consistency:** `InAppNotificationRepository` 7-method signature fixed in Task 4, consumed in
Task 5. View records defined in Task 6, consumed in Tasks 7 and 11. Event records defined in Task 10,
consumed in Tasks 11–12. `NotificationSender.send(NotificationMessage, NotificationRecipient)` fixed
in Task 5, implemented in Tasks 8–9, called in Task 11. `app.notification.listeners.enabled` — false
in Task 12, flipped in Task 14; `app.notification.kafka-listeners.enabled` (monolith) — added in
Task 13, flipped off in Task 14, deleted in Task 16. Caddy verbs `GET HEAD PUT` consistent between
§4.1 note, Task 5, Task 14.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-08-28-notification-service-extraction.md`.
Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks,
fast iteration. Tasks 1–2 (monolith prep) can even ship on their own first.

**2. Inline Execution** — execute tasks in this session with checkpoints for review.

**Which approach?**
