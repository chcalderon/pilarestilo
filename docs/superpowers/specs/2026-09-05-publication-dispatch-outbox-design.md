# Publication Dispatch Outbox — Design

**Status:** approved (brainstorm 2026-09-05)
**Module:** `backend/.../publication` (Spring Boot 4 hexagonal monolith)
**Precedes:** MercadoLibre increments I/J. This is the last piece of the `publication` module.

## Problem

Today "Publicar ahora" (`POST /api/admin/publications/batch` → `PublishProductsBatchUseCase`)
dispatches to the Meta Graph API **synchronously inside the HTTP request**. For an Instagram
carousel of several products this can run 30s or more (each child container is created and polled
to `FINISHED`, then the parent, then `media_publish`). If Meta returns a transient error (HTTP 429,
a network blip, `code 9007` "media not ready"), the row lands in `FAILED` and the only recovery is
the admin noticing and clicking "Reintentar".

Scheduled batches already dispatch in the background (`PublishDueScheduledPublicationsScheduler`,
every minute → `PublishDueScheduledPublicationsUseCase`), so the codebase already has one
background-dispatch path. This design generalises it into the single dispatch path for every
publication and adds automatic retry with backoff.

## Goal

1. **Async dispatch.** The batch endpoint creates rows and returns immediately; a background worker
   posts to Meta. "Publicar ahora" behaves like "Programar para ahora".
2. **Automatic retry.** Transient Meta failures retry themselves on a fixed backoff schedule, up to
   a cap, then land in `FAILED` (terminal, as today).
3. **One dispatch path.** Immediate, scheduled, and retried publications all flow through the same
   worker and the same `PublicationService.dispatchInternal`.

Non-goals: distributed locking (single prod instance), a separate `publication_outbox` table (the
`publications` table already carries `status` / `retry_count` / `last_error_*` / `attempts[]` /
unique `idempotency_key` — it *is* the queue), changing the Meta adapters' publish logic, Reels.

## Global constraints (from the codebase)

- Java 25, Spring Boot 4.0.7. Jackson 3 (`tools.jackson.databind`). Flyway; current highest
  migration **V103** → this adds **V104**. Never edit an applied migration.
- Domain models carry no framework annotations; JPA entities are separate. Use cases take ports.
- `PublicationService` methods are `@Transactional` on a dedicated bean; the batch/worker
  orchestrators are **deliberately not `@Transactional`** (looping over `@Transactional` calls —
  one outer tx would go rollback-only on the first failure and lose every other row's result).
- Scheduled components: `@Scheduled(cron = "${...}")` on an `infrastructure/jobs/` `@Component`
  delegating to a use case; scheduled use cases take an injected `java.time.Clock`
  (package-private ctor for tests, `@Autowired` ctor passes `Clock.systemUTC()`).
- Config keys: `application.yml` `app.social-publishing.*` with `${APP_SOCIAL_PUBLISHING_*}` env +
  a matching entry in `META-INF/additional-spring-configuration-metadata.json` + `infra/.env.example`
  + the backend `environment:` block in `infra/docker-compose.yml`.
- An IT class that logs in per-test sits near the per-IP login rate limit (default 12). New IT
  tests generate the token with `jwtTokenProvider.generateAccessToken(...)`, not `POST /auth/login`.
- Frontend: Astro 5 + React islands, Vitest + happy-dom. `./node_modules/.bin/tsc --noEmit`,
  `npx vitest run <path>`.

## Status model

New enum value: **`PublicationStatus.RETRY_SCHEDULED`** — not done, not terminal, waiting for its
next attempt. Placed after `PUBLISHING`, before `PUBLISHED`.

```
Publicar ahora  → APPROVED     (next_attempt_at = now)
Programar       → SCHEDULED    (next_attempt_at = scheduled_at)
approve()       → APPROVED     (next_attempt_at = now)
                     │
        worker picks up when status ∈ {APPROVED, SCHEDULED, RETRY_SCHEDULED}
        and next_attempt_at ≤ now
                     ↓
                 PUBLISHING  ──success──→  PUBLISHED ✓
                     │
       transient failure, retry_count < N  ──→  RETRY_SCHEDULED
                     │                          (retry_count++, next_attempt_at = now + backoff[retry_count-1])
                     │                                    │
                     │                          next_attempt_at ≤ now → back to worker
                     │
       transient failure, retry_count = N  ──→  FAILED ✗ (terminal)
       permanent failure (any attempt)     ──→  FAILED ✗ (terminal, immediate)
```

`SCHEDULED` rows more than `max-lateness-minutes` overdue are still failed as
`SCHEDULE_WINDOW_MISSED` (existing behaviour, kept).

### `next_attempt_at`

The single "ready to be worked" signal. `V104` adds `publications.next_attempt_at TIMESTAMPTZ`
(nullable). Set it wherever a row becomes dispatch-ready:

| Transition | `status` | `next_attempt_at` |
|---|---|---|
| `create()`, no approval, no schedule | `APPROVED` | `now` |
| `create()`, scheduled | `SCHEDULED` | `command.scheduledAt()` |
| `approve()` | `APPROVED` | `now` |
| manual `retry(id)` | `RETRY_SCHEDULED` | `now` (jump the queue) |
| worker transient failure | `RETRY_SCHEDULED` | `now + backoff[retry_count - 1]` |
| worker success / permanent failure / terminal `FAILED` | `PUBLISHED` / `FAILED` | `null` |

Partial index for the worker query:
`CREATE INDEX idx_publications_dispatch_due ON publications (next_attempt_at)
 WHERE status IN ('APPROVED','SCHEDULED','RETRY_SCHEDULED');`

Backfill: none needed. Existing `PUBLISHED` / `FAILED` / `DRAFT` etc. rows keep `next_attempt_at`
NULL and are never picked up. Any pre-existing `APPROVED` or `SCHEDULED` rows (there should be
none on prod — H-2/H-3 batches all published) would be swept in by the worker on first tick;
`V104` sets `next_attempt_at = COALESCE(scheduled_at, now())` for rows currently in
`('APPROVED','SCHEDULED')` so that is deterministic rather than NULL-and-ignored.

## The dispatch worker

Rename `PublishDueScheduledPublicationsUseCase` → **`DispatchDuePublicationsUseCase`** and
`PublishDueScheduledPublicationsScheduler` → **`DispatchDuePublicationsScheduler`** (the job is no
longer scheduling-specific). Keep them in `application/usecases/` and `infrastructure/jobs/`.

`DispatchDuePublicationsScheduler`:
```java
@Scheduled(cron = "${app.social-publishing.dispatch.cron:*/20 * * * * *}")
public void run() { useCase.execute(); }
```

`DispatchDuePublicationsUseCase.execute()` (not `@Transactional`, injected `Clock`):

1. `Instant now = Instant.now(clock);`
2. **Recover stuck rows.** `publicationRepository.findByStatusAndUpdatedAtLessThan(PUBLISHING,
   now.minus(stuckPublishingMinutes))` → for each, call
   `publicationService.markDispatchInterrupted(id)` (new: → `FAILED`, code
   `DISPATCH_INTERRUPTED`, message "El servidor se reinició mientras publicaba esta pieza. Revisá
   Instagram/Facebook: si ya salió marcala como lista, si no reintentá."). **Not auto-retried** —
   the post may have gone live on Meta before the crash, and re-dispatch would double-post (Meta's
   Graph create-container call is not idempotent on our key).
3. **Dispatch due rows.** `publicationRepository.findDueForDispatch(now, PageRequest.of(0,
   batchSize))` (new query, see below), already ordered by `next_attempt_at asc`. For each:
   - If `status == SCHEDULED` and `scheduledAt` is before `now - maxLatenessMinutes` →
     `publicationService.markScheduleWindowMissed(id)` (existing).
   - Else → `publicationService.dispatchFromWorker(id)` (new; drives `dispatchInternal` with the
     right `PublicationAttemptTriggerType` — `RETRY` when the row was `RETRY_SCHEDULED`, else
     `SCHEDULED`). Today `PublishDueScheduledPublicationsUseCase` calls `dispatch(id, null)` which
     hardcodes `MANUAL`; `dispatchFromWorker` fixes that mislabel too. `dispatch(id, actorUserId)`
     stays `MANUAL` for the `POST /publications/{id}/dispatch` endpoint.
   - `catch (RuntimeException)` → log at WARN, continue (one row must not stop the pass).
4. Return a small `DispatchPassResult(int dispatched, int retriesScheduled, int failed, int
   recovered)` for the log line.

New repository queries on `PublicationJpaRepository`:
```java
List<PublicationEntity> findByStatusAndUpdatedAtLessThan(PublicationStatus status, Instant cutoff);

@Query("""
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

The existing `findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc` becomes unused and is
deleted.

### Concurrency

Single prod instance + a 20s tick + low volume. The only concurrent dispatchers are the worker and
a manual `POST /publications/{id}/retry` or `POST /publications/{id}/dispatch`. `dispatchInternal`
guards on `status ∈ {APPROVED, SCHEDULED, RETRY_SCHEDULED}` and flips to `PUBLISHING` inside its own
transaction; whichever call commits `PUBLISHING` first wins, the other throws the guard
(`DomainException`) and its caller logs-and-skips. No `SELECT ... FOR UPDATE SKIP LOCKED` needed;
noted here so a future second instance is a known follow-up.

## `PublicationService.dispatchInternal` changes

Currently the `else` branch (not `SUCCEEDED`) always sets `FAILED`. Split it:

```java
// after the attempt is recorded, on a non-SUCCEEDED result:
boolean permanent = !result.retryable();
int attemptsSoFar = entity.getRetryCount();               // 0 on the first ever attempt
if (permanent || attemptsSoFar >= backoff.size()) {
    entity.setStatus(PublicationStatus.FAILED);
    entity.setNextAttemptAt(null);
    entity.setLastErrorCode(result.errorCode());
    entity.setLastErrorMessage(result.errorMessage());
    PublicationEntity saved = publicationRepository.save(entity);
    eventPublisher.publish(new PublicationDispatchFailed(saved.getId(), attempt.getAttemptNumber(), result.errorCode()));
    return toDto(saved);
}
entity.setRetryCount(attemptsSoFar + 1);
entity.setStatus(PublicationStatus.RETRY_SCHEDULED);
entity.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(backoff.get(attemptsSoFar))));
entity.setLastErrorCode(result.errorCode());
entity.setLastErrorMessage(result.errorMessage());
PublicationEntity saved = publicationRepository.save(entity);
eventPublisher.publish(new PublicationDispatchScheduledForRetry(saved.getId(), saved.getRetryCount(), saved.getNextAttemptAt()));
return toDto(saved);
```

- `backoff` is `List<Integer>` minutes, injected from config. `backoff.size()` is the retry cap
  (initial attempt + `backoff.size()` retries; default 5 → 6 total attempts).
- The success branch also sets `entity.setNextAttemptAt(null)`.
- `dispatchInternal`'s status guard gains `RETRY_SCHEDULED`.
- New domain event `PublicationDispatchScheduledForRetry(UUID publicationId, int retryCount,
  Instant nextAttemptAt)` in `domain/events/` (mirrors the existing `PublicationDispatch*` events).

`PublicationService.retry(id)` (manual): allow it from `FAILED` **or** `RETRY_SCHEDULED` (force an
early attempt). Set `status = RETRY_SCHEDULED`, `nextAttemptAt = now`, clear `lastError*`, and
**reset `retryCount = 0`** — a manual retry is the admin taking responsibility, so it earns a fresh
automatic-retry budget rather than inheriting an exhausted one. Return **without dispatching
inline**; the worker picks it up within 20s. The true attempt count is preserved in `attempts[]`
(each dispatch appends one, `attemptNumber = attempts.size() + 1`), so audit history is intact even
though `retryCount` is a resettable "automatic retries since last manual touch" counter.

`PublicationService.dispatch(id)` (the `POST /publications/{id}/dispatch` endpoint): unchanged —
stays a synchronous single-shot for a "publish this one right now" admin action from a detail view.
It calls `dispatchInternal` directly.

New method `markDispatchInterrupted(UUID id)`: guard `status == PUBLISHING`, set `FAILED` +
`DISPATCH_INTERRUPTED` + `nextAttemptAt = null`, publish `PublicationDispatchFailed`.

New method `enqueue`-style helper is **not** added — `create()` and `approve()` set
`nextAttemptAt` inline where they already set `status`.

## Error classification (transient vs permanent)

`PublicationDispatcher.DispatchResult` gains a field: **`boolean retryable`** (7 fields → 8,
positional record). Construction sites to update: `InstagramGraphPublisherAdapter` (success +
`failed()`), `FacebookPagePublisherAdapter` (success + its `failed`/`parse` paths),
`MetaDirectPublicationDispatcher` (only re-wraps the payload, doesn't build a result — no change),
`PublicationService.dispatchInternal` (the `catch (RuntimeException)` fallback), and every test
that builds a `DispatchResult`. Success results: `retryable` is irrelevant, set `true`.

Classification lives in the Meta infra layer (it knows Graph error shapes). Add
`MetaErrorClassifier` (`infrastructure/meta/`, package-private static helper):

```java
static boolean isRetryable(Throwable ex) {
    if (ex instanceof org.springframework.web.client.HttpServerErrorException) return true;   // 5xx
    if (ex instanceof org.springframework.web.client.ResourceAccessException) return true;    // connect/read timeout
    if (ex instanceof org.springframework.web.client.HttpClientErrorException http) {
        int code = http.getStatusCode().value();
        if (code == 429) return true;                                                          // rate limit
        Integer metaCode = extractMetaErrorCode(http.getResponseBodyAsString());               // error.code in body
        if (metaCode != null && RATE_LIMIT_CODES.contains(metaCode)) return true;              // 4, 17, 32, 613
        return false;                                                                          // 190 token, 10/200-299 perms, 100 bad param → permanent
    }
    // IllegalStateException thrown by the adapter itself:
    String msg = ex.getMessage() == null ? "" : ex.getMessage();
    if (msg.contains("was not ready after")) return true;                                      // container still processing
    if (msg.contains("error before it could be published")
        || msg.contains("expired before it could be published")) return false;                 // media rejected by Meta
    return true;   // unknown adapter/parse error → give it the retry budget rather than fail hard
}
```
- `RATE_LIMIT_CODES = Set.of(4, 17, 32, 613)` (Meta app/user/page rate limit families).
- `extractMetaErrorCode`: parse `{"error":{"code":N,...}}` from the body with the shared
  `ObjectMapper`; return `null` if absent/unparseable.
- **Config-missing errors** ("Instagram credentials are not configured",
  "public-media-base-url is not configured", "Cannot dispatch publication without a media URL"):
  these are thrown as `DomainException` / returned by `failed(...)` *before* any HTTP call.
  Treat as **permanent** (`retryable = false`) — nothing changes by retrying. The adapters set
  `retryable = false` on those specific `failed(...)` returns; `dispatchInternal`'s
  `catch (RuntimeException)` treats a `DomainException` as permanent, any other `RuntimeException`
  as transient.

Both adapters' `publish(...)` wrap their body in `try { ... } catch (RuntimeException ex) { return
failed(ex.getMessage(), MetaErrorClassifier.isRetryable(ex)); }`. `failed` gains a `boolean
retryable` parameter. The two "credentials not configured" early returns pass `false`.

## Config

`application.yml`, replacing the `schedule:` block under `app.social-publishing:` with `dispatch:`:

```yaml
    dispatch:
      cron: ${APP_SOCIAL_PUBLISHING_DISPATCH_CRON:*/20 * * * * *}
      max-lateness-minutes: ${APP_SOCIAL_PUBLISHING_DISPATCH_MAX_LATENESS_MINUTES:360}
      backoff-minutes: ${APP_SOCIAL_PUBLISHING_DISPATCH_BACKOFF_MINUTES:2,10,30,120,360}
      batch-size: ${APP_SOCIAL_PUBLISHING_DISPATCH_BATCH_SIZE:25}
      stuck-publishing-minutes: ${APP_SOCIAL_PUBLISHING_DISPATCH_STUCK_PUBLISHING_MINUTES:15}
```

- `backoff-minutes` binds to `List<Integer>` (Spring splits on comma).
- `additional-spring-configuration-metadata.json`: replace the two `app.social-publishing.schedule.*`
  entries with the five `app.social-publishing.dispatch.*` entries (descriptions).
- `infra/.env.example` + `infra/docker-compose.yml` backend `environment:`: replace
  `APP_SOCIAL_PUBLISHING_SCHEDULE_CRON` / `_MAX_LATENESS_MINUTES` with the five
  `APP_SOCIAL_PUBLISHING_DISPATCH_*` vars.
- **Deploy note:** the VPS `infra/.env` may carry `APP_SOCIAL_PUBLISHING_SCHEDULE_CRON` /
  `_MAX_LATENESS_MINUTES` (added in H-3). They become dead. If the owner overrode them, re-add
  the value under the new `APP_SOCIAL_PUBLISHING_DISPATCH_*` name; otherwise just delete the old
  two. Defaults are sensible, so a missed rename only reverts to defaults, it does not break boot.

## Admin UX

### Publicar tab (`PublicarTab.tsx`)

`PublishProductsBatchUseCase.publishOne` still catches `DomainException` from
`publicationService.create()` — a product-not-found or a bad create is still reported per item with
`success=false, errorMessage=<reason>`. What is gone is the *dispatch* outcome: every row that
was created returns `success=false, errorMessage=null` (it is queued, not published-or-failed).
So `response.items` is now "creation errors, if any" rather than "publish results". In the
component:

- On a non-scheduled publish: if any item has a non-null `errorMessage`, show those lines under a
  heading *"No se pudieron encolar"*. Otherwise (the normal case) show the confirmation the
  scheduled path uses in spirit: *"Encolado. Los posts salen en unos segundos, seguí el estado en
  Historial."* Then switch to the Historial tab — wire a new `onPublished` prop from
  `PublicacionesPage` that calls `setTab('historial')` (mirrors the existing `onGoToPublish` /
  `onEditCancelled` channels).
- The `{publishResults && <section>Resultado…</section>}` block and the `publishResults` /
  `setPublishResults` state are replaced by the above (a small `queueError` string list +
  `queued` boolean, or just render straight off `response.items`).
- Keep the "Programar" confirmation exactly as is.

### Historial tab (`HistorialTab.tsx`)

- `StatusPill` gains a `RETRY_SCHEDULED` case:
  *"↻ Reintentando"* on `bg-pe-warning-surface text-pe-warning-ink`. When the row carries
  `retryCount` and `nextAttemptAt`, append `· intento {retryCount} · próximo {hora}` using
  `instantToSantiagoLabel`.
- `PublicationBatchDetailRow` (api.ts + `PublicationBatchDetailDto.Row`) gains `retryCount: number`
  and `nextAttemptAt: string | null`. `PublicationService.getBatch` already maps rows — add
  `r.getRetryCount()` and `r.getNextAttemptAt()`.
- Batch summary: `PublicationBatchSummaryDto` / `summarize()` — `RETRY_SCHEDULED` currently falls
  into `default -> pending++`. Add an explicit `retrying` count (new field on the DTO, new
  `int retrying` param), surface it in `HistorialTab` next to `scheduled`
  (*"· {retrying} reintentando"*). `PublicationBatchSummary` in api.ts gains `retrying: number`.
- The "Reintentar" / "Reintentar fallidos" buttons stay. Their behaviour is unchanged from the
  admin's view (they now enqueue instead of dispatching inline, but the row-status refresh already
  polls `getPublicationBatchDetail`).
- The `r.status === 'FAILED'` retry button should also show for `r.status === 'RETRY_SCHEDULED'`
  (force an early attempt) — relabel to "Reintentar ahora" for that state.

### `CampaignReportService`

`RETRY_SCHEDULED` is a non-terminal state — treat it like `SCHEDULED`/pending in the campaign
rollup (`postsWithError` counts only `FAILED`; `published` counts only `PUBLISHED`). One line in
the status switch.

## Files

**Backend — create**
- `db/migration/V104__publication_dispatch_queue.sql`
- `publication/domain/events/PublicationDispatchScheduledForRetry.java`
- `publication/infrastructure/meta/MetaErrorClassifier.java`

**Backend — modify**
- `publication/domain/enums/PublicationStatus.java` — add `RETRY_SCHEDULED`
- `publication/application/ports/PublicationDispatcher.java` — `DispatchResult` + `boolean retryable`
- `publication/infrastructure/meta/InstagramGraphPublisherAdapter.java`,
  `FacebookPagePublisherAdapter.java` — `failed(msg, retryable)`, classify in the `catch`
- `publication/application/PublicationService.java` — `dispatchInternal` retry/permanent split +
  `RETRY_SCHEDULED` in its status guard; `create()` / `approve()` set `nextAttemptAt`; `retry()`
  async + `retryCount` reset; new `dispatchFromWorker(id)` (trigger-type aware);
  `markDispatchInterrupted(id)`; `getBatch()` row mapping; `summarize()` retrying count
- `publication/application/usecases/PublishDueScheduledPublicationsUseCase.java` →
  rename to `DispatchDuePublicationsUseCase`, broaden query, stuck-row recovery
- `publication/infrastructure/jobs/PublishDueScheduledPublicationsScheduler.java` → rename to
  `DispatchDuePublicationsScheduler`, cron key
- `publication/application/usecases/PublishProductsBatchUseCase.java` — `publishOne` stops calling
  `publicationService.dispatch`; every non-scheduled row is `APPROVED` and left for the worker
- `publication/infrastructure/persistence/entities/PublicationEntity.java` — `nextAttemptAt`
- `publication/infrastructure/persistence/repositories/PublicationJpaRepository.java` — new
  queries, drop the old scheduled-only one
- `publication/application/dto/PublicationBatchDetailDto.java` — `Row` + `retryCount`, `nextAttemptAt`
- `publication/application/dto/PublicationBatchSummaryDto.java` — `retrying`
- `publication/application/CampaignReportService.java` — `RETRY_SCHEDULED` in the status switch
- `resources/application.yml`, `resources/META-INF/additional-spring-configuration-metadata.json`
- `infra/.env.example`, `infra/docker-compose.yml`

**Backend — notification-service RO check:** `publications` is not one of notification-service's
read-only mapped tables (it maps `orders`, `order_items`, `users`, `payments`, `sales_documents`,
`return_requests`, `system_settings`). `V104` touches only `publications` → **no `*RoEntity`
change, `ReadOnlyMappingIT` unaffected.** Confirm during implementation.

**Frontend — modify**
- `src/lib/api.ts` — `PublicationBatchDetailRow` + `retryCount`/`nextAttemptAt`,
  `PublicationBatchSummary` + `retrying`
- `src/islands/admin/PublicarTab.tsx` — drop `publishResults`, "encolado" confirmation, tab switch
- `src/islands/admin/HistorialTab.tsx` — `StatusPill` `RETRY_SCHEDULED`, summary `retrying`,
  retry button for `RETRY_SCHEDULED`
- `src/islands/admin/PublicacionesPage.tsx` — `onPublished` → `setTab('historial')` channel

## Testing

**Backend unit — `DispatchDuePublicationsUseCaseTest`** (fake `Clock`, mock `PublicationService`,
mock repo):
- due `APPROVED` row → `publicationService.dispatch` called
- `SCHEDULED` row past `maxLatenessMinutes` → `markScheduleWindowMissed`, not `dispatch`
- `PUBLISHING` row older than `stuck-publishing-minutes` → `markDispatchInterrupted`
- a `dispatch` that throws → other due rows still processed
- `RETRY_SCHEDULED` row with `nextAttemptAt` in the future → not selected (query-level; covered in
  the repo IT)

**Backend unit — `PublicationServiceDispatchRetryTest`** (mock `PublicationDispatcher`):
- transient failure (`retryable=true`), `retryCount 0` → `RETRY_SCHEDULED`, `retryCount 1`,
  `nextAttemptAt == now + 2min`, event `PublicationDispatchScheduledForRetry`
- transient failure at `retryCount == backoff.size()` → `FAILED`, `nextAttemptAt null`, event
  `PublicationDispatchFailed`
- permanent failure (`retryable=false`) at `retryCount 0` → `FAILED` immediately
- success → `PUBLISHED`, `nextAttemptAt null`
- backoff index table: `retryCount` 0..4 → minutes 2,10,30,120,360
- `retry(id)` on a `RETRY_SCHEDULED` row → `status RETRY_SCHEDULED`, `nextAttemptAt = now`,
  `retryCount` reset to 0, `lastError*` cleared, no inline dispatch call
- `retry(id)` on a `FAILED` row with `retryCount 5` → same (fresh budget), not immediate re-FAIL
- `dispatchFromWorker` on a `RETRY_SCHEDULED` row records `attempt.triggerType == RETRY`; on an
  `APPROVED` row, `== SCHEDULED`

**Backend unit — `MetaErrorClassifierTest`:**
- `HttpClientErrorException` 429 → retryable
- `HttpClientErrorException` 400 body `{"error":{"code":190}}` → not retryable
- `HttpClientErrorException` 400 body `{"error":{"code":4}}` → retryable
- `HttpServerErrorException` 500 → retryable
- `ResourceAccessException` → retryable
- `IllegalStateException("...was not ready after 10 checks")` → retryable
- `IllegalStateException("...error before it could be published")` → not retryable
- `DomainException("...credentials are not configured")` → not retryable (via `dispatchInternal`
  fallback rule)

**Backend IT — `PublicationDispatchOutboxIT`** (Testcontainers; `@TestPropertySource`
`app.gateway.rate-limit.login-max-requests=500`; token via `jwtTokenProvider.generateAccessToken`):
- `POST /api/admin/publications/batch` (no `scheduledAt`) → 200, rows are `APPROVED` with
  `nextAttemptAt ≈ now`, response items all `success=false`
- with a stub `PublicationDispatcher` bean returning `SUCCEEDED` → run
  `DispatchDuePublicationsUseCase.execute()` → rows `PUBLISHED`
- stub returning a transient failure once then `SUCCEEDED` → after two `execute()` passes (advance
  the injected clock past the backoff) the row is `PUBLISHED` with `retryCount 1`
- `V104` applied: `next_attempt_at` column + partial index exist (a light assertion or rely on
  `ddl-auto: validate` + the entity mapping)

**Backend — `MetaGraphPublishingIT` / existing adapter tests:** update every `DispatchResult`
constructor call for the new `retryable` arg.

**Frontend — `HistorialTab.test.tsx`:** a batch detail row with `status: 'RETRY_SCHEDULED'`,
`retryCount: 2`, `nextAttemptAt` → pill reads "Reintentando" + "intento 2"; summary with
`retrying: 1` shows "1 reintentando".

**Frontend — `PublicarTab.test.tsx`:** publishing a non-scheduled batch shows the "Encolado"
confirmation, not a per-item results list; the `getProducts`/`publishProductsBatch` mocks already
exist. Remove/replace the assertion that currently checks for the results list.

**Frontend — `PublicacionesPage.test.tsx`:** publishing switches to the `historial` tab
(`?tab=historial`).

## Rollout

1. `develop`, full local verify: `mvn -q -pl backend test` for the new unit + IT classes,
   `./node_modules/.bin/tsc --noEmit`, `npx vitest run` the three specs, `npm run build`.
2. Local Docker smoke: publish a real batch to the **sandbox** IG/FB (or with the stub dispatcher),
   confirm the row moves `APPROVED → PUBLISHING → PUBLISHED` on the worker within ~40s and the
   Historial pill updates.
3. Master merge on the owner's word. `V104` runs on deploy (additive column + index, safe).
4. **Prod deploy note:** rename the two `APP_SOCIAL_PUBLISHING_SCHEDULE_*` env vars in the VPS
   `infra/.env` to `APP_SOCIAL_PUBLISHING_DISPATCH_*` (or delete them — defaults are fine).
5. Prod smoke: one real 1-product batch, watch it publish via the worker; leave it up as real
   content or clean up per the owner.

## Risks

- **Double-post on crash recovery.** Mitigated by *not* auto-retrying stuck `PUBLISHING` rows —
  they go to `FAILED / DISPATCH_INTERRUPTED` for the admin to check Meta and decide. Accept that a
  crash exactly between "Meta accepted" and "row saved" needs a human.
- **The 20s tick means "Publicar ahora" is not instant.** Acceptable per the brainstorm (owner
  publishes a few products occasionally). If it ever needs to feel instant, a post-commit
  `ApplicationEventPublisher` nudge that runs one `execute()` pass is a clean follow-up.
- **Losing the immediate pub/fail screen** is a deliberate UX trade the owner approved. Historial
  already shows per-row status and errors.
- **Second app instance** would need `SELECT ... FOR UPDATE SKIP LOCKED` in `findDueForDispatch`
  and a claim column. Out of scope; single instance today.
