# Social Publishing — Publication History View (Increment H, Etapa H-2)

## Context

Increment H, Etapa 1 (spec `2026-09-05-social-publishing-batch-design.md`) shipped: an admin
screen at `/admin/publicaciones` that publishes N products × platforms to Instagram/Facebook in
one synchronous batch, plus per-post photo override, image history, and variant caption tokens.

What Etapa 1 does **not** give the shop owner: any way to look back. `PublishProductsBatchResult`
is rendered once, in memory, and gone on navigation. There is no "what did I post last week", no
way to retry the ones that failed after fixing a token, no link to the live post. The backend
`list()` / `get()` / `retry()` methods on `PublicationService` exist and are exposed
(`GET /api/admin/publications`, `/{id}`, `POST /{id}/retry`) but have **zero frontend consumers**.

Separately, the design review for the H roadmap settled the sequencing: **H complete = H-2
(this doc) → H-3 (scheduling) → H-4 (carousel) → H-5 (campaign reporting)**. Reels is a
separate future increment, not part of H.

This etapa builds the history view and lays the data foundation (`publication_batches`) that
H-3, H-4 and H-5 all build on.

## Scope

In scope:
- A new `publication_batches` table: one row per `PublishProductsBatchUseCase.execute()` call,
  holding the batch-level data (caption template, hashtags, campaign label, actor, timestamp)
  that Etapa 1 either duplicates across rows or loses.
- `publications.batch_id` FK + `publications.external_permalink` column.
- Capture the live-post permalink at publish time (Instagram: one extra Graph call; Facebook:
  parse the `post_id` already in the response).
- Three new read/action endpoints: list batches, batch detail, retry-failed-in-batch.
- A second tab on `/admin/publicaciones` — **Publicar** | **Historial** — with the Etapa 1
  compose UI unchanged under "Publicar" and a grouped, expandable batch history under "Historial".

Out of scope (later etapas, each its own brainstorm):
- H-3 scheduling: a `@Scheduled` job that dispatches `SCHEDULED` rows at `scheduledAt`; a
  date/time picker in the compose UI. `publication_batches` will gain a nullable `scheduled_at`
  then — not added now.
- H-4 carousel: multi-media posts. `PublicationDispatchPayload.mediaUrl` stays singular here.
- H-5 campaign reporting: a view grouped by `campaign_label` across batches. The
  `publication_batches` table added here is exactly what makes that a clean `GROUP BY`.
- Approval-workflow UI (`submitForReview` / `approve` / `reject`). Etapa 1 publishes with
  `approvalRequired=false`; that stays. The `reject`/`approve` endpoints keep having no UI.

## Data model

### New table: `publication_batches`

Migration **`V100__publication_batches.sql`** (current highest is V99).

```sql
CREATE TABLE publication_batches (
    id               UUID PRIMARY KEY,
    caption_template TEXT        NOT NULL,
    hashtags_json    TEXT        NOT NULL,
    campaign_label   VARCHAR(120),
    created_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL
);

ALTER TABLE publications ADD COLUMN batch_id UUID REFERENCES publication_batches(id);
ALTER TABLE publications ADD COLUMN external_permalink TEXT;

CREATE INDEX idx_publications_batch_id ON publications(batch_id);
CREATE INDEX idx_publication_batches_created_at ON publication_batches(created_at DESC);
```

`hashtags_json` uses the same serialized-list convention as `publications.hashtags_json`
(a JSON string array, written/read by `PublicationService`'s existing `writeHashtags`/`readHashtags`).

**No backfill.** Pre-flight step in the plan: confirm `SELECT count(*) FROM publications` is 0 in
local, staging and prod before deploy (Etapa 1's spec already assessed this as "very likely
empty — zero frontend consumers ever existed"; Etapa 1 shipped only days ago). If any row exists,
it keeps `batch_id = NULL` and the frontend groups those under a single "Sin tanda" section
keyed on `null`. The endpoints and DTOs below already tolerate `batch_id = NULL`, so this is a
display concern only, not a migration blocker.

### Why a table and not just `batch_id` on `publications`

Three concrete needs the grouped-history UI has that a bare column cannot serve:

1. **Stable batch identity.** Grouping on `campaign_label` fails: it is optional (nullable in
   Etapa 1) and reusable — "Liquidación primavera" can span three batches over a week. The
   history groups by *batch*; the campaign report (H-5) groups by *label across batches*. These
   are different axes and both are needed.
2. **"Volver a publicar" needs the template back.** `publications.caption` stores the
   *interpolated* text (`"Chaqueta a solo $49.990"`), not `"{producto} a solo {precio}"`.
   Re-publishing a batch with edits requires the original template, which only lives on the
   batch.
3. **H-5 is a clean read.** `SELECT campaign_label, ... FROM publication_batches b JOIN
   publications p ON p.batch_id = b.id GROUP BY campaign_label` versus reconstructing batches
   from a `(campaign_label, created_by, created_at ± window)` heuristic.

It is the shared spine for H-2 → H-5, not speculative schema.

## Backend

### `PublicationBatchEntity` + repository

New JPA entity `publication/infrastructure/persistence/entities/PublicationBatchEntity.java`
mapping `publication_batches` (fields: `id`, `captionTemplate`, `hashtagsJson`, `campaignLabel`,
`createdBy`, `createdAt`). No relationships mapped on it — `PublicationEntity` gets the
`batch_id` column as a plain `UUID batchId` field (not a `@ManyToOne`; the module already keeps
`productId`/`sourceId` as bare UUIDs rather than JPA associations, follow that).

New `PublicationBatchJpaRepository extends JpaRepository<PublicationBatchEntity, UUID>` with:
```java
List<PublicationBatchEntity> findAllByOrderByCreatedAtDesc();
```

`PublicationJpaRepository` gains:
```java
List<PublicationEntity> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
List<PublicationEntity> findByBatchIdInOrderByCreatedAtAsc(Collection<UUID> batchIds);
```

### `CreatePublicationCommand` + `PublicationEntity` gain `batchId`

`CreatePublicationCommand` gets a trailing `UUID batchId` component (nullable). Every existing
caller passes `null` except `PublishProductsBatchUseCase`. `PublicationService.create` copies it
onto `entity.setBatchId(command.batchId())`.

`PublicationEntity`: add `@Column(name = "batch_id") private UUID batchId;` + getter/setter, and
`@Column(name = "external_permalink", columnDefinition = "text") private String externalPermalink;`
+ getter/setter.

### `PublishProductsBatchUseCase`: create the batch row first

Before the product loop in `execute(...)`:
```java
PublicationBatchEntity batch = new PublicationBatchEntity();
batch.setId(UUID.randomUUID());
batch.setCaptionTemplate(command.captionTemplate());
batch.setHashtagsJson(serializeHashtags(command.hashtags())); // same JSON-array convention
batch.setCampaignLabel(trimToNull(command.campaignLabel()));
batch.setCreatedBy(actorUserId);
batch.setCreatedAt(Instant.now());
publicationBatchRepository.save(batch);
```
Then thread `batch.getId()` into each `CreatePublicationCommand` built in `publishOne`.

The use case stays **non-`@Transactional`** (unchanged rationale from Etapa 1). The batch row is
saved in its own transaction via the repository; each `create`/`dispatch` pair commits
independently. A batch row with zero successful children is a valid, expected state (the whole
batch failed — e.g. no Meta credentials) and is still shown in the history.

If `command.productIds()` is empty the endpoint already rejects it (`@NotEmpty`), so a batch row
is never created with no children.

### Permalink capture

`PublicationDispatcher.DispatchResult` gains a trailing `String remotePermalink` component
(nullable — null on failure, null if the platform call to fetch it fails). All existing
constructions of `DispatchResult` (both adapters, `MetaDirectPublicationDispatcher` has none of
its own, `PublicationService`'s catch block, and every test) append the new arg.

**`InstagramGraphPublisherAdapter`** — after the `media_publish` call returns `remotePostId`, one
more call:
```java
Map<String, Object> permalinkResponse = client.get()
        .uri("/{mediaId}?fields=permalink&access_token={token}", remotePostId, config.instagramAccessToken())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
String permalink = permalinkResponse.get("permalink") == null ? null
        : String.valueOf(permalinkResponse.get("permalink"));
```
Wrapped so a failure here does **not** flip the whole publish to failed — the post is already
live. On exception, log at WARN and return the success result with `remotePermalink = null`.

**`FacebookPagePublisherAdapter`** — the `/photos` response already yields a post identifier: the
adapter reads `post_id`, falling back to `id`. The permalink is a constant web prefix plus that
identifier — the Graph API base URL is a different host and must not be used to derive it:
```java
Object postIdRaw = response.get("post_id"); // NOT the "id" fallback — only post_id is a feed story
String permalink = postIdRaw == null ? null : "https://www.facebook.com/" + postIdRaw;
```
When only `id` is returned (a bare photo id, not a feed story), `permalink = null`. The existing
`remotePostId` logic keeps its `post_id`-then-`id` fallback unchanged; only the permalink is
strict about needing `post_id`.

`PublicationService.dispatchInternal`: on the `SUCCEEDED` branch, add
`entity.setExternalPermalink(result.remotePermalink());`. On failure, leave it null.

`PublicationDto` gains a trailing `String externalPermalink` component; `toDto` maps
`entity.getExternalPermalink()`. (This flows to the existing `GET /{id}` too — harmless, it's
just one more nullable field.)

### New DTOs

`publication/application/dto/PublicationBatchSummaryDto.java`:
```java
public record PublicationBatchSummaryDto(
        UUID batchId,                 // null for the legacy "Sin tanda" pseudo-group
        String campaignLabel,         // nullable
        Instant createdAt,
        Set<PublicationPlatform> platforms,   // distinct across the batch's rows
        int total,
        int published,
        int failed,
        int scheduled,
        int pending                   // anything not in the three buckets above (PUBLISHING, DRAFT, ...)
) {}
```

`publication/application/dto/PublicationBatchDetailDto.java`:
```java
public record PublicationBatchDetailDto(
        UUID batchId,                 // nullable
        String campaignLabel,
        String captionTemplate,       // null for the legacy pseudo-group
        List<String> hashtags,
        Instant createdAt,
        List<UUID> productIds,        // distinct, for "volver a publicar" preload
        List<Row> rows
) {
    public record Row(
            UUID publicationId,
            UUID productId,
            String productName,       // resolved now; "(producto eliminado)" if the product is gone
            String thumbnailUrl,      // product.imageUrl, nullable
            PublicationPlatform platform,
            PublicationStatus status,
            String externalPermalink, // nullable
            String lastErrorCode,     // nullable
            String lastErrorMessage   // nullable
    ) {}
}
```

Product name/thumbnail resolution: batch-fetch with the existing
`ProductRepository.findAllByIds(Collection<UUID>)`, build a `Map<UUID, Product>`, fall back to
`"(producto eliminado)"` / `null` for ids not found.

### New read methods on `PublicationService`

```java
@Transactional(readOnly = true)
public List<PublicationBatchSummaryDto> listBatches();

@Transactional(readOnly = true)
public PublicationBatchDetailDto getBatch(UUID batchId);   // NoSuchElementException if unknown
```

- `listBatches`: `publicationBatchRepository.findAllByOrderByCreatedAtDesc()`, then load all
  publications for the returned batch ids in one `findByBatchIdInOrderByCreatedAtAsc` and group
  by `batchId` in memory to build the per-status counts (avoids N+1). If any legacy
  `batch_id IS NULL` publications exist, append one synthetic `PublicationBatchSummaryDto` with
  `batchId = null` aggregating them, sorted last; otherwise omit it entirely.
- `getBatch`: batch row + its publications (`findByBatchIdOrderByCreatedAtAsc`) + a batch product
  lookup via `ProductRepository.findAllByIds`. Requires a non-null UUID —
  `NoSuchElementException` (→ 404) for an unknown id. The "Sin tanda" pseudo-group is **not
  expandable** in this etapa: the frontend never calls `getBatch(null)`; its summary line is all
  it shows (YAGNI — legacy rows are a near-impossible edge case).

### Retry orchestration: `RetryFailedBatchUseCase`

The per-row re-dispatch **must not** run inside one transaction spanning the loop — same
rollback-only hazard as `PublishProductsBatchUseCase` (Etapa 1 spec). And `PublicationService`
cannot loop over its own `@Transactional retry(...)` — self-invocation bypasses the proxy. So the
orchestration lives in a new non-transactional use case, mirroring `PublishProductsBatchUseCase`:

```java
@Component
public class RetryFailedBatchUseCase {
    private final PublicationService publicationService;
    private final PublicationJpaRepository publicationRepository;

    // execute(UUID batchId, UUID actorUserId) -> PublicationBatchDetailDto:
    //   publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)
    //     .filter(status == FAILED)
    //     .forEach(p -> { try { publicationService.retry(p.getId(), actorUserId); }
    //                     catch (RuntimeException ignored) { /* row raced out of FAILED, skip */ } });
    //   return publicationService.getBatch(batchId);
}
```

`PublicationService.retry` already guards `status == FAILED` and throws otherwise, so a row that
raced out of FAILED is caught and skipped. An unknown `batchId` yields an empty FAILED list and
then `getBatch` throws `NoSuchElementException` → 404.

### Controller: three endpoints on the existing `PublicationController`

```java
@GetMapping("/batches")
@PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(...).PUBLICATIONS_READ)")
public List<PublicationBatchSummaryDto> listBatches();

@GetMapping("/batches/{batchId}")
@PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(...).PUBLICATIONS_READ)")
public PublicationBatchDetailDto getBatch(@PathVariable UUID batchId);

@PostMapping("/batches/{batchId}/retry-failed")
@PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(...).PUBLICATIONS_UPDATE)")
public PublicationBatchDetailDto retryFailed(@PathVariable UUID batchId,
                                             @AuthenticationPrincipal AuthenticatedUser currentUser);
// delegates to RetryFailedBatchUseCase.execute(batchId, currentUser.id())
```

`PUBLICATIONS_READ` / `PUBLICATIONS_UPDATE` already exist and are already granted to ADMIN — no
RBAC migration. Routes sit under the existing `/api/admin/publications` mapping; Caddy already
routes `/api/*` to the monolith (nothing publication-related goes to notification-service).

"Volver a publicar" needs **no endpoint** — the frontend reads `getBatch`, pre-loads the Publicar
tab's state (`productIds`, `captionTemplate`, `hashtags`, `campaignLabel`), and the user reviews
and submits, creating a fresh batch through the existing `POST /batch`.

## Frontend

### Tab shell on `/admin/publicaciones`

Mirror the established URL-synced tab pattern from `SystemSettingsPanel.tsx` (`parseSettingsTab`,
`url.searchParams.set('tab', ...)`, `history.replaceState`). Two tabs:

- **Publicar** (`?tab=publicar`, default) — the entire current `PublicacionesPage` compose flow,
  extracted verbatim into a `PublicarTab` component. No behavior change.
- **Historial** (`?tab=historial`) — new `HistorialTab` component.

`PublicacionesPage` becomes the shell: reads/writes the `tab` param, renders a
`role="tablist"` header with two `role="tab"` buttons (`aria-selected`, active style using
`pe-rose` underline like other admin active states), renders the active panel.

The `publicaciones.astro` intro paragraph stays generic ("Publica productos en Instagram y
Facebook, y revisa el historial de lo publicado.").

### `PublicarTab` — "Volver a publicar" preload

`PublicarTab` accepts an optional `preload?: { productIds: string[]; captionTemplate: string;
hashtags: string[]; campaignLabel: string | null }`. When present (set by the shell when the user
clicks "Volver a publicar" in Historial), a mount effect seeds the state: `Promise.all(productIds
.map(getProduct))` (the existing `getProduct(id)` — a handful of small parallel GETs, no new
endpoint), pre-checks those products, and sets `captionTemplate` / `hashtagsInput` /
`campaignLabel` from the preload. Products no longer found are silently dropped. The shell
switches to `?tab=publicar` and passes `preload`; `PublicarTab` calls a shell callback to clear
`preload` once consumed so a later manual tab switch doesn't re-seed.

### `HistorialTab`

Data: on mount, `getPublicationBatches()` → `PublicationBatchSummary[]`. Skeleton rows while
loading (>300ms). Error state with a retry button. Empty state: "Todavía no publicaste ninguna
tanda." + a button that switches to the Publicar tab.

**Batch card (collapsed):** a full-width `<button>` with `aria-expanded`:
- Left: campaign label in `font-sans` medium, or "Sin campaña" in `text-pe-muted`.
- Below it: relative time ("hace 2 h"), `title` attribute with the absolute timestamp.
- Right: platform chips (small "IG" / "FB" pills, `pe-surface` bg, `pe-border`), then the
  summary line with `font-variant-numeric: tabular-nums`:
  - all published → `"16 publicados"` in `text-pe-positive-ink`
  - any failed → `"14 publicados · 2 fallidos"`, the failed count in `text-pe-danger-ink`
  - any scheduled (future etapa, tolerate now) → `· N programados` in `text-pe-warning-ink`
- A chevron that rotates 90° on expand, `transition-transform` ~180ms, wrapped so
  `@media (prefers-reduced-motion: reduce)` drops the transition.

The "Sin tanda" pseudo-card (`batchId === null`, only present if legacy rows exist) is **not
expandable** — no chevron, no detail fetch, just the summary line.

**Batch card (expanded):** fetches `getPublicationBatchDetail(batchId)` on first expand (cache in
a `Map<batchId, detail>` in component state; re-fetch after a retry). Renders:
- A header action row:
  - if `detail.rows` has any `FAILED` → `[Reintentar fallidos]` (calls
    `retryBatchFailed(batchId)`, button shows a spinner + disables while in flight, then
    replaces the cached detail with the response).
  - always → `[Volver a publicar esta tanda]` → calls the shell's "preload Publicar" handler
    with `{ productIds: detail.productIds, captionTemplate: detail.captionTemplate, hashtags:
    detail.hashtags, campaignLabel: detail.campaignLabel }`.
  - the "Sin tanda" pseudo-group has no `captionTemplate` → hide "Volver a publicar" for it.
- A light table, one row per `PublicationBatchDetail.Row`:
  | thumbnail (w-8 h-10 object-cover) | product name (truncate) | platform ("IG"/"FB") | status pill | action |
- **Status pill** — icon + word, never color alone:
  - `PUBLISHED` → check icon + "Publicado", `bg-pe-positive-surface text-pe-positive-ink`
  - `FAILED` → x icon + "Falló", `bg-pe-danger-surface text-pe-danger-ink`
  - `SCHEDULED` → clock icon + "Programado", `bg-pe-warning-surface text-pe-warning-ink`
  - `PUBLISHING` / other → spinner-less dot icon + "Publicando" / the status word, `text-pe-muted`
- **Row action, contextual:**
  - `FAILED` → `[Reintentar]` (single-row, calls existing `POST /{id}/retry` via a new
    `retryPublication(id, token)` in `api.ts`; on success re-fetch this batch's detail) and,
    next to it, `[ver detalle]` — a disclosure toggling an inline sub-row showing
    `lastErrorCode: lastErrorMessage` in `text-[0.72rem] text-pe-muted font-mono`.
  - `PUBLISHED` with `externalPermalink` → `[Ver en Instagram ↗]` / `[Ver en Facebook ↗]`
    (`<a target="_blank" rel="noreferrer">`, external-link icon).
  - `PUBLISHED` without permalink (older row, or the fetch failed) → nothing, or a muted
    "publicado" with no link.

### `api.ts` additions

```ts
export interface PublicationBatchSummary {
  batchId: string | null;
  campaignLabel: string | null;
  createdAt: string;
  platforms: Array<'INSTAGRAM' | 'FACEBOOK'>;
  total: number; published: number; failed: number; scheduled: number; pending: number;
}
export interface PublicationBatchDetailRow {
  publicationId: string; productId: string; productName: string; thumbnailUrl: string | null;
  platform: 'INSTAGRAM' | 'FACEBOOK'; status: string;
  externalPermalink: string | null; lastErrorCode: string | null; lastErrorMessage: string | null;
}
export interface PublicationBatchDetail {
  batchId: string | null; campaignLabel: string | null; captionTemplate: string | null;
  hashtags: string[]; createdAt: string; productIds: string[]; rows: PublicationBatchDetailRow[];
}
export async function getPublicationBatches(token: string): Promise<PublicationBatchSummary[]>;
export async function getPublicationBatchDetail(batchId: string, token: string): Promise<PublicationBatchDetail>;
export async function retryBatchFailed(batchId: string, token: string): Promise<PublicationBatchDetail>;
export async function retryPublication(publicationId: string, token: string): Promise<unknown>; // POST /{id}/retry
```

Reuse the existing `apiFetch` + `authHeaders(token)` helpers.

## Testing

### Backend unit
- `PublishProductsBatchUseCaseTest` — add: a `publication_batches` row is created once per
  `execute()`, and every `CreatePublicationCommand` captured carries the same non-null `batchId`.
  Existing 7 tests get the new `null` trailing arg on `CreatePublicationCommand` assertions where
  they inspect it (most don't).
- `InstagramGraphPublisherAdapterTest` (extend / create if absent — `MetaDirectPublicationDispatcherTest`
  is the existing meta test): with `MockRestServiceServer`, a successful publish issues a third
  request `GET .../{mediaId}?fields=permalink` and the returned `permalink` lands on
  `DispatchResult.remotePermalink`; a 5xx on that third call still yields `SUCCEEDED` with
  `remotePermalink == null`.
- `FacebookPagePublisherAdapterTest` — a `post_id` in the `/photos` response produces
  `remotePermalink == "https://www.facebook.com/{post_id}"`; a response with only `id` produces
  `null`.
- `RetryFailedBatchUseCaseTest` — a batch with 1 FAILED + 1 PUBLISHED row: `execute` calls
  `retry` only for the FAILED one; a row that 's no longer FAILED is skipped without throwing.

### Backend integration — `PublicationControllerIT`
- `admin_sees_a_published_batch_in_the_history`: POST `/batch` for one real product on
  IG+FB (both fail — no Meta creds in test env, same as the existing batch test), then
  `GET /batches` returns one summary with `total=2, failed=2, published=0` and both platforms;
  `GET /batches/{id}` returns 2 rows with `status=FAILED` and `lastErrorCode` present and the
  `captionTemplate` / `productIds` echoed.
- `retry_failed_in_batch_redispatches_only_failed_rows`: same setup, `POST
  /batches/{id}/retry-failed` returns the detail with the rows still `FAILED` (still no creds)
  but each row's underlying `retryCount` incremented — assert via `GET /{publicationId}` on one
  row that `retryCount == 1`.
- `retry_failed_in_batch_requires_update_permission`: seller token (`publications.read` only) →
  403.
- `unknown_batch_id_returns_404`.

### Frontend — `PublicacionesPage.test.tsx` (extend) + a new `HistorialTab.test.tsx`
- Tab shell: default shows Publicar; clicking "Historial" swaps the panel and sets
  `?tab=historial`; a render with `?tab=historial` in the URL opens on Historial.
- `HistorialTab`: mocked `getPublicationBatches` → renders a collapsed card with the summary
  line; empty array → empty state with a button that switches to Publicar.
- Expand: clicking the card calls `getPublicationBatchDetail` once and renders the rows; a
  `FAILED` row shows `[Reintentar]` + `[ver detalle]`, and "ver detalle" reveals the error text.
- `[Reintentar fallidos]` calls `retryBatchFailed` and re-renders from its response.
- A `PUBLISHED` row with an `externalPermalink` renders an `<a>` to that URL with
  `target="_blank"`.
- "Volver a publicar esta tanda" switches to the Publicar tab (assert `?tab=publicar` and that
  the compose form is shown) — the deep preload assertion (products actually loaded) can be a
  lighter check that the preload handler was invoked with the right ids.

## Migration / deploy checklist (for the plan)

1. Confirm `publications` is empty in every environment before merging V100.
2. V100 is expand-only (adds a table + two nullable columns + indexes) — safe with the running
   old app; deploy order doesn't matter.
3. `notification-service` maps read-only views of `publications` under `ddl-auto: validate` —
   **adding** nullable columns does not break `validate` (it only fails on missing/mistyped
   mapped columns, not extra DB columns). No `*RoEntity` change needed. Note it in the plan so
   the reviewer doesn't have to re-derive it.
