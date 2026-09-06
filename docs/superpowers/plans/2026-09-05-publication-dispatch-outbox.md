# Publication Dispatch Outbox — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Social publishing dispatches to Meta from a background worker with automatic retry on a fixed backoff, instead of blocking the admin's HTTP request and leaving transient failures for a human to retry.

**Architecture:** The `publications` table is already the queue (`status`, `retry_count`, `last_error_*`, `attempts[]`, unique `idempotency_key`). Add a `next_attempt_at` column and a `RETRY_SCHEDULED` status; generalise the existing scheduled-batch worker into the single dispatch path for every publication (immediate, scheduled, retried); classify Meta errors as transient (retry) or permanent (fail now); "Publicar ahora" creates rows and returns, the worker posts within ~20s.

**Tech Stack:** Java 25, Spring Boot 4.0.7, Flyway, Testcontainers + MockMvc ITs, JUnit 5 + Mockito, Jackson 3 (`tools.jackson`). Frontend: Astro 5 + React islands, Vitest + happy-dom.

**Spec:** `docs/superpowers/specs/2026-09-05-publication-dispatch-outbox-design.md`

## Global Constraints

- Current highest migration **V103** → this adds **V104**. Never edit an applied migration.
- Domain models carry no framework annotations; JPA entities are separate. Use cases take ports.
- `PublicationService` is `@Transactional` on a dedicated bean; batch/worker orchestrators are
  **deliberately not `@Transactional`** (a loop over `@Transactional` calls would go rollback-only
  on the first failure and lose every other row's result).
- Scheduled components: `@Scheduled(cron = "${...}")` on an `infrastructure/jobs/` `@Component`
  delegating to a use case; scheduled use cases take an injected `java.time.Clock` (package-private
  ctor for tests, `@Autowired` ctor passes `Clock.systemUTC()`).
- Config: `application.yml` `app.social-publishing.*` + `${APP_SOCIAL_PUBLISHING_*}` env, mirrored
  in `META-INF/additional-spring-configuration-metadata.json`, `infra/.env.example`, and the
  backend `environment:` block of `infra/docker-compose.yml`.
- Jackson 3: `tools.jackson.databind.ObjectMapper`, not `com.fasterxml`.
- New IT classes that would log in per test must set
  `@TestPropertySource(properties = "app.gateway.rate-limit.login-max-requests=500")` on the class
  and mint tokens with `jwtTokenProvider.generateAccessToken(UUID, String email, UserRole, List.of(), List.of())`,
  never `POST /auth/login`.
- Backoff schedule: **2, 10, 30, 120, 360 minutes** (5 retries after the first attempt = 6 total).
- Worker tick: **every 20 seconds** (`*/20 * * * * *`).
- Stuck-`PUBLISHING` recovery marks the row `FAILED` / `DISPATCH_INTERRUPTED` and does **not**
  auto-retry (a re-dispatch could double-post — Meta's create-container call is not idempotent).
- Commits stay on `develop`; `master` only on the owner's explicit word.
- Copy: Spanish, no em dashes, no `--`.
- `notification-service` does not map `publications` → no `*RoEntity` change; confirm
  `ReadOnlyMappingIT` stays green after V104.

---

## File Structure

**Backend — create**
- `backend/src/main/resources/db/migration/V104__publication_dispatch_queue.sql` — `next_attempt_at` column + partial index + backfill of any live `APPROVED`/`SCHEDULED` rows.
- `backend/.../publication/application/DispatchBackoffPolicy.java` — reads `backoff-minutes`, answers `canRetry(int)` / `delayFor(int)` / `maxRetries()`.
- `backend/.../publication/domain/events/PublicationDispatchScheduledForRetry.java` — domain event.
- `backend/.../publication/infrastructure/meta/MetaErrorClassifier.java` — transient vs permanent.
- `backend/src/test/.../publication/application/DispatchBackoffPolicyTest.java`
- `backend/src/test/.../publication/infrastructure/meta/MetaErrorClassifierTest.java`
- `backend/src/test/.../publication/application/PublicationServiceDispatchRetryTest.java`
- `backend/src/test/.../publication/infrastructure/web/PublicationDispatchOutboxIT.java`

**Backend — rename**
- `PublishDueScheduledPublicationsUseCase` → `DispatchDuePublicationsUseCase` (+ its test)
- `PublishDueScheduledPublicationsScheduler` → `DispatchDuePublicationsScheduler`

**Backend — modify**
- `publication/domain/enums/PublicationStatus.java`
- `publication/infrastructure/persistence/entities/PublicationEntity.java`
- `publication/infrastructure/persistence/repositories/PublicationJpaRepository.java`
- `publication/application/ports/PublicationDispatcher.java`
- `publication/infrastructure/meta/InstagramGraphPublisherAdapter.java`, `FacebookPagePublisherAdapter.java`
- `publication/application/PublicationService.java`
- `publication/application/usecases/PublishProductsBatchUseCase.java`
- `publication/application/dto/PublicationBatchDetailDto.java`, `PublicationBatchSummaryDto.java`
- `resources/application.yml`, `resources/META-INF/additional-spring-configuration-metadata.json`
- `infra/.env.example`, `infra/docker-compose.yml`
- affected tests: `PublicationServiceTest`, `PublishProductsBatchUseCaseTest`, both Meta adapter tests

**Frontend — modify**
- `src/lib/api.ts`
- `src/islands/admin/PublicarTab.tsx`, `src/islands/admin/PublicacionesPage.tsx`, `src/islands/admin/HistorialTab.tsx`
- tests: `PublicarTab.test.tsx`, `PublicacionesPage.test.tsx`, `HistorialTab.test.tsx`

---

## Task 1: Schema and status foundation

**Files:**
- Create: `backend/src/main/resources/db/migration/V104__publication_dispatch_queue.sql`
- Modify: `backend/src/main/java/com/pilarestilo/publication/domain/enums/PublicationStatus.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/entities/PublicationEntity.java`
- Test: existing `backend/src/test/java/com/pilarestilo/**/ReadOnlyMappingIT.java` (runs the real migration set)

**Interfaces:**
- Produces: `PublicationStatus.RETRY_SCHEDULED`; `PublicationEntity.getNextAttemptAt()` /
  `setNextAttemptAt(Instant)` mapping column `next_attempt_at`.

- [ ] **Step 1: Write the migration**

Create `V104__publication_dispatch_queue.sql`:
```sql
-- Publication dispatch outbox: a single "ready to be worked" timestamp + a retry state.
ALTER TABLE publications ADD COLUMN next_attempt_at TIMESTAMPTZ;

-- The worker's hot query: rows waiting to be dispatched whose time has come.
CREATE INDEX idx_publications_dispatch_due
    ON publications (next_attempt_at)
    WHERE status IN ('APPROVED', 'SCHEDULED', 'RETRY_SCHEDULED');

-- Any row currently mid-flight gets a deterministic pickup time instead of NULL-and-ignored.
UPDATE publications
   SET next_attempt_at = COALESCE(scheduled_at, now())
 WHERE status IN ('APPROVED', 'SCHEDULED');
```

- [ ] **Step 2: Add the enum value**

In `PublicationStatus.java`, add `RETRY_SCHEDULED` between `PUBLISHING` and `PUBLISHED`:
```java
public enum PublicationStatus {
    DRAFT,
    AI_READY,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    SCHEDULED,
    PUBLISHING,
    RETRY_SCHEDULED,
    PUBLISHED,
    FAILED,
    CANCELLED
}
```

- [ ] **Step 3: Add the entity field**

In `PublicationEntity.java`, after the `scheduledAt` field (line ~68):
```java
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
```
and with the other getters/setters (after `getScheduledAt`/`setScheduledAt`):
```java
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
```

- [ ] **Step 4: Run the migration-backed IT**

Run: `cd backend && mvn -q test -Dtest=ReadOnlyMappingIT`
Expected: PASS. V104 applies, `ddl-auto: validate` accepts the new column, notification-service's
read-only entities still validate (they do not map `publications`).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V104__publication_dispatch_queue.sql \
        backend/src/main/java/com/pilarestilo/publication/domain/enums/PublicationStatus.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/entities/PublicationEntity.java
git commit -m "feat(publication): V104 next_attempt_at + RETRY_SCHEDULED status"
```

---

## Task 2: Dispatch config block

**Files:**
- Modify: `backend/src/main/resources/application.yml` (lines ~187-189, the `schedule:` block)
- Modify: `backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json` (entries at ~441 and ~457)
- Modify: `infra/.env.example` (lines 176-177)
- Modify: `infra/docker-compose.yml` (lines 166-167)
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishDueScheduledPublicationsUseCase.java` (the `@Value` key only)
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/jobs/PublishDueScheduledPublicationsScheduler.java` (the cron key only)
- Test: an existing `@SpringBootTest` context load, e.g. `PublicationControllerIT`

**Interfaces:**
- Produces: config keys `app.social-publishing.dispatch.{cron, max-lateness-minutes,
  backoff-minutes, batch-size, stuck-publishing-minutes}` with defaults
  `*/20 * * * * *` / `360` / `2,10,30,120,360` / `25` / `15`.

- [ ] **Step 1: Replace the `schedule:` block in `application.yml`**

Under `app.social-publishing:`, replace:
```yaml
    schedule:
      cron: ${APP_SOCIAL_PUBLISHING_SCHEDULE_CRON:0 * * * * *}
      max-lateness-minutes: ${APP_SOCIAL_PUBLISHING_SCHEDULE_MAX_LATENESS_MINUTES:360}
```
with:
```yaml
    dispatch:
      cron: ${APP_SOCIAL_PUBLISHING_DISPATCH_CRON:*/20 * * * * *}
      max-lateness-minutes: ${APP_SOCIAL_PUBLISHING_DISPATCH_MAX_LATENESS_MINUTES:360}
      backoff-minutes: ${APP_SOCIAL_PUBLISHING_DISPATCH_BACKOFF_MINUTES:2,10,30,120,360}
      batch-size: ${APP_SOCIAL_PUBLISHING_DISPATCH_BATCH_SIZE:25}
      stuck-publishing-minutes: ${APP_SOCIAL_PUBLISHING_DISPATCH_STUCK_PUBLISHING_MINUTES:15}
```

- [ ] **Step 2: Swap the two metadata entries**

In `additional-spring-configuration-metadata.json`, replace the `app.social-publishing.schedule.cron`
object and the `app.social-publishing.schedule.max-lateness-minutes` object with:
```json
    {
      "name": "app.social-publishing.dispatch.cron",
      "type": "java.lang.String",
      "description": "Cron for the publication dispatch worker (immediate + scheduled + retry). Default: every 20 seconds."
    },
    {
      "name": "app.social-publishing.dispatch.max-lateness-minutes",
      "type": "java.lang.Long",
      "description": "A SCHEDULED publication more than this many minutes overdue is failed instead of published."
    },
    {
      "name": "app.social-publishing.dispatch.backoff-minutes",
      "type": "java.util.List<java.lang.Integer>",
      "description": "Minutes to wait before each automatic retry. List length is the retry cap. Default: 2,10,30,120,360."
    },
    {
      "name": "app.social-publishing.dispatch.batch-size",
      "type": "java.lang.Integer",
      "description": "Maximum publications dispatched per worker tick.",
      "defaultValue": 25
    },
    {
      "name": "app.social-publishing.dispatch.stuck-publishing-minutes",
      "type": "java.lang.Integer",
      "description": "A publication left in PUBLISHING longer than this (server crashed mid-dispatch) is failed as DISPATCH_INTERRUPTED for manual review.",
      "defaultValue": 15
    }
```
Also rename the group object at line ~100 `"name": "app.social-publishing.schedule"` →
`"name": "app.social-publishing.dispatch"` (keep its `sourceType` if present).

- [ ] **Step 3: Swap the env vars in `.env.example`**

Replace lines 176-177:
```
# APP_SOCIAL_PUBLISHING_DISPATCH_CRON=*/20 * * * * *
# APP_SOCIAL_PUBLISHING_DISPATCH_MAX_LATENESS_MINUTES=360
# APP_SOCIAL_PUBLISHING_DISPATCH_BACKOFF_MINUTES=2,10,30,120,360
# APP_SOCIAL_PUBLISHING_DISPATCH_BATCH_SIZE=25
# APP_SOCIAL_PUBLISHING_DISPATCH_STUCK_PUBLISHING_MINUTES=15
```

- [ ] **Step 4: Swap the env vars in `docker-compose.yml`**

Replace lines 166-167 in the backend `environment:` block:
```yaml
      APP_SOCIAL_PUBLISHING_DISPATCH_CRON: ${APP_SOCIAL_PUBLISHING_DISPATCH_CRON:-*/20 * * * * *}
      APP_SOCIAL_PUBLISHING_DISPATCH_MAX_LATENESS_MINUTES: ${APP_SOCIAL_PUBLISHING_DISPATCH_MAX_LATENESS_MINUTES:-360}
      APP_SOCIAL_PUBLISHING_DISPATCH_BACKOFF_MINUTES: ${APP_SOCIAL_PUBLISHING_DISPATCH_BACKOFF_MINUTES:-2,10,30,120,360}
      APP_SOCIAL_PUBLISHING_DISPATCH_BATCH_SIZE: ${APP_SOCIAL_PUBLISHING_DISPATCH_BATCH_SIZE:-25}
      APP_SOCIAL_PUBLISHING_DISPATCH_STUCK_PUBLISHING_MINUTES: ${APP_SOCIAL_PUBLISHING_DISPATCH_STUCK_PUBLISHING_MINUTES:-15}
```

- [ ] **Step 5: Point the existing use case + scheduler at the new keys**

In `PublishDueScheduledPublicationsUseCase.java` line ~37, change the `@Value` string only:
```java
@Value("${app.social-publishing.dispatch.max-lateness-minutes:360}") long maxLatenessMinutes) {
```
In `PublishDueScheduledPublicationsScheduler.java` line ~20:
```java
@Scheduled(cron = "${app.social-publishing.dispatch.cron:*/20 * * * * *}")
```
(These classes are renamed in Task 5; only the config strings change here so the app still boots.)

- [ ] **Step 6: Run a context-load IT**

Run: `cd backend && mvn -q test -Dtest=PublicationControllerIT`
Expected: PASS. The context binds the new `dispatch` keys; nothing references the removed
`schedule` keys.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/application.yml \
        backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json \
        infra/.env.example infra/docker-compose.yml \
        backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishDueScheduledPublicationsUseCase.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/jobs/PublishDueScheduledPublicationsScheduler.java
git commit -m "feat(publication): app.social-publishing.dispatch.* config block"
```

---

## Task 3: Backoff policy

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/application/DispatchBackoffPolicy.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/application/DispatchBackoffPolicyTest.java`

**Interfaces:**
- Consumes: `app.social-publishing.dispatch.backoff-minutes` (Task 2).
- Produces: `DispatchBackoffPolicy` bean —
  `boolean canRetry(int retryCount)`, `Duration delayFor(int retryCount)`, `int maxRetries()`.

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.publication.application;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DispatchBackoffPolicyTest {

    private final DispatchBackoffPolicy policy =
            new DispatchBackoffPolicy(List.of(2, 10, 30, 120, 360));

    @Test
    void delay_for_each_retry_index_matches_the_configured_minutes() {
        assertEquals(Duration.ofMinutes(2), policy.delayFor(0));
        assertEquals(Duration.ofMinutes(10), policy.delayFor(1));
        assertEquals(Duration.ofMinutes(30), policy.delayFor(2));
        assertEquals(Duration.ofMinutes(120), policy.delayFor(3));
        assertEquals(Duration.ofMinutes(360), policy.delayFor(4));
    }

    @Test
    void can_retry_until_the_list_is_exhausted() {
        assertTrue(policy.canRetry(0));
        assertTrue(policy.canRetry(4));
        assertFalse(policy.canRetry(5));
        assertEquals(5, policy.maxRetries());
    }
}
```

- [ ] **Step 2: Run it, expect compile failure**

Run: `cd backend && mvn -q test -Dtest=DispatchBackoffPolicyTest`
Expected: FAIL — `DispatchBackoffPolicy` does not exist.

- [ ] **Step 3: Implement**

```java
package com.pilarestilo.publication.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * The fixed backoff schedule for automatic dispatch retries. {@code retryCount} is how many
 * automatic retries the row has already made (0 on the first failure). The list length is the cap.
 */
@Component
public class DispatchBackoffPolicy {

    private final List<Integer> backoffMinutes;

    public DispatchBackoffPolicy(
            @Value("${app.social-publishing.dispatch.backoff-minutes}") List<Integer> backoffMinutes) {
        this.backoffMinutes = List.copyOf(backoffMinutes);
    }

    public boolean canRetry(int retryCount) {
        return retryCount < backoffMinutes.size();
    }

    public Duration delayFor(int retryCount) {
        return Duration.ofMinutes(backoffMinutes.get(retryCount));
    }

    public int maxRetries() {
        return backoffMinutes.size();
    }
}
```

- [ ] **Step 4: Run the test, expect PASS**

Run: `cd backend && mvn -q test -Dtest=DispatchBackoffPolicyTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/DispatchBackoffPolicy.java \
        backend/src/test/java/com/pilarestilo/publication/application/DispatchBackoffPolicyTest.java
git commit -m "feat(publication): DispatchBackoffPolicy"
```

---

## Task 4: Meta error classification + `DispatchResult.retryable`

**Files:**
- Modify: `backend/.../publication/application/ports/PublicationDispatcher.java`
- Create: `backend/.../publication/infrastructure/meta/MetaErrorClassifier.java`
- Modify: `backend/.../publication/infrastructure/meta/InstagramGraphPublisherAdapter.java`
- Modify: `backend/.../publication/infrastructure/meta/FacebookPagePublisherAdapter.java`
- Modify: `backend/.../publication/application/PublicationService.java` (only the `catch` fallback in `dispatchInternal`)
- Test: `backend/src/test/.../publication/infrastructure/meta/MetaErrorClassifierTest.java` (new)
- Test: `backend/src/test/.../publication/infrastructure/meta/*PublisherAdapterTest.java` (update constructor calls)

**Interfaces:**
- Produces: `PublicationDispatcher.DispatchResult` gains a **final** `boolean retryable` component
  (8th, last). `MetaErrorClassifier.isRetryable(Throwable)` → `boolean`. Adapter helper signature
  becomes `failed(String message, boolean retryable)`.

- [ ] **Step 1: Add `retryable` to the record**

In `PublicationDispatcher.java`:
```java
    record DispatchResult(
            String requestId,
            String payloadHash,
            PublicationAttemptStatus status,
            String remotePostId,
            String errorCode,
            String errorMessage,
            String remotePermalink,
            boolean retryable
    ) {}
```

- [ ] **Step 2: Write the classifier test**

```java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MetaErrorClassifierTest {

    @Test
    void http_429_is_retryable() {
        assertTrue(MetaErrorClassifier.isRetryable(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "", null, null, null)));
    }

    @Test
    void meta_code_190_bad_token_is_permanent() {
        var ex = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "",
                null, "{\"error\":{\"code\":190,\"message\":\"expired\"}}".getBytes(), null);
        assertFalse(MetaErrorClassifier.isRetryable(ex));
    }

    @Test
    void meta_rate_limit_code_4_is_retryable() {
        var ex = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "",
                null, "{\"error\":{\"code\":4}}".getBytes(), null);
        assertTrue(MetaErrorClassifier.isRetryable(ex));
    }

    @Test
    void http_5xx_is_retryable() {
        assertTrue(MetaErrorClassifier.isRetryable(
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null)));
    }

    @Test
    void connection_timeout_is_retryable() {
        assertTrue(MetaErrorClassifier.isRetryable(new ResourceAccessException("timeout", new IOException())));
    }

    @Test
    void container_not_ready_is_retryable() {
        assertTrue(MetaErrorClassifier.isRetryable(
                new IllegalStateException("Instagram media container was not ready after 10 checks")));
    }

    @Test
    void container_error_is_permanent() {
        assertFalse(MetaErrorClassifier.isRetryable(
                new IllegalStateException("Instagram media container error before it could be published")));
    }

    @Test
    void missing_config_domain_exception_is_permanent() {
        assertFalse(MetaErrorClassifier.isRetryable(
                new DomainException("Instagram credentials are not configured")));
    }

    @Test
    void unknown_runtime_error_defaults_to_retryable() {
        assertTrue(MetaErrorClassifier.isRetryable(new RuntimeException("something odd")));
    }
}
```

- [ ] **Step 3: Run it, expect compile failure**

Run: `cd backend && mvn -q test -Dtest=MetaErrorClassifierTest`
Expected: FAIL — `MetaErrorClassifier` does not exist.

- [ ] **Step 4: Implement the classifier**

```java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.shared.domain.DomainException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/** Decides whether a failed Meta dispatch should be retried automatically or failed for a human. */
final class MetaErrorClassifier {

    private static final Set<Integer> RATE_LIMIT_CODES = Set.of(4, 17, 32, 613);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MetaErrorClassifier() {}

    static boolean isRetryable(Throwable ex) {
        if (ex instanceof DomainException) {
            return false;                                   // missing config / no media URL — retrying changes nothing
        }
        if (ex instanceof HttpServerErrorException) {
            return true;                                    // 5xx
        }
        if (ex instanceof ResourceAccessException) {
            return true;                                    // connect / read timeout
        }
        if (ex instanceof HttpClientErrorException http) {
            if (http.getStatusCode().value() == 429) {
                return true;
            }
            Integer metaCode = extractMetaErrorCode(http.getResponseBodyAsString());
            if (metaCode != null && RATE_LIMIT_CODES.contains(metaCode)) {
                return true;
            }
            return false;                                   // 190 token, 10/200-299 perms, 100 bad param, etc.
        }
        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        if (msg.contains("was not ready after")) {
            return true;                                    // container still processing
        }
        if (msg.contains("error before it could be published")
                || msg.contains("expired before it could be published")) {
            return false;                                   // media rejected by Meta
        }
        return true;                                        // unknown adapter/parse error — give it the retry budget
    }

    private static Integer extractMetaErrorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            JsonNode code = node.path("error").path("code");
            return code.isNumber() ? code.asInt() : null;
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }
}
```

- [ ] **Step 5: Run the classifier test, expect PASS**

Run: `cd backend && mvn -q test -Dtest=MetaErrorClassifierTest`
Expected: PASS.

- [ ] **Step 6: Wire `retryable` through the Instagram adapter**

In `InstagramGraphPublisherAdapter.java`:
- The success `return new PublicationDispatcher.DispatchResult(...)` gains a trailing `, true`.
- `catch (RuntimeException ex) { return failed(ex.getMessage()); }` →
  `catch (RuntimeException ex) { return failed(ex.getMessage(), MetaErrorClassifier.isRetryable(ex)); }`
- The early `if (config.instagramUserId() == null ...) return failed("Instagram credentials are not configured");`
  → `return failed("Instagram credentials are not configured", false);`
- `failed`:
```java
    private PublicationDispatcher.DispatchResult failed(String message, boolean retryable) {
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.FAILED, null,
                "INSTAGRAM_PUBLISH_ERROR", message, null, retryable);
    }
```

- [ ] **Step 7: Wire `retryable` through the Facebook adapter**

Same shape in `FacebookPagePublisherAdapter.java`: both success `DispatchResult` constructions
(`publishSinglePhoto`, `publishCarousel`) gain trailing `, true`; the `catch` becomes
`failed(ex.getMessage(), MetaErrorClassifier.isRetryable(ex))`; the credentials early-return
becomes `failed("Facebook credentials are not configured", false)`; `failed` gains the
`boolean retryable` param and passes it as the 8th `DispatchResult` arg with code
`"FACEBOOK_PUBLISH_ERROR"`.

- [ ] **Step 8: Fix the `dispatchInternal` catch fallback**

In `PublicationService.java` `dispatchInternal`, the local `catch (RuntimeException ex)` that builds
a `DispatchResult` with `DISPATCH_ERROR`:
```java
        } catch (RuntimeException ex) {
            boolean retryable = !(ex instanceof com.pilarestilo.shared.domain.DomainException);
            result = new PublicationDispatcher.DispatchResult(
                    null, null, PublicationAttemptStatus.FAILED, null, DISPATCH_ERROR_CODE, ex.getMessage(), null, retryable);
        }
```

- [ ] **Step 9: Update every other `DispatchResult(...)` construction site**

Search: `grep -rn "new PublicationDispatcher.DispatchResult(" backend/src` and
`grep -rn "DispatchResult(" backend/src/test`. Add a trailing boolean to each — `true` for
success fixtures, `false` for "permanent failure" fixtures, matching the test's intent. Known
sites: `InstagramGraphPublisherAdapterTest`, `FacebookPagePublisherAdapterTest`,
`MetaDirectPublicationDispatcherTest` (if present), `PublicationServiceTest` helper
`failedDto`/`dispatchResult` builders.

- [ ] **Step 10: Run the Meta + service test slice**

Run: `cd backend && mvn -q test -Dtest='*PublisherAdapterTest,MetaDirectPublicationDispatcherTest,MetaErrorClassifierTest,PublicationServiceTest'`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/ports/PublicationDispatcher.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/ \
        backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java \
        backend/src/test/java/com/pilarestilo/publication/
git commit -m "feat(publication): classify Meta dispatch errors as transient or permanent"
```

---

## Task 5: `PublicationService` retry / permanent split

**Files:**
- Modify: `backend/.../publication/application/PublicationService.java`
- Create: `backend/.../publication/domain/events/PublicationDispatchScheduledForRetry.java`
- Modify: `backend/src/test/.../publication/application/PublicationServiceTest.java` (ctor arg)
- Test: `backend/src/test/.../publication/application/PublicationServiceDispatchRetryTest.java` (new)

**Interfaces:**
- Consumes: `DispatchBackoffPolicy` (Task 3); `DispatchResult.retryable` (Task 4);
  `PublicationStatus.RETRY_SCHEDULED`, `PublicationEntity.nextAttemptAt` (Task 1).
- Produces:
  - `PublicationService` ctor gains a 7th param `DispatchBackoffPolicy backoffPolicy`.
  - `dispatchInternal` guard accepts `RETRY_SCHEDULED`; on a non-`SUCCEEDED` result it either
    schedules a retry (`RETRY_SCHEDULED` + `retryCount++` + `nextAttemptAt = now + backoff`) or
    terminally fails (`FAILED` + `nextAttemptAt = null`).
  - `create()` and `approve()` set `nextAttemptAt`.
  - `retry(UUID, UUID)` becomes async: `status RETRY_SCHEDULED`, `nextAttemptAt = now`,
    `retryCount = 0`, clears `lastError*`, **no inline dispatch**.
  - New `dispatchFromWorker(UUID id)` — `@Transactional`, drives `dispatchInternal` with
    `PublicationAttemptTriggerType.RETRY` when the row was `RETRY_SCHEDULED`, else `SCHEDULED`.
  - New `markDispatchInterrupted(UUID id)` — `@Transactional`, guard `status == PUBLISHING`,
    → `FAILED` / `DISPATCH_INTERRUPTED` / `nextAttemptAt = null`, publishes `PublicationDispatchFailed`.
  - New event `PublicationDispatchScheduledForRetry(UUID publicationId, int retryCount, Instant nextAttemptAt)`.

- [ ] **Step 1: Create the event**

```java
package com.pilarestilo.publication.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record PublicationDispatchScheduledForRetry(
        UUID publicationId, int retryCount, Instant nextAttemptAt) implements DomainEvent {
}
```

- [ ] **Step 2: Write the failing test**

`PublicationServiceDispatchRetryTest.java`:
```java
package com.pilarestilo.publication.application;

import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.*;
import com.pilarestilo.publication.domain.events.PublicationDispatchFailed;
import com.pilarestilo.publication.domain.events.PublicationDispatchScheduledForRetry;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMediaBundleEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicationServiceDispatchRetryTest {

    @Mock PublicationBatchJpaRepository batchRepo;
    @Mock PublicationJpaRepository pubRepo;
    @Mock ProductRepository productRepo;
    @Mock PublicationDispatcher dispatcher;
    @Mock DomainEventPublisher events;

    PublicationService service;

    @BeforeEach
    void setUp() {
        service = new PublicationService(batchRepo, pubRepo, productRepo, dispatcher, events,
                new ObjectMapper(), new DispatchBackoffPolicy(List.of(2, 10, 30, 120, 360)));
        when(pubRepo.save(any(PublicationEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private PublicationEntity approvedRow() {
        PublicationEntity e = new PublicationEntity();
        e.setId(UUID.randomUUID());
        e.setStatus(PublicationStatus.APPROVED);
        e.setPlatform(PublicationPlatform.INSTAGRAM);
        e.setChannelType(PublicationChannelType.FEED_POST);
        e.setSourceType(PublicationSourceType.MANUAL);
        e.setIdempotencyKey("k-" + e.getId());
        e.setHashtagsJson("[]");
        e.setRetryCount(0);
        e.setAttempts(new ArrayList<>());
        e.setSnapshots(new ArrayList<>());
        e.setMediaBundles(new ArrayList<>());
        PublicationMediaBundleEntity b = new PublicationMediaBundleEntity();
        b.setId(UUID.randomUUID());
        b.setPublication(e);
        b.setPrimaryAssetUrl("https://cdn.example.com/a.jpg");
        e.getMediaBundles().add(b);
        return e;
    }

    private PublicationDispatcher.DispatchResult transientFailure() {
        return new PublicationDispatcher.DispatchResult(null, null,
                PublicationAttemptStatus.FAILED, null, "INSTAGRAM_PUBLISH_ERROR", "429", null, true);
    }

    private PublicationDispatcher.DispatchResult permanentFailure() {
        return new PublicationDispatcher.DispatchResult(null, null,
                PublicationAttemptStatus.FAILED, null, "INSTAGRAM_PUBLISH_ERROR", "bad token", null, false);
    }

    private PublicationDispatcher.DispatchResult success() {
        return new PublicationDispatcher.DispatchResult("req", null,
                PublicationAttemptStatus.SUCCEEDED, "ig-1", null, null, "https://instagram.com/p/x", true);
    }

    @Test
    void transient_failure_on_first_attempt_schedules_a_retry_two_minutes_out() {
        PublicationEntity row = approvedRow();
        when(pubRepo.findById(row.getId())).thenReturn(Optional.of(row));
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(transientFailure());
        Instant before = Instant.now();

        service.dispatch(row.getId(), null);

        assertEquals(PublicationStatus.RETRY_SCHEDULED, row.getStatus());
        assertEquals(1, row.getRetryCount());
        assertNotNull(row.getNextAttemptAt());
        long gapMin = ChronoUnit.MINUTES.between(before, row.getNextAttemptAt());
        assertTrue(gapMin >= 1 && gapMin <= 3, "retry ~2 min out, was " + gapMin);
        verify(events).publish(any(PublicationDispatchScheduledForRetry.class));
    }

    @Test
    void transient_failure_after_the_retry_budget_is_terminal() {
        PublicationEntity row = approvedRow();
        row.setRetryCount(5);                       // budget exhausted
        row.setStatus(PublicationStatus.RETRY_SCHEDULED);
        when(pubRepo.findById(row.getId())).thenReturn(Optional.of(row));
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(transientFailure());

        service.dispatch(row.getId(), null);

        assertEquals(PublicationStatus.FAILED, row.getStatus());
        assertNull(row.getNextAttemptAt());
        verify(events).publish(any(PublicationDispatchFailed.class));
    }

    @Test
    void permanent_failure_fails_immediately_regardless_of_budget() {
        PublicationEntity row = approvedRow();
        when(pubRepo.findById(row.getId())).thenReturn(Optional.of(row));
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(permanentFailure());

        service.dispatch(row.getId(), null);

        assertEquals(PublicationStatus.FAILED, row.getStatus());
        assertEquals(0, row.getRetryCount());
        assertNull(row.getNextAttemptAt());
    }

    @Test
    void success_clears_the_next_attempt() {
        PublicationEntity row = approvedRow();
        when(pubRepo.findById(row.getId())).thenReturn(Optional.of(row));
        when(dispatcher.dispatch(any(), any(), any())).thenReturn(success());

        service.dispatch(row.getId(), null);

        assertEquals(PublicationStatus.PUBLISHED, row.getStatus());
        assertNull(row.getNextAttemptAt());
    }

    @Test
    void manual_retry_resets_the_budget_and_does_not_dispatch_inline() {
        PublicationEntity row = approvedRow();
        row.setStatus(PublicationStatus.FAILED);
        row.setRetryCount(5);
        row.setLastErrorCode("INSTAGRAM_PUBLISH_ERROR");
        when(pubRepo.findById(row.getId())).thenReturn(Optional.of(row));

        service.retry(row.getId(), null);

        assertEquals(PublicationStatus.RETRY_SCHEDULED, row.getStatus());
        assertEquals(0, row.getRetryCount());
        assertNull(row.getLastErrorCode());
        assertNotNull(row.getNextAttemptAt());
        verify(dispatcher, never()).dispatch(any(), any(), any());
    }
}
```

- [ ] **Step 3: Run it, expect failure**

Run: `cd backend && mvn -q test -Dtest=PublicationServiceDispatchRetryTest`
Expected: FAIL — ctor arity, `RETRY_SCHEDULED` guard, retry semantics.

- [ ] **Step 4: Add the ctor param + field**

In `PublicationService.java` add `private final DispatchBackoffPolicy backoffPolicy;`, add it as the
last constructor parameter, assign it. Update `PublicationServiceTest.setUp()`:
```java
        service = new PublicationService(
                publicationBatchRepository, publicationRepository, productRepository,
                publicationDispatcher, eventPublisher, new ObjectMapper(),
                new DispatchBackoffPolicy(java.util.List.of(2, 10, 30, 120, 360)));
```

- [ ] **Step 5: `create()` and `approve()` set `nextAttemptAt`**

In `create()`, right after `entity.setStatus(initialStatus);` (line ~111), add:
```java
        if (initialStatus == PublicationStatus.APPROVED) {
            entity.setNextAttemptAt(now);
        } else if (initialStatus == PublicationStatus.SCHEDULED) {
            entity.setNextAttemptAt(command.scheduledAt());
        }
```
In `approve()`, after `entity.setStatus(PublicationStatus.APPROVED);` add
`entity.setNextAttemptAt(Instant.now());`.

- [ ] **Step 6: `dispatchInternal` guard + retry/permanent split**

Guard (line ~346):
```java
        if (!(entity.getStatus() == PublicationStatus.APPROVED
                || entity.getStatus() == PublicationStatus.SCHEDULED
                || entity.getStatus() == PublicationStatus.RETRY_SCHEDULED)) {
            throw new DomainException("Publication cannot be dispatched from status " + entity.getStatus());
        }
```
Success branch: add `entity.setNextAttemptAt(null);` before `publicationRepository.save(entity)`.
Replace the terminal `FAILED` tail (lines ~405-410) with:
```java
        int retriesDone = entity.getRetryCount();
        if (result.retryable() && backoffPolicy.canRetry(retriesDone)) {
            entity.setRetryCount(retriesDone + 1);
            entity.setStatus(PublicationStatus.RETRY_SCHEDULED);
            entity.setNextAttemptAt(Instant.now().plus(backoffPolicy.delayFor(retriesDone)));
            entity.setLastErrorCode(result.errorCode());
            entity.setLastErrorMessage(result.errorMessage());
            PublicationEntity saved = publicationRepository.save(entity);
            eventPublisher.publish(new PublicationDispatchScheduledForRetry(
                    saved.getId(), saved.getRetryCount(), saved.getNextAttemptAt()));
            return toDto(saved);
        }
        entity.setStatus(PublicationStatus.FAILED);
        entity.setNextAttemptAt(null);
        entity.setLastErrorCode(result.errorCode());
        entity.setLastErrorMessage(result.errorMessage());
        PublicationEntity saved = publicationRepository.save(entity);
        eventPublisher.publish(new PublicationDispatchFailed(saved.getId(), attempt.getAttemptNumber(), result.errorCode()));
        return toDto(saved);
```

- [ ] **Step 7: Rewrite `retry()`**

```java
    @Transactional
    public PublicationDto retry(UUID id, UUID actorUserId) {
        PublicationEntity entity = findById(id);
        if (!(entity.getStatus() == PublicationStatus.FAILED
                || entity.getStatus() == PublicationStatus.RETRY_SCHEDULED)) {
            throw new DomainException("Only FAILED or RETRY_SCHEDULED publications can be retried");
        }
        entity.setStatus(PublicationStatus.RETRY_SCHEDULED);
        entity.setRetryCount(0);
        entity.setNextAttemptAt(Instant.now());
        entity.setLastErrorCode(null);
        entity.setLastErrorMessage(null);
        entity.setUpdatedAt(Instant.now());
        return toDto(publicationRepository.save(entity));
    }
```

- [ ] **Step 8: Add `dispatchFromWorker` and `markDispatchInterrupted`**

```java
    @Transactional
    public PublicationDto dispatchFromWorker(UUID id) {
        PublicationEntity entity = findById(id);
        PublicationAttemptTriggerType trigger = entity.getStatus() == PublicationStatus.RETRY_SCHEDULED
                ? PublicationAttemptTriggerType.RETRY
                : PublicationAttemptTriggerType.SCHEDULED;
        return dispatchInternal(id, trigger);
    }

    @Transactional
    public PublicationDto markDispatchInterrupted(UUID id) {
        PublicationEntity entity = findById(id);
        if (entity.getStatus() != PublicationStatus.PUBLISHING) {
            throw new DomainException("Publication is not mid-dispatch: " + entity.getStatus());
        }
        entity.setStatus(PublicationStatus.FAILED);
        entity.setNextAttemptAt(null);
        entity.setLastErrorCode("DISPATCH_INTERRUPTED");
        entity.setLastErrorMessage(
                "El servidor se reinicio mientras publicaba esta pieza. Revisa Instagram o Facebook: "
                        + "si ya salio marcala como lista, si no reintentala.");
        entity.setUpdatedAt(Instant.now());
        PublicationEntity saved = publicationRepository.save(entity);
        eventPublisher.publish(new PublicationDispatchFailed(saved.getId(), 0, "DISPATCH_INTERRUPTED"));
        return toDto(saved);
    }
```
Add `import com.pilarestilo.publication.domain.events.PublicationDispatchScheduledForRetry;` and
`import java.time.Duration;` if not present.

- [ ] **Step 9: Run the new + existing service tests**

Run: `cd backend && mvn -q test -Dtest='PublicationServiceDispatchRetryTest,PublicationServiceTest'`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/ backend/src/test/java/com/pilarestilo/publication/
git commit -m "feat(publication): automatic retry with backoff in dispatchInternal"
```

---

## Task 6: Generalise the worker

**Files:**
- Rename: `PublishDueScheduledPublicationsUseCase.java` → `DispatchDuePublicationsUseCase.java`
- Rename: `PublishDueScheduledPublicationsScheduler.java` → `DispatchDuePublicationsScheduler.java`
- Rename: `PublishDueScheduledPublicationsUseCaseTest.java` → `DispatchDuePublicationsUseCaseTest.java`
- Modify: `backend/.../publication/infrastructure/persistence/repositories/PublicationJpaRepository.java`

**Interfaces:**
- Consumes: `PublicationService.dispatchFromWorker(UUID)`, `markScheduleWindowMissed(UUID)`,
  `markDispatchInterrupted(UUID)`; `PublicationStatus.RETRY_SCHEDULED`;
  config `app.social-publishing.dispatch.{cron, max-lateness-minutes, batch-size, stuck-publishing-minutes}`.
- Produces: `DispatchDuePublicationsUseCase.execute()` → `int` (rows handled).
  Repo: `findByStatusAndUpdatedAtLessThan(PublicationStatus, Instant)`;
  `findDueForDispatch(Instant now, Pageable pageable)`.

- [ ] **Step 1: Add the repository queries, remove the old one**

In `PublicationJpaRepository.java`, delete
`findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc` and add:
```java
    List<PublicationEntity> findByStatusAndUpdatedAtLessThan(PublicationStatus status, Instant cutoff);

    @org.springframework.data.jpa.repository.Query("""
            select p from PublicationEntity p
            where p.status in (
                com.pilarestilo.publication.domain.enums.PublicationStatus.APPROVED,
                com.pilarestilo.publication.domain.enums.PublicationStatus.SCHEDULED,
                com.pilarestilo.publication.domain.enums.PublicationStatus.RETRY_SCHEDULED)
              and p.nextAttemptAt is not null
              and p.nextAttemptAt <= :now
            order by p.nextAttemptAt asc
            """)
    List<PublicationEntity> findDueForDispatch(Instant now, org.springframework.data.domain.Pageable pageable);
```
Add `import org.springframework.data.domain.Pageable;` if you prefer the short form.

- [ ] **Step 2: Rewrite the use case test**

`DispatchDuePublicationsUseCaseTest.java` (package unchanged):
```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchDuePublicationsUseCaseTest {

    @Mock PublicationJpaRepository publicationRepository;
    @Mock PublicationService publicationService;

    private final Instant now = Instant.parse("2026-09-06T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private DispatchDuePublicationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DispatchDuePublicationsUseCase(publicationRepository, publicationService, clock, 360L, 25, 15);
        when(publicationRepository.findByStatusAndUpdatedAtLessThan(eq(PublicationStatus.PUBLISHING), any()))
                .thenReturn(List.of());
    }

    private PublicationEntity row(PublicationStatus status, Instant scheduledAt) {
        PublicationEntity e = new PublicationEntity();
        e.setId(UUID.randomUUID());
        e.setStatus(status);
        e.setPlatform(PublicationPlatform.INSTAGRAM);
        e.setScheduledAt(scheduledAt);
        return e;
    }

    @Test
    void dispatches_a_due_approved_row() {
        PublicationEntity due = row(PublicationStatus.APPROVED, null);
        when(publicationRepository.findDueForDispatch(eq(now), any(Pageable.class))).thenReturn(List.of(due));

        assertEquals(1, useCase.execute());
        verify(publicationService).dispatchFromWorker(due.getId());
    }

    @Test
    void fails_a_scheduled_row_that_is_way_overdue() {
        PublicationEntity stale = row(PublicationStatus.SCHEDULED, now.minusSeconds(7 * 3600));
        when(publicationRepository.findDueForDispatch(eq(now), any(Pageable.class))).thenReturn(List.of(stale));

        useCase.execute();
        verify(publicationService).markScheduleWindowMissed(stale.getId());
        verify(publicationService, never()).dispatchFromWorker(stale.getId());
    }

    @Test
    void recovers_a_stuck_publishing_row() {
        PublicationEntity stuck = row(PublicationStatus.PUBLISHING, null);
        when(publicationRepository.findByStatusAndUpdatedAtLessThan(eq(PublicationStatus.PUBLISHING), any()))
                .thenReturn(List.of(stuck));
        when(publicationRepository.findDueForDispatch(eq(now), any(Pageable.class))).thenReturn(List.of());

        useCase.execute();
        verify(publicationService).markDispatchInterrupted(stuck.getId());
    }

    @Test
    void one_row_throwing_does_not_stop_the_pass() {
        PublicationEntity a = row(PublicationStatus.APPROVED, null);
        PublicationEntity b = row(PublicationStatus.APPROVED, null);
        when(publicationRepository.findDueForDispatch(eq(now), any(Pageable.class))).thenReturn(List.of(a, b));
        when(publicationService.dispatchFromWorker(a.getId())).thenThrow(new DomainException("boom"));

        assertEquals(1, useCase.execute());
        verify(publicationService).dispatchFromWorker(b.getId());
    }
}
```

- [ ] **Step 3: Run it, expect failure**

Run: `cd backend && mvn -q test -Dtest=DispatchDuePublicationsUseCaseTest`
Expected: FAIL — class not renamed yet.

- [ ] **Step 4: Rename + rewrite the use case**

`git mv backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishDueScheduledPublicationsUseCase.java backend/src/main/java/com/pilarestilo/publication/application/usecases/DispatchDuePublicationsUseCase.java`
then replace its body:
```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The single dispatch path for every publication: immediate (APPROVED), scheduled (SCHEDULED),
 * and automatic retries (RETRY_SCHEDULED) all become due rows here. Not @Transactional: each
 * dispatch is its own @Transactional call on PublicationService, so one failure does not roll back
 * the others. A row left in PUBLISHING past {@code stuckPublishingMinutes} (server crashed
 * mid-dispatch) is failed as DISPATCH_INTERRUPTED and NOT re-dispatched.
 */
@Component
public class DispatchDuePublicationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(DispatchDuePublicationsUseCase.class);

    private final PublicationJpaRepository publicationRepository;
    private final PublicationService publicationService;
    private final Clock clock;
    private final long maxLatenessMinutes;
    private final int batchSize;
    private final int stuckPublishingMinutes;

    @Autowired
    public DispatchDuePublicationsUseCase(
            PublicationJpaRepository publicationRepository,
            PublicationService publicationService,
            @Value("${app.social-publishing.dispatch.max-lateness-minutes:360}") long maxLatenessMinutes,
            @Value("${app.social-publishing.dispatch.batch-size:25}") int batchSize,
            @Value("${app.social-publishing.dispatch.stuck-publishing-minutes:15}") int stuckPublishingMinutes) {
        this(publicationRepository, publicationService, Clock.systemUTC(),
                maxLatenessMinutes, batchSize, stuckPublishingMinutes);
    }

    DispatchDuePublicationsUseCase(PublicationJpaRepository publicationRepository,
                                   PublicationService publicationService,
                                   Clock clock,
                                   long maxLatenessMinutes,
                                   int batchSize,
                                   int stuckPublishingMinutes) {
        this.publicationRepository = publicationRepository;
        this.publicationService = publicationService;
        this.clock = clock;
        this.maxLatenessMinutes = maxLatenessMinutes;
        this.batchSize = batchSize;
        this.stuckPublishingMinutes = stuckPublishingMinutes;
    }

    public int execute() {
        Instant now = Instant.now(clock);
        int handled = 0;

        Instant stuckBefore = now.minus(Duration.ofMinutes(stuckPublishingMinutes));
        for (PublicationEntity p : publicationRepository.findByStatusAndUpdatedAtLessThan(
                PublicationStatus.PUBLISHING, stuckBefore)) {
            try {
                publicationService.markDispatchInterrupted(p.getId());
                handled++;
            } catch (RuntimeException ex) {
                log.warn("Could not recover stuck publication {}: {}", p.getId(), ex.getMessage());
            }
        }

        Instant staleBefore = now.minus(Duration.ofMinutes(maxLatenessMinutes));
        for (PublicationEntity p : publicationRepository.findDueForDispatch(now, PageRequest.of(0, batchSize))) {
            try {
                if (p.getStatus() == PublicationStatus.SCHEDULED
                        && p.getScheduledAt() != null && p.getScheduledAt().isBefore(staleBefore)) {
                    publicationService.markScheduleWindowMissed(p.getId());
                } else {
                    publicationService.dispatchFromWorker(p.getId());
                }
                handled++;
            } catch (RuntimeException ex) {
                log.warn("Publication {} could not be dispatched: {}", p.getId(), ex.getMessage());
            }
        }
        return handled;
    }
}
```

- [ ] **Step 5: Rename + rewrite the scheduler**

`git mv .../jobs/PublishDueScheduledPublicationsScheduler.java .../jobs/DispatchDuePublicationsScheduler.java`
then:
```java
package com.pilarestilo.publication.infrastructure.jobs;

import com.pilarestilo.publication.application.usecases.DispatchDuePublicationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DispatchDuePublicationsScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispatchDuePublicationsScheduler.class);

    private final DispatchDuePublicationsUseCase useCase;

    public DispatchDuePublicationsScheduler(DispatchDuePublicationsUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(cron = "${app.social-publishing.dispatch.cron:*/20 * * * * *}")
    public void run() {
        int handled = useCase.execute();
        if (handled > 0) {
            log.info("Dispatch worker handled {} publications", handled);
        }
    }
}
```

- [ ] **Step 6: `git mv` the test and run it**

`git mv .../PublishDueScheduledPublicationsUseCaseTest.java .../DispatchDuePublicationsUseCaseTest.java`
then paste the Step 2 body.
Run: `cd backend && mvn -q test -Dtest=DispatchDuePublicationsUseCaseTest`
Expected: PASS.

- [ ] **Step 7: Full publication package compile check**

Run: `cd backend && mvn -q test-compile`
Expected: SUCCESS — no dangling references to the old class names or the removed repo method.

- [ ] **Step 8: Commit**

```bash
git add -A backend/src/main/java/com/pilarestilo/publication backend/src/test/java/com/pilarestilo/publication
git commit -m "feat(publication): DispatchDuePublicationsUseCase — one worker for now/scheduled/retry"
```

---

## Task 7: "Publicar ahora" becomes async

**Files:**
- Modify: `backend/.../publication/application/usecases/PublishProductsBatchUseCase.java`
- Modify: `backend/src/test/.../publication/application/usecases/PublishProductsBatchUseCaseTest.java`

**Interfaces:**
- Consumes: `PublicationService.create` (unchanged). No longer calls `PublicationService.dispatch`.
- Produces: for the immediate path, every created row's `PublicationItemResult` is
  `(productId, platform, false, publicationId, null, false)` — the row is queued, not
  published-or-failed. Creation failures still report `success=false` + `errorMessage`.

- [ ] **Step 1: Update the failing tests first**

In `PublishProductsBatchUseCaseTest.java`:
- Delete every `when(publicationService.dispatch(eq(publicationId), any()))...` stub.
- `interpolates_caption_template_..._dispatches_each_selected_platform`: rename to
  `..._queues_each_selected_platform`; change
  `assertTrue(result.items().stream().allMatch(...::success))` to
  `assertTrue(result.items().stream().noneMatch(PublishProductsBatchResult.PublicationItemResult::success))`
  and add `assertTrue(result.items().stream().allMatch(i -> i.publicationId() != null));`
  and `verify(publicationService, never()).dispatch(any(), any());`.
- `one_missing_product_does_not_stop_the_rest_of_the_batch`: `assertTrue(result.items().get(1).success())`
  → `assertFalse(result.items().get(1).success()); assertNotNull(result.items().get(1).publicationId());`
- `a_dispatch_result_that_is_not_published_is_reported_as_a_failure_with_its_error`: this test's
  premise is gone. Replace it with `a_create_failure_is_reported_per_item`:
```java
    @Test
    void a_create_failure_is_reported_per_item() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Falda", "desc", new Money(BigDecimal.valueOf(8000), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 1);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenThrow(new DomainException("clave idempotente en conflicto"));

        PublishProductsBatchResult result = useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.INSTAGRAM), "{producto}", List.of(), null, Map.of(), Map.of(),
                null), UUID.randomUUID());

        assertFalse(result.items().get(0).success());
        assertEquals("clave idempotente en conflicto", result.items().get(0).errorMessage());
    }
```
- The scheduled-path test (`sends scheduledAt when the batch is scheduled` or similar) is unchanged.

- [ ] **Step 2: Run the test, expect failure**

Run: `cd backend && mvn -q test -Dtest=PublishProductsBatchUseCaseTest`
Expected: FAIL — `publishOne` still calls `dispatch`.

- [ ] **Step 3: Drop the inline dispatch**

In `PublishProductsBatchUseCase.publishOne`, replace the body of the `try` after `create`:
```java
            CreatePublicationCommand createCommand =
                    factory.buildCreateCommand(command, productId, product, platform, caption, batchId);
            CreatePublicationResult created = publicationService.create(createCommand, actorUserId);
            // Immediate and scheduled both leave the row for the dispatch worker. "scheduled" here
            // still means "the admin picked a future time" (drives the confirmation copy).
            return new PublishProductsBatchResult.PublicationItemResult(
                    productId, platform, false, created.publication().id(), null, scheduled);
```
Delete the now-unused `PublicationDto dispatched = ...` block and the `PublicationStatus` /
`PublicationDto` imports if they become unused.

- [ ] **Step 4: Run the test, expect PASS**

Run: `cd backend && mvn -q test -Dtest=PublishProductsBatchUseCaseTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCase.java \
        backend/src/test/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCaseTest.java
git commit -m "feat(publication): publish batch queues rows for the worker instead of dispatching inline"
```

---

## Task 8: Surface retry state in the batch read models

**Files:**
- Modify: `backend/.../publication/application/dto/PublicationBatchDetailDto.java`
- Modify: `backend/.../publication/application/dto/PublicationBatchSummaryDto.java`
- Modify: `backend/.../publication/application/PublicationService.java` (`getBatch`, `summarize`)
- Modify: `backend/.../publication/application/CampaignReportService.java` (verify only)
- Modify: `backend/src/test/.../publication/application/PublicationServiceTest.java`

**Interfaces:**
- Produces:
  - `PublicationBatchDetailDto.Row` gains trailing `int retryCount, Instant nextAttemptAt`.
  - `PublicationBatchSummaryDto` gains `int retrying` immediately before `Instant scheduledAt`.

- [ ] **Step 1: Extend the DTOs**

`PublicationBatchDetailDto.Row`:
```java
    public record Row(
            UUID publicationId,
            UUID productId,
            String productName,
            String thumbnailUrl,
            PublicationPlatform platform,
            PublicationStatus status,
            String externalPermalink,
            String lastErrorCode,
            String lastErrorMessage,
            List<String> imageUrls,
            int retryCount,
            Instant nextAttemptAt
    ) {}
```
`PublicationBatchSummaryDto`:
```java
public record PublicationBatchSummaryDto(
        UUID batchId,
        String campaignLabel,
        Instant createdAt,
        Set<PublicationPlatform> platforms,
        int total,
        int published,
        int failed,
        int scheduled,
        int pending,
        int retrying,
        Instant scheduledAt
) {}
```

- [ ] **Step 2: Map them in `PublicationService`**

`getBatch` (the `dtoRows` lambda, ~line 209): append
`, r.getRetryCount(), r.getNextAttemptAt()` to the `new PublicationBatchDetailDto.Row(...)` call.

`summarize` (~line 175): add `int retrying = 0;`, add a `case RETRY_SCHEDULED -> retrying++;`
to the switch, and pass `retrying` into the `new PublicationBatchSummaryDto(...)` before
`scheduledAt`. Both callers in `listBatches` already delegate through `summarize`, so no change
there.

- [ ] **Step 3: Verify `CampaignReportService` needs no change**

Its status switch (`listCampaigns`, ~line 72) has `default -> { /* counted only in totalPosts */ }`.
`RETRY_SCHEDULED` lands there naturally — a non-terminal state that is neither `published` nor a
`postsWithError`. Add a one-line comment `// RETRY_SCHEDULED, APPROVED, DRAFT... land here` for the
next reader; no behaviour change.

- [ ] **Step 4: Update `PublicationServiceTest`**

The batch-summary / batch-detail tests build expected records — update those constructions for the
new trailing fields (`0, null` for a row that never retried; `0` for `retrying`). Run
`grep -n "new PublicationBatchSummaryDto\|PublicationBatchDetailDto.Row\|\.retrying()\|\.retryCount()" backend/src/test`
and fix each.

- [ ] **Step 5: Run the affected tests**

Run: `cd backend && mvn -q test -Dtest='PublicationServiceTest,CampaignReportServiceTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/ backend/src/test/java/com/pilarestilo/publication/
git commit -m "feat(publication): retryCount + retrying counts in batch read models"
```

---

## Task 9: End-to-end outbox IT

**Files:**
- Create: `backend/src/test/.../publication/infrastructure/web/PublicationDispatchOutboxIT.java`

**Interfaces:**
- Consumes: `POST /api/admin/publications/batch`, `DispatchDuePublicationsUseCase.execute()`,
  `PublicationJpaRepository`. A `@TestConfiguration` `@Primary` stub `PublicationDispatcher`.

- [ ] **Step 1: Write the IT**

```java
package com.pilarestilo.publication.infrastructure.web;

import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.application.usecases.DispatchDuePublicationsUseCase;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.auth.JwtTokenProvider;
import com.pilarestilo.user.domain.enums.UserRole;
// ...product creation helpers per the codebase's existing IT base class...
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.gateway.rate-limit.login-max-requests=500",
        "app.social-publishing.dispatch.backoff-minutes=0,0,0,0,0"
})
class PublicationDispatchOutboxIT {

    @TestConfiguration
    static class StubDispatcherConfig {
        static final AtomicInteger CALLS = new AtomicInteger();
        static volatile boolean failFirstAsTransient = false;

        @Bean @Primary
        PublicationDispatcher stubDispatcher() {
            return (publicationId, idempotencyKey, payload) -> {
                int n = CALLS.incrementAndGet();
                if (failFirstAsTransient && n == 1) {
                    return new PublicationDispatcher.DispatchResult(null, null,
                            PublicationAttemptStatus.FAILED, null, "STUB", "transient", null, true);
                }
                return new PublicationDispatcher.DispatchResult("req", null,
                        PublicationAttemptStatus.SUCCEEDED, "post-" + n, null, null,
                        "https://example.com/p/" + n, true);
            };
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired PublicationJpaRepository publicationRepository;
    @Autowired DispatchDuePublicationsUseCase worker;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private String adminToken() {
        return jwtTokenProvider.generateAccessToken(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "admin@pilarestilo.com", UserRole.ADMIN, List.of(), List.of());
    }

    @Test
    void batch_publish_queues_rows_then_the_worker_publishes_them() throws Exception {
        StubDispatcherConfig.CALLS.set(0);
        StubDispatcherConfig.failFirstAsTransient = false;
        UUID productId = /* create a product via the IT helper */ UUID.randomUUID();

        mockMvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("""
                            {"productIds":["%s"],"platforms":["INSTAGRAM"],
                             "captionTemplate":"{producto}","hashtags":[]}
                            """.formatted(productId)))
                .andExpect(status().isOk());

        List<PublicationEntity> rows = publicationRepository.findAllByOrderByCreatedAtDesc();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(PublicationStatus.APPROVED);
        assertThat(rows.get(0).getNextAttemptAt()).isNotNull();

        worker.execute();

        assertThat(publicationRepository.findById(rows.get(0).getId()).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.PUBLISHED);
    }

    @Test
    void a_transient_failure_is_retried_and_then_succeeds() throws Exception {
        StubDispatcherConfig.CALLS.set(0);
        StubDispatcherConfig.failFirstAsTransient = true;
        UUID productId = /* create a product via the IT helper */ UUID.randomUUID();

        mockMvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("""
                            {"productIds":["%s"],"platforms":["FACEBOOK"],
                             "captionTemplate":"{producto}","hashtags":[]}
                            """.formatted(productId)))
                .andExpect(status().isOk());

        UUID pubId = publicationRepository.findAllByOrderByCreatedAtDesc().get(0).getId();

        worker.execute();   // attempt 1 -> transient failure -> RETRY_SCHEDULED (backoff 0 min)
        assertThat(publicationRepository.findById(pubId).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.RETRY_SCHEDULED);
        assertThat(publicationRepository.findById(pubId).orElseThrow().getRetryCount()).isEqualTo(1);

        worker.execute();   // attempt 2 -> success
        assertThat(publicationRepository.findById(pubId).orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.PUBLISHED);
    }
}
```
Use the repo's existing IT product-creation helper (look at `PublicationControllerIT` /
`ExternalSaleIT` for the pattern — a `ProductRepository` autowire + `Product.create(...)` + save,
or a REST call). `backoff-minutes=0,0,0,0,0` makes the retry immediately due on the next
`worker.execute()`.

- [ ] **Step 2: Run it**

Run: `cd backend && mvn -q test -Dtest=PublicationDispatchOutboxIT`
Expected: PASS. (If Testcontainers postgres flakes — see the spec's note — re-run in isolation.)

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationDispatchOutboxIT.java
git commit -m "test(publication): end-to-end dispatch outbox IT"
```

---

## Task 10: Frontend API types

**Files:**
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Produces: `PublicationBatchDetailRow` gains `retryCount: number; nextAttemptAt: string | null;`.
  `PublicationBatchSummary` gains `retrying: number;`.

- [ ] **Step 1: Edit the interfaces**

`PublicationBatchDetailRow` (line ~1585) — add after `imageUrls`:
```ts
  retryCount: number;
  nextAttemptAt: string | null;
```
`PublicationBatchSummary` (line ~1572) — add after `pending`:
```ts
  retrying: number;
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && ./node_modules/.bin/tsc --noEmit`
Expected: PASS (no consumer reads the new fields yet; adding optional-free fields to a
response-only interface is safe).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(frontend): publication batch types gain retry fields"
```

---

## Task 11: Publicar tab async confirmation + tab switch

**Files:**
- Modify: `frontend/src/islands/admin/PublicarTab.tsx`
- Modify: `frontend/src/islands/admin/PublicacionesPage.tsx`
- Modify: `frontend/src/islands/admin/__tests__/PublicarTab.test.tsx`
- Modify: `frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx`

**Interfaces:**
- Consumes: `publishProductsBatch` response `items[]` (now "creation errors, if any").
- Produces: `PublicarTab` prop `onPublished?: () => void`, called after a successful non-scheduled
  publish. `PublicacionesPage` passes `onPublished={() => setTab('historial')}`.

- [ ] **Step 1: Update the failing tests**

`PublicarTab.test.tsx`:
- The mock `publishProductsBatch` resolved value (line ~44): make every item `success: false`,
  `errorMessage: null` for the happy path.
- `publishes the batch and renders a mixed result` → rename to
  `publishes the batch and shows the queued confirmation`; assert
  `expect(await screen.findByText(/encolado/i)).toBeInTheDocument()` and
  `expect(onPublished).toHaveBeenCalled()` (render with `<PublicarTab onPublished={onPublished} />`,
  `const onPublished = vi.fn()`).
- Add `shows creation errors when an item comes back with a message`:
```tsx
  it('shows creation errors when an item comes back with a message', async () => {
    vi.mocked(publishProductsBatch).mockResolvedValueOnce({
      items: [{ productId: 'p1', platform: 'INSTAGRAM', success: false, publicationId: null, errorMessage: 'clave duplicada', scheduled: false }],
    });
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);
    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));
    expect(await screen.findByText(/clave duplicada/i)).toBeInTheDocument();
  });
```

`PublicacionesPage.test.tsx`:
- Add `publishing a batch switches to the Historial tab`: mock `PublicarTab` is not mocked here
  (it renders real). Simpler: assert the prop is wired — render `<PublicacionesPage />`, and
  since `PublicarTab` is heavy, add a shallow check by triggering the same channel the test file
  already uses for tab assertions (follow the existing "opens the Campañas tab" test's pattern).
  If the existing tests mock the child tabs, extend that mock: `PublicarTab: (props) => <button onClick={props.onPublished}>pub</button>` then click and assert `?tab=historial`.

- [ ] **Step 2: Run the tests, expect failure**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/PublicarTab.test.tsx src/islands/admin/__tests__/PublicacionesPage.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Edit `PublicarTab.tsx`**

- Props type: add `onPublished?: () => void;` to `PublicarTabProps`; destructure it in the
  component signature (line ~95).
- Remove `const [publishResults, setPublishResults] = useState<PublishProductsBatchItemResult[] | null>(null);`
  (line 113) and the `PublishProductsBatchItemResult` import if unused.
- Add `const [queueErrors, setQueueErrors] = useState<string[]>([]);` and
  `const [queued, setQueued] = useState(false);`.
- In `handleSubmit`, the non-editing branch after `const response = await publishProductsBatch(...)`:
```ts
      if (scheduledAt) {
        setScheduledConfirmation(
          `Programada para ${instantToSantiagoLabel(scheduledAt)}. La vas a ver en Historial.`,
        );
      } else {
        const errs = response.items
          .map((i) => i.errorMessage)
          .filter((m): m is string => Boolean(m));
        if (errs.length > 0) {
          setQueueErrors(errs);
          setQueued(false);
        } else {
          setQueueErrors([]);
          setQueued(true);
          onPublished?.();
        }
      }
```
- Replace the `{publishResults && (<section>Resultado…</section>)}` block (lines ~702-720) with:
```tsx
      {queued && (
        <p className="text-sm text-pe-positive-ink">
          Encolado. Los posts salen en unos segundos, segui el estado en Historial.
        </p>
      )}
      {queueErrors.length > 0 && (
        <section>
          <h2 className="font-sans text-sm text-pe-muted mb-2">No se pudieron encolar</h2>
          <ul className="flex flex-col gap-1">
            {queueErrors.map((msg, i) => (
              <li key={i} className="text-sm flex items-center gap-2">
                <span aria-hidden="true">✗</span> <span>{msg}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
```
- Reset `setQueued(false); setQueueErrors([]);` at the top of `handleSubmit` alongside the other
  state resets.

- [ ] **Step 4: Wire `PublicacionesPage.tsx`**

Line ~79, the `publicar` tab render:
```tsx
        <PublicarTab
          preload={preload}
          onPreloadConsumed={() => setPreload(undefined)}
          editingBatchId={editingBatchId ?? undefined}
          onEditCancelled={clearEditing}
          onPublished={() => setTab('historial')}
        />
```

- [ ] **Step 5: Run the tests, expect PASS**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/PublicarTab.test.tsx src/islands/admin/__tests__/PublicacionesPage.test.tsx`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/islands/admin/PublicarTab.tsx frontend/src/islands/admin/PublicacionesPage.tsx frontend/src/islands/admin/__tests__/
git commit -m "feat(frontend): Publicar tab shows queued confirmation and jumps to Historial"
```

---

## Task 12: Historial RETRY_SCHEDULED pill + counts

**Files:**
- Modify: `frontend/src/islands/admin/HistorialTab.tsx`
- Modify: `frontend/src/islands/admin/__tests__/HistorialTab.test.tsx`

**Interfaces:**
- Consumes: `PublicationBatchDetailRow.retryCount` / `.nextAttemptAt`,
  `PublicationBatchSummary.retrying` (Task 10).

- [ ] **Step 1: Write the failing test**

In `HistorialTab.test.tsx`, add (follow the file's existing mock/render helpers):
```tsx
  it('shows a Reintentando pill with the attempt number and next time', async () => {
    // mock getPublicationBatchDetail to return one row:
    // { ...row, status: 'RETRY_SCHEDULED', retryCount: 2, nextAttemptAt: '2026-09-06T14:35:00Z' }
    // and getPublicationBatches to include { ...summary, retrying: 1 }
    // expand the batch, then:
    expect(await screen.findByText(/reintentando/i)).toBeInTheDocument();
    expect(screen.getByText(/intento 2/i)).toBeInTheDocument();
    expect(screen.getByText(/1 reintentando/i)).toBeInTheDocument();
  });
```

- [ ] **Step 2: Run it, expect failure**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/HistorialTab.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Extend `StatusPill`**

`HistorialTab.tsx`, before the final fallback `return` (line ~75), add:
```tsx
  if (status === 'RETRY_SCHEDULED') {
    return (
      <span className="inline-flex items-center gap-1 text-[0.72rem] px-1.5 py-0.5 bg-pe-warning-surface text-pe-warning-ink">
        <Loader2 size={12} /> Reintentando
      </span>
    );
  }
```
`StatusPill` currently takes only `status`. Extend its props to
`{ status: string; retryCount?: number; nextAttemptAt?: string | null }` and, in the
`RETRY_SCHEDULED` branch, append when present:
```tsx
        {typeof retryCount === 'number' && retryCount > 0 ? ` · intento ${retryCount}` : ''}
        {nextAttemptAt ? ` · próximo ${instantToSantiagoLabel(nextAttemptAt)}` : ''}
```
Update the call site (row render, ~line 361):
`<StatusPill status={r.status} retryCount={r.retryCount} nextAttemptAt={r.nextAttemptAt} />`.

- [ ] **Step 4: Show the `retrying` count in the batch summary line**

Where the summary renders `{b.scheduled > 0 && <span ...> · {b.scheduled} programados</span>}`
(~line 245), add alongside:
```tsx
{b.retrying > 0 && <span className="text-pe-warning-ink"> · {b.retrying} reintentando</span>}
```

- [ ] **Step 5: Retry button also for RETRY_SCHEDULED**

Where the row renders the retry control (`{r.status === 'FAILED' && (...)}`, ~line 363), change the
condition to `{(r.status === 'FAILED' || r.status === 'RETRY_SCHEDULED') && (...)}` and make the
button label `{r.status === 'RETRY_SCHEDULED' ? 'Reintentar ahora' : 'Reintentar'}`.

- [ ] **Step 6: Run the test, expect PASS**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/HistorialTab.test.tsx`
Expected: PASS.

- [ ] **Step 7: Typecheck + build**

Run: `cd frontend && ./node_modules/.bin/tsc --noEmit && npm run build`
Expected: both PASS.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/islands/admin/HistorialTab.tsx frontend/src/islands/admin/__tests__/HistorialTab.test.tsx
git commit -m "feat(frontend): Historial shows the Reintentando state and count"
```

---

## Task 13: Full verification + finish

**Files:** none (verification + memory)

- [ ] **Step 1: Backend full suite**

Run: `cd backend && mvn -q test`
Expected: all green. If Testcontainers postgres flakes on an unrelated IT, re-run that class in
isolation (documented in the spec).

- [ ] **Step 2: Backend integration suite**

Run: `cd backend && mvn -q verify -DskipUnitTests=false`
(or `mvn -q failsafe:integration-test failsafe:verify` per the repo's convention). Confirm
`ReadOnlyMappingIT` and `PublicationDispatchOutboxIT` pass.

- [ ] **Step 3: Frontend full suite**

Run: `cd frontend && npx vitest run && ./node_modules/.bin/tsc --noEmit && npm run build`
Expected: all green.

- [ ] **Step 4: Local Docker smoke (per the spec's rollout step 2)**

Bring up the stack, publish a real 1-product batch to the sandbox (or with `PAYMENT`/dispatch
stubbed), confirm in `/admin/publicaciones` → Historial that the row goes
`APPROVED → PUBLISHING → PUBLISHED` within ~40s and the pill updates without a page reload.

- [ ] **Step 5: Update the work-queue memory**

Append to `C:\Users\chcal\.claude\projects\e--dev-PilarEstilo\memory\pending-work-queue.md`
under the publication section: outbox DONE on `develop` (commit range), what shipped, the
`APP_SOCIAL_PUBLISHING_SCHEDULE_* → DISPATCH_*` env rename the VPS needs, and that
`master` merge waits on the owner.

- [ ] **Step 6: Hand off**

Announce: "I'm using the finishing-a-development-branch skill to complete this work." Then follow
that skill — tests are already green from Steps 1-3; present the merge options; `master` only on
the owner's explicit word (the deploy note about the env-var rename goes in that message).

---

## Self-Review

**Spec coverage:**
- Status model / `RETRY_SCHEDULED` / `next_attempt_at` → Task 1. ✓
- `next_attempt_at` set on create/approve/retry/worker-fail → Tasks 5. ✓
- Partial index + backfill → Task 1. ✓
- Dispatch worker (rename, 20s, broadened query, stuck recovery, batch size) → Task 6. ✓
- `dispatchInternal` retry/permanent split + events → Task 5. ✓
- `dispatchFromWorker` trigger-type → Tasks 5 + 6. ✓
- Error classification + `retryable` → Task 4. ✓
- Config block + metadata + env + compose + deploy note → Task 2 (+ rename cleanup in Task 6 is
  folded: Task 2 already removes `schedule.*` everywhere and points the still-old-named classes at
  the new keys; Task 6 renames the classes). ✓
- "Publicar ahora" async → Task 7. ✓
- Admin UX: Publicar confirmation + tab switch → Task 11; Historial pill + counts + retry button
  → Task 12. ✓
- `CampaignReportService` → Task 8 Step 3 (verify, no change). ✓
- Batch DTOs `retryCount` / `nextAttemptAt` / `retrying` → Tasks 8 + 10. ✓
- Tests: backoff, classifier, retry split, worker, outbox IT, 3 frontend specs → Tasks 3, 4, 5,
  6, 9, 11, 12. ✓
- `notification-service` RO check → Task 1 Step 4 + Task 13 Step 2. ✓
- Rollout / deploy note / risks → Task 13. ✓

**Deviations from the spec:** `DispatchDuePublicationsUseCase.execute()` returns `int` (rows
handled) rather than the spec's `DispatchPassResult` record — the richer breakdown had no consumer
beyond a log line. Spec updated to match.

**Placeholder scan:** the only "fill this in" is the IT product-creation helper in Task 9 Step 1,
which points at the concrete existing pattern (`PublicationControllerIT` / `ExternalSaleIT`) to
copy. Acceptable — the helper is repo-specific boilerplate, not new logic.

**Type consistency:** `DispatchResult` is 8-arg everywhere from Task 4 on. `PublicationService`
ctor is 7-arg from Task 5 on (Task 9's IT autowires it, no manual construction). `Row` is 12-arg
and `PublicationBatchSummaryDto` 11-arg from Task 8 on. Frontend `retryCount` / `nextAttemptAt` /
`retrying` names match backend. `dispatchFromWorker` / `markDispatchInterrupted` /
`markScheduleWindowMissed` used consistently in Tasks 5 and 6.
