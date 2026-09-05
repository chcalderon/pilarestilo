# Social Publishing — Scheduling (Increment H, Etapa H-3)

## Context

H-1 shipped immediate batch publishing to Instagram/Facebook. H-2 (spec
`2026-09-05-social-publishing-h2-history-design.md`, on `develop`) added the
`publication_batches` table, the batch history view, and permalink capture.

H-3 makes a batch schedulable: pick a future date/time instead of "publish now", and a background
job publishes the batch when its time comes. `Publication.scheduledAt` (nullable `Instant`,
column `scheduled_at`) and `PublicationStatus.SCHEDULED` / `CANCELLED` already exist and are
already accepted by `PublicationService.dispatchInternal` (it dispatches from `APPROVED` **or**
`SCHEDULED`). Nothing consumes them today — no code path ever creates a `SCHEDULED` row and no job
ever picks one up.

Approach (agreed during brainstorming): **poll by status + timestamp**, mirroring the codebase's
existing `DispatchAutoDeliveryScheduler` (`findByStatusAndDispatchedAtBefore`). Single prod
instance, no distributed lock. Not an outbox/lease queue — that solves a concurrency problem this
deployment does not have.

## Scope

In scope:
- `scheduledAt` on `PublishProductsBatchCommand` / `PublishProductsBatchRequest`; when present the
  batch is created as `SCHEDULED` and **not** dispatched immediately.
- `publication_batches.scheduled_at` column (V101) for display, rescheduling and editing.
- A `@Scheduled` job (`PublishDueScheduledPublicationsScheduler` → use case) that runs every
  minute, publishes due `SCHEDULED` rows, and fails rows that are past the lateness cap with
  `SCHEDULE_WINDOW_MISSED`.
- Three batch-level actions on a scheduled batch: **cancel**, **reschedule** (time only), **edit
  content** (`PUT` replaces the SCHEDULED rows).
- A "Publicar ahora | Programar" choice + datetime picker in the Publicar tab, pinned to
  `America/Santiago`; scheduled-batch representation + actions in HistorialTab.

Out of scope:
- Recurring schedules. One-shot only.
- Per-row / per-product scheduling. The whole batch fires at one instant.
- Editing a batch that has already started publishing (any row not `SCHEDULED` → 409).
- Approval workflow (unchanged — `approvalRequired=false`).
- H-4 carousel, H-5 campaign reporting (later etapas, own brainstorms).

## Global constraints (carried from H-1/H-2)

- Flyway: never edit an applied migration. Current highest **V100**; this adds **V101**.
  Expand-only (one nullable column + one partial index), safe against the running old app.
- `notification-service` maps read-only views of `publications` under `ddl-auto: validate`;
  adding a nullable column to `publication_batches` (which it does not map) needs no `*RoEntity`
  change.
- Scheduled use cases take an injected `java.time.Clock` (package-private constructor for tests,
  `Clock.systemUTC()` from the `@Autowired` one) — the pattern `AutoConfirmDeliveredDispatchesUseCase`
  established. `@Value` config keys with defaults, env-derived names.
- Non-transactional orchestrators when looping over `@Transactional` calls on `PublicationService`
  (rollback-only hazard — H-1 spec).
- Spanish (Chilean, tú-form) UI copy. No em dashes in copy. Status pills carry an icon + a word,
  never color alone. `pe-*` tokens only.

## Data model

### V101 — `publication_batches.scheduled_at`

```sql
ALTER TABLE publication_batches ADD COLUMN scheduled_at TIMESTAMPTZ;

CREATE INDEX idx_publications_scheduled_due
    ON publications (scheduled_at)
    WHERE status = 'SCHEDULED';
```

- `publication_batches.scheduled_at` — `NULL` for an immediate batch (every H-1/H-2 batch),
  set for a scheduled one. It is the single source of truth the reschedule/edit endpoints write;
  the job never reads it (it reads `publications.scheduled_at`).
- The partial index makes the job's `WHERE status = 'SCHEDULED' AND scheduled_at <= ?` query an
  index scan over only the pending rows, not the whole (growing) `publications` table.

No new enum values. `SCHEDULE_WINDOW_MISSED` is an `errorCode` **string** written to
`publications.last_error_code` / `publication_attempts.error_code` (that column is a free-text
`varchar(80)`, like the existing `INSTAGRAM_PUBLISH_ERROR` / `DISPATCH_ERROR`).

## Backend

### `create()` puts a scheduled row in `SCHEDULED`

`PublicationService.create` currently:
```java
entity.setStatus(command.approvalRequired() ? PublicationStatus.DRAFT : PublicationStatus.APPROVED);
```
becomes:
```java
PublicationStatus initialStatus;
if (command.approvalRequired()) {
    initialStatus = PublicationStatus.DRAFT;
} else if (command.scheduledAt() != null) {
    initialStatus = PublicationStatus.SCHEDULED;
} else {
    initialStatus = PublicationStatus.APPROVED;
}
entity.setStatus(initialStatus);
```
`approvalStatus` stays `NOT_REQUIRED` for both the `APPROVED` and `SCHEDULED` branches (only the
`DRAFT`/approval path uses `PENDING_REVIEW`). `entity.setScheduledAt(command.scheduledAt())` is
already there (line 110). `CreatePublicationCommand` already carries `scheduledAt` — no change to
that record.

### `PublishProductsBatchUseCase` — scheduled mode

`PublishProductsBatchCommand` gains a trailing `Instant scheduledAt` (nullable).
`PublishProductsBatchRequest` gains `String scheduledAt` (ISO-8601 instant string, nullable),
parsed to `Instant.parse(...)` in the controller mapper; a value in the past → 400.

`execute(...)`:
- The `PublicationBatchEntity` it already creates gains `batch.setScheduledAt(command.scheduledAt())`.
- `publishOne(...)` builds the `CreatePublicationCommand` with `command.scheduledAt()` as the
  `scheduledAt` arg (was `null`).
- When `command.scheduledAt() != null`: call `publicationService.create(...)` and **return a
  scheduled item result** — do **not** call `publicationService.dispatch(...)`.
  `PublishProductsBatchResult.PublicationItemResult` gains a trailing `boolean scheduled` field;
  a scheduled item is `new PublicationItemResult(productId, platform, false, publicationId, null,
  true)` (`success=false`, `scheduled=true`). Immediate items keep `scheduled=false`.

### The job

`publication/infrastructure/jobs/PublishDueScheduledPublicationsScheduler.java` (new — mirrors
`DispatchAutoDeliveryScheduler` exactly):
```java
@Component
public class PublishDueScheduledPublicationsScheduler {
    private final PublishDueScheduledPublicationsUseCase useCase;
    // constructor

    @Scheduled(cron = "${app.social-publishing.schedule.cron:0 * * * * *}")
    public void run() {
        int handled = useCase.execute();
        if (handled > 0) log.info("Published/failed {} due scheduled publications", handled);
    }
}
```

`publication/application/usecases/PublishDueScheduledPublicationsUseCase.java` (new,
non-`@Transactional` orchestrator, injected `Clock`):
```java
@Component
public class PublishDueScheduledPublicationsUseCase {
    private final PublicationJpaRepository publicationRepository;
    private final PublicationService publicationService;
    private final Clock clock;
    private final long maxLatenessMinutes;   // @Value("${app.social-publishing.schedule.max-lateness-minutes:360}")

    // package-private ctor takes Clock for tests; @Autowired ctor passes Clock.systemUTC()

    public int execute() {
        Instant now = Instant.now(clock);
        Instant staleBefore = now.minus(Duration.ofMinutes(maxLatenessMinutes));
        List<PublicationEntity> due = publicationRepository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(PublicationStatus.SCHEDULED, now);
        int handled = 0;
        for (PublicationEntity p : due) {
            try {
                if (p.getScheduledAt() != null && p.getScheduledAt().isBefore(staleBefore)) {
                    publicationService.markScheduleWindowMissed(p.getId());
                } else {
                    publicationService.dispatch(p.getId(), null);
                }
                handled++;
            } catch (RuntimeException ex) {
                log.warn("Scheduled publication {} could not be handled: {}", p.getId(), ex.getMessage());
            }
        }
        return handled;
    }
}
```

New repository method:
`List<PublicationEntity> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(PublicationStatus status, Instant cutoff)`.

New `PublicationService` method (no schedule config in the service — the message is fixed text):
```java
@Transactional
public PublicationDto markScheduleWindowMissed(UUID id) {
    PublicationEntity entity = findById(id);
    if (entity.getStatus() != PublicationStatus.SCHEDULED) {
        throw new DomainException("Publication is not scheduled: " + entity.getStatus());
    }
    entity.setStatus(PublicationStatus.FAILED);
    entity.setLastErrorCode("SCHEDULE_WINDOW_MISSED");
    entity.setLastErrorMessage(
            "La hora programada ya pasó; no se publicó automáticamente. Publícala o reprográmala a mano.");
    entity.setUpdatedAt(Instant.now());
    PublicationEntity saved = publicationRepository.save(entity);
    eventPublisher.publish(new PublicationDispatchFailed(saved.getId(), 0, "SCHEDULE_WINDOW_MISSED"));
    return toDto(saved);
}
```

`dispatch(id, actorUserId)` from a `SCHEDULED` row: `dispatchInternal` already accepts
`SCHEDULED`, transitions it `PUBLISHING → PUBLISHED/FAILED`, records the attempt. No change.

**Catch-up after downtime** needs no special code: the job's query is `scheduled_at <= now`, so
everything that came due while the server was down is returned on the first tick after restart.
The lateness cap is the only guard — anything more than `max-lateness-minutes` overdue is failed
rather than posted at a surprising time.

### Batch actions — three endpoints + `UpdateScheduledBatchUseCase`

All on the existing `PublicationController`, all guarded by the existing
`hasRole('ADMIN') or @rbac.hasPermission(..., PUBLICATIONS_UPDATE)`.

**Wrong-state responses:** every "this batch is not in a state that allows X" case in this etapa
throws `DomainException`, which the existing `GlobalExceptionHandler` maps to **400** — consistent
with how `retry` / `dispatch` already behave. This etapa introduces no 409.

**`POST /api/admin/publications/batches/{batchId}/cancel`** → `PublicationBatchDetailDto`
- A `PublicationService` method `cancelScheduledBatch(UUID batchId)` (a simple bulk status flip,
  no orchestration): load the batch's `publications`; the ones in `SCHEDULED` → `CANCELLED`
  (+ `updatedAt`); if none were `SCHEDULED` throw `DomainException`
  ("Esta tanda no tiene publicaciones programadas para cancelar").
- Returns `publicationService.getBatch(batchId)`.

**`POST /api/admin/publications/batches/{batchId}/reschedule`** body
`{ "scheduledAt": "<iso instant>" }` → `PublicationBatchDetailDto`
- `RescheduleBatchRequest(String scheduledAt)`; parse to `Instant`, reject past → 400.
- `PublicationService.rescheduleBatch(UUID batchId, Instant newScheduledAt)`: update
  `publication_batches.scheduled_at`; every `SCHEDULED` row's `scheduled_at` → `newScheduledAt`
  (+ `updatedAt`). If no `SCHEDULED` rows → 400 "Esta tanda ya no está programada".
- Returns the refreshed detail.

**`PUT /api/admin/publications/batches/{batchId}`** body = `PublishProductsBatchRequest` (same
shape as `POST /batch`, `scheduledAt` required here) → `PublicationBatchDetailDto`
- `UpdateScheduledBatchUseCase` (new, non-`@Transactional` — it deletes then recreates rows
  through `PublicationService`, same rollback reasoning):
  1. Load the batch's `publications`. If **any** is not `SCHEDULED` → `DomainException`
     ("Esta tanda ya empezó a publicarse, no se puede editar") → 400.
  2. Delete all of them (`publicationRepository.deleteAll(rows)` — orphan cleanup cascades the
     media bundles / snapshots / attempts via the existing `CascadeType.ALL` + `orphanRemoval`).
  3. Update the `PublicationBatchEntity`: `captionTemplate`, `hashtagsJson`, `campaignLabel`,
     `scheduledAt` from the new command.
  4. Regenerate the `SCHEDULED` rows: same per-item loop as `PublishProductsBatchUseCase` in
     scheduled mode. **Extract a package-private `BatchPublicationFactory`** component that both
     `PublishProductsBatchUseCase` and `UpdateScheduledBatchUseCase` depend on: it owns caption
     interpolation (the `{producto}/{precio}/{color}/{talla}/{cantidad}` logic currently private
     to `PublishProductsBatchUseCase`), variant resolution, media-bundle shape and the
     idempotency-key scheme (`pub-batch-<productId>-<platform>-<uuid>`), exposing one method that
     turns `(command, product, platform, batchId)` into a `CreatePublicationCommand`. Regenerated
     rows get fresh idempotency keys.
  5. Return `publicationService.getBatch(batchId)`.

"Edit content" needs the `PUT` because caption/products/platforms all change together and
regenerating is simpler and safer than diffing. A batch mid-publish can't be edited — step 1
guards that.

### DTO changes

`PublicationBatchSummaryDto` and `PublicationBatchDetailDto` each gain a trailing
`Instant scheduledAt` (nullable). `PublicationService.summarize(...)` reads it from the
`PublicationBatchEntity`; `getBatch(...)` already has the entity. `PublishProductsBatchResult
.PublicationItemResult` gains trailing `boolean scheduled`.

## Timezone

**Backend:** none. Every timestamp is an `Instant` (`timestamptz`). The job compares instants.
`markScheduleWindowMissed` / `reschedule` reason in instants.

**Frontend:** the shop is in Chile and the spec pins scheduling to `America/Santiago` regardless
of the admin's device timezone. A dependency-free helper module
`frontend/src/lib/santiagoTime.ts`:

```ts
const TZ = 'America/Santiago';

/** Offset (ms) of America/Santiago from UTC at the given instant — handles CLT/CLST DST. */
function santiagoOffsetMs(at: Date): number {
  // Format the instant as if it were in Santiago and as if it were in UTC, diff the two.
  const santiago = new Date(at.toLocaleString('en-US', { timeZone: TZ }));
  const utc = new Date(at.toLocaleString('en-US', { timeZone: 'UTC' }));
  return santiago.getTime() - utc.getTime();
}

/** "2026-09-06T10:00" (a Santiago wall-clock from <input type=datetime-local>) -> UTC ISO instant. */
export function santiagoWallTimeToInstant(local: string): string {
  const naive = new Date(local + ':00'); // parsed in the browser's local tz
  // First approximation using the browser tz, then correct by the Santiago offset at that instant.
  const browserOffset = -naive.getTimezoneOffset() * 60000;
  const santiagoOffset = santiagoOffsetMs(naive);
  return new Date(naive.getTime() + browserOffset - santiagoOffset).toISOString();
}

/** UTC ISO instant -> "sáb 6 sep, 10:00" in Santiago, for display. */
export function instantToSantiagoLabel(iso: string): string {
  return new Date(iso).toLocaleString('es-CL', {
    timeZone: TZ, weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
  });
}

/** UTC ISO instant -> "2026-09-06T10:00" for pre-filling <input type=datetime-local>. */
export function instantToSantiagoInputValue(iso: string): string {
  const d = new Date(iso);
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).formatToParts(d);
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? '00';
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
}
```

`santiagoTime.test.ts` locks the round-trip and a known DST date (Chile springs forward the first
Sunday of September, falls back the first Sunday of April).

## Frontend

### `PublicarTab`

- New state `mode: 'now' | 'schedule'` (radio, "Publicar ahora" / "Programar"). `schedule` reveals
  a `<label>` + `<input type="datetime-local">` with `min` = now + 5 min in Santiago input format.
- `handlePublish` (rename to `handleSubmit`): when `mode === 'schedule'`, add
  `scheduledAt: santiagoWallTimeToInstant(scheduleInput)` to the batch payload; the CTA reads
  "Programar publicación". When `now`, unchanged ("Publicar ahora").
- The result panel: a scheduled submit shows "Programada para <label>. La vas a ver en Historial."
  instead of the per-item ✓/✗ list.
- **Edit mode** — new prop `editingBatchId?: string`. When set:
  - the mount preload effect (H-2's `preload` mechanism) also seeds `mode='schedule'` +
    `scheduleInput` from `preload.scheduledAt`.
  - `handleSubmit` calls `updateScheduledBatch(editingBatchId, payload, token)` (`PUT`) instead of
    `publishProductsBatch`.
  - the CTA reads "Guardar cambios"; a "Cancelar edición" link calls `onEditCancelled?.()`.
- `PublicarTabPreload` gains `scheduledAt?: string | null`.

### `PublicacionesPage` shell

- New state `editingBatchId: string | null`. HistorialTab's `onEditScheduled(batchId, preload)`
  sets it + `preload` + switches to `?tab=publicar`. `onEditCancelled` / `onPreloadConsumed`
  clear it (and clear `preload`) — a `PUT` success in `PublicarTab` calls `onEditCancelled` too,
  then the user can go back to Historial and see the updated batch.

### `HistorialTab`

- **When the scheduled styling applies:** `scheduledAt != null` **and** at least one row is
  `SCHEDULED`. Then:
  - Collapsed summary line: `◷ Programada para <instantToSantiagoLabel(scheduledAt)>` in
    `text-pe-warning-ink`, instead of the published/failed counts. If some rows already failed
    (window missed) append `· N fallidos`.
  - `scheduledAt != null` but **no** row still `SCHEDULED`: the job has run (or the batch was
    cancelled) — fall back to the normal H-2 rendering (published/failed counts, or all rows
    `Cancelado`).
  - Expanded header actions (only while it still has `SCHEDULED` rows):
    `Cancelar programación` (→ `cancelBatch`), `Cambiar hora` (reveals an inline
    `<input type="datetime-local">` + "Guardar" → `rescheduleBatch`), `Editar`
    (→ `onEditScheduled(batchId, { productIds, captionTemplate, hashtags, campaignLabel,
    scheduledAt })`).
  - A cancelled batch: rows show a `Cancelada` pill (new `StatusPill` case for `CANCELLED`),
    no actions.
- `StatusPill` gains `CANCELLED` → "Cancelado", `bg-pe-surface text-pe-muted` + a slash/ban icon.

### `api.ts`

```ts
// PublishProductsBatchRequest gains: scheduledAt?: string;   // ISO instant
// PublicationBatchSummary + PublicationBatchDetail gain: scheduledAt: string | null;
// PublishProductsBatchItemResult gains: scheduled: boolean;
export async function cancelBatch(batchId: string, token: string): Promise<PublicationBatchDetail>;
export async function rescheduleBatch(batchId: string, scheduledAt: string, token: string): Promise<PublicationBatchDetail>;
export async function updateScheduledBatch(batchId: string, body: PublishProductsBatchRequest, token: string): Promise<PublicationBatchDetail>;
```

## Config

| Key | Env | Default | Meaning |
|---|---|---|---|
| `app.social-publishing.schedule.cron` | `APP_SOCIAL_PUBLISHING_SCHEDULE_CRON` | `0 * * * * *` | job cadence (every minute) |
| `app.social-publishing.schedule.max-lateness-minutes` | `APP_SOCIAL_PUBLISHING_SCHEDULE_MAX_LATENESS_MINUTES` | `360` | past this many minutes overdue, fail instead of publish |

Both get an `additional-spring-configuration-metadata.json` entry and an
`infra/.env.example` line (H-1's spec calls out that the n8n keys' absence from `.env.example` is
exactly why that dead path went unnoticed).

## Testing

### Backend unit
- `PublishDueScheduledPublicationsUseCaseTest` (injected fixed `Clock`):
  - a row due 1 min ago → `publicationService.dispatch(id, null)` called.
  - a row due 7 h ago (cap 360 min) → `markScheduleWindowMissed(id)` called, `dispatch` not.
  - a row due in 10 min → not returned by the query / not touched.
  - one row throwing does not stop the others (`handled` counts the rest).
- `PublicationServiceTest`:
  - `create` with `scheduledAt` set + `approvalRequired=false` → status `SCHEDULED`,
    `approvalStatus=NOT_REQUIRED`.
  - `markScheduleWindowMissed` on a `SCHEDULED` row → `FAILED` + `SCHEDULE_WINDOW_MISSED` +
    `PublicationDispatchFailed` event; on a non-`SCHEDULED` row → `DomainException`.
  - `cancelScheduledBatch` flips only `SCHEDULED` rows to `CANCELLED`; none scheduled →
    `DomainException`.
  - `rescheduleBatch` updates the batch row + `SCHEDULED` rows; none scheduled → `DomainException`.
- `PublishProductsBatchUseCaseTest`: with `scheduledAt` set, `publicationService.dispatch` is
  **never** called; the batch row carries `scheduledAt`; every item result has `scheduled=true`.
- `UpdateScheduledBatchUseCaseTest`: replaces rows for an all-`SCHEDULED` batch; a batch with a
  non-`SCHEDULED` row → `DomainException`, nothing deleted.

### Backend integration — `PublicationControllerIT`
- `POST /batch` with a future `scheduledAt` → `200`, items `scheduled:true`; `GET /batches` shows
  the batch with `scheduledAt` and rows `SCHEDULED`.
- `POST /batches/{id}/cancel` → rows `CANCELLED`.
- `POST /batches/{id}/reschedule` with a new future instant → `GET /batches/{id}` shows the new
  `scheduledAt` on the batch and the rows; a past instant → `400`.
- `PUT /batches/{id}` with a changed product list + caption → `GET /batches/{id}` reflects the new
  rows and template; still `SCHEDULED`.
- The job path: inject / call `PublishDueScheduledPublicationsUseCase` directly with a `Clock`
  fixed after the scheduled instant → the row transitions to `FAILED` (no Meta creds in the test
  env, same as every other IT) with a real `lastErrorCode`, proving the dispatch path ran.
- `POST /batch` with a `scheduledAt` in the past → `400`.

### Frontend
- `santiagoTime.test.ts`: `santiagoWallTimeToInstant` then `instantToSantiagoInputValue`
  round-trips; a summer date and a winter date both map to the right UTC hour.
- `PublicarTab.test.tsx`: choosing "Programar" + a datetime → payload carries `scheduledAt`;
  the CTA text switches; a scheduled submit shows the "Programada para…" confirmation.
  With `editingBatchId` set, submit calls `updateScheduledBatch` and the CTA says "Guardar cambios".
- `HistorialTab.test.tsx`: a batch with `scheduledAt` + all-`SCHEDULED` rows shows
  "Programada para" and the three actions; "Cancelar programación" calls `cancelBatch`;
  "Cambiar hora" reveals the picker and "Guardar" calls `rescheduleBatch`; "Editar" calls
  `onEditScheduled` with the batch data.
- `PublicacionesPage.test.tsx`: HistorialTab "Editar" switches to `?tab=publicar` with the shell
  in edit mode.

## Migration / deploy checklist (for the plan)

1. V101 is expand-only (one nullable column + one partial index). Deploy order does not matter.
2. `@EnableScheduling` is already on `PilarEstiloApplication`. The new job starts running on
   deploy; with no `SCHEDULED` rows it is a no-op query once a minute.
3. `notification-service` does not map `publication_batches` — no RO-entity change.
4. Prod has one backend instance, so the once-a-minute job has no double-fire risk. If a second
   instance is ever added, this job needs a lock (ShedLock or a `SELECT ... FOR UPDATE SKIP
   LOCKED` claim) — note it, do not build it now.
