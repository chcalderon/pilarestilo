# Increment H, Etapa H-5 — Campaign reporting

**Status:** design approved in chat 2026-09-06, pending written-spec review.

**Parent:** Increment H (social publishing). Final etapa. H-2 gave the Historial tab a per-batch
detail view; H-4a/H-4b added the product gallery and IG/FB carousels. H-5 is the campaign-level
rollup: group batches by `publication_batches.campaign_label`, aggregate the outcome, and pull
engagement metrics (impressions / reach / likes / comments / shares / saved) from the Meta Graph
API per published post.

Reels / video stays a separate future increment.

## Why

`campaign_label` is a free-text field the admin sets in the Publicar tab. Posts from one campaign
can span several batches (published now, scheduled for later, a retry batch). Today the only
rollup is per-batch. The shop wants a per-campaign answer to "how did this campaign do" — how many
posts went live on each platform, links to them, and how they performed.

## Global constraints

- Backend: Spring Boot 4 (Java 25), hexagonal. One use-case class per action. Domain objects carry
  no framework annotations. Jackson 3 (`tools.jackson.databind.*`), never `com.fasterxml`.
- Flyway only, never edit an applied migration. Current highest is **V102** → this adds **V103**.
- Every Meta Graph call reads `.body(String.class)` then `objectMapper.readTree(raw)` (Graph serves
  JSON as `Content-Type: text/javascript`).
- Meta metrics need scopes the current token may not have (`instagram_manage_insights`,
  `pages_read_engagement` / `read_insights`; only `content_publish` is confirmed granted — see
  `increment-d-meta-app`). The design **degrades gracefully**: a fetch that fails on a permission
  error (or any error) records the message in `publication_metrics.fetch_error` and the UI shows
  "no disponible" for that post. It never fails a refresh run or a page load.
- `notification-service` maps none of the publication tables — a new `publication_metrics` table
  needs no `*RoEntity` change. `ReadOnlyMappingIT` still runs V103.
- Permissions: reuse `PUBLICATIONS_READ` (reads) and `PUBLICATIONS_UPDATE` (the refresh POST). No
  new permission.
- Single production instance → the daily `@Scheduled` refresh needs no lock.
- Frontend: Astro 5 SSR + React islands, Zustand, Tailwind `pe-*` tokens, Vitest + RTL. Before
  writing any `CampanasTab` markup, invoke `ui-ux-pro-max` and `impeccable` (session rule).
- Caveman mode is chat-only; code, comments, commits, spec prose stay normal.

## Architecture

The metrics subsystem mirrors the publishing one (`MetaDirectPublicationDispatcher` + its two
adapters):

```
RefreshRecentMetricsScheduler  (@Scheduled daily)
     |  execute(new RecentDays(maxAgeDays))
CampanasTab "Actualizar métricas" button
     |  POST /campaigns/refresh-metrics?label=...
     v
RefreshMetricsUseCase.execute(MetricsRefreshScope)      (@Component, NOT @Transactional over the loop)
     |  selects PUBLISHED publications with external_post_id
     |  by campaign_label (via batch_id) OR by published_at >= now - n days
     |  per publication:
     v
PublicationMetricsFetcher.fetch(platform, externalPostId): Optional<PostMetrics>   (port)
     |  -> MetaMetricsFetcher (routes) -> InstagramMetricsFetcher / FacebookMetricsFetcher
     v
MetricsUpsertService.upsert(publicationId, Optional<PostMetrics>, error)   (@Transactional per call)
     v
publication_metrics  (one row per publication, upserted)

CampaignReportService  (@Transactional(readOnly=true))
     |  listCampaigns() / getCampaign(label): in-memory grouping of batches + publications + metrics
     v
GET /campaigns , GET /campaigns/detail?label= , POST /campaigns/refresh-metrics?label=
     v
CampanasTab  (3rd tab in PublicacionesPage: Publicar | Historial | Campañas)
```

## Component detail

### 1. Migration — `V103__publication_metrics.sql`

```sql
-- Engagement metrics for a published social post, pulled from the Meta Graph API.
-- One row per publication, upserted on each refresh. NULL metrics mean the platform does not
-- report that number for this post (or the fetch has not populated it yet). fetch_error holds
-- the last failure message (bad token scope, deleted post) so the UI can say "no disponible".
CREATE TABLE publication_metrics (
    publication_id UUID PRIMARY KEY REFERENCES publications (id) ON DELETE CASCADE,
    impressions BIGINT,
    reach       BIGINT,
    likes       BIGINT,
    comments    BIGINT,
    shares      BIGINT,
    saved       BIGINT,
    fetched_at  TIMESTAMPTZ NOT NULL,
    fetch_error TEXT
);
```

No backfill.

### 2. `PublicationMetricsEntity` + repository

```java
@Entity
@Table(name = "publication_metrics")
public class PublicationMetricsEntity {
    @Id
    @Column(name = "publication_id")
    private UUID publicationId;
    private Long impressions;
    private Long reach;
    private Long likes;
    private Long comments;
    private Long shares;
    private Long saved;
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
    @Column(name = "fetch_error", columnDefinition = "text")
    private String fetchError;
    // getters/setters
}
```

`interface PublicationMetricsJpaRepository extends JpaRepository<PublicationMetricsEntity, UUID>` —
plus `List<PublicationMetricsEntity> findByPublicationIdIn(Collection<UUID> ids)` for the report.

### 3. Value object — `PostMetrics`

`publication/domain/model/PostMetrics.java`:

```java
public record PostMetrics(
        Long impressions, Long reach, Long likes, Long comments, Long shares, Long saved) {
    public static PostMetrics empty() {
        return new PostMetrics(null, null, null, null, null, null);
    }
}
```

### 4. Port — `PublicationMetricsFetcher`

`publication/application/ports/PublicationMetricsFetcher.java`:

```java
public interface PublicationMetricsFetcher {
    /** Empty when the fetch failed for any reason (bad scope, deleted post, network). */
    Result fetch(PublicationPlatform platform, String externalPostId);

    record Result(Optional<PostMetrics> metrics, String error) {
        static Result ok(PostMetrics m) { return new Result(Optional.of(m), null); }
        static Result failed(String error) { return new Result(Optional.empty(), error); }
    }
}
```

### 5. `MetaMetricsFetcher` + two sub-fetchers

`infrastructure/meta/MetaMetricsFetcher.java` (`@Component`, implements the port) routes on
platform to `InstagramMetricsFetcher` / `FacebookMetricsFetcher` (package-private `@Component`s),
exactly as `MetaDirectPublicationDispatcher` routes to the two publishers. All three take
`RestClient.Builder`, `MetaPublishingConfigResolver`, `ObjectMapper`. Each sub-fetcher exposes
`PublicationMetricsFetcher.Result fetch(String externalPostId)`; `MetaMetricsFetcher.fetch`
switches on `platform` and delegates. A sub-fetcher whose credentials resolve to `null`
returns `Result.failed("<platform> credentials are not configured")`.

**`InstagramMetricsFetcher`** — base URL `config.instagramBaseUrl()`, token
`config.instagramAccessToken()`:
- `GET /{mediaId}?fields=like_count,comments_count&access_token={token}` → `likes`, `comments`.
- `GET /{mediaId}/insights?metric={metrics}&access_token={token}` → an `data[]` array of
  `{name, values:[{value}]}`. The plan verifies the exact valid metric names against Graph v23.0
  before wiring — the documented feed set is `reach`, `saved`, `shares`, `total_interactions`
  (and `views` where impressions used to be). The fetcher maps whatever comes back by `name`,
  leaving unmapped/absent metrics `null`. `impressions` maps from `views` if present, else stays
  null.
- On any exception → `Result.failed(message)`.

**`FacebookMetricsFetcher`** — base URL `config.facebookBaseUrl()`, token
`config.facebookPageAccessToken()`:
- `GET /{postId}?fields=likes.summary(true),comments.summary(true),shares&access_token={token}`
  → `likes` = `likes.summary.total_count`, `comments` = `comments.summary.total_count`,
  `shares` = `shares.count` (absent when 0 → null).
- `GET /{postId}/insights?metric=post_impressions,post_impressions_unique&access_token={token}`
  → `impressions` = `post_impressions`, `reach` = `post_impressions_unique`. Needs `read_insights`;
  a 403 here still lets the like/comment call's result stand — catch it separately so the
  summary counts survive an insights permission gap.
- On the summary call failing → `Result.failed(message)`.

`saved` is IG-only; `reach` is best-effort on both.

### 6. `MetricsUpsertService`

`publication/application/MetricsUpsertService.java` (`@Service`, `@Transactional` per call — the
non-transactional loop calls this so one bad write does not roll back the batch):

```java
@Transactional
public void upsert(UUID publicationId, PublicationMetricsFetcher.Result result, Instant now) {
    PublicationMetricsEntity e = repo.findById(publicationId).orElseGet(() -> {
        PublicationMetricsEntity n = new PublicationMetricsEntity();
        n.setPublicationId(publicationId);
        return n;
    });
    e.setFetchedAt(now);
    result.metrics().ifPresentOrElse(m -> {
        e.setImpressions(m.impressions()); e.setReach(m.reach()); e.setLikes(m.likes());
        e.setComments(m.comments()); e.setShares(m.shares()); e.setSaved(m.saved());
        e.setFetchError(null);
    }, () -> e.setFetchError(result.error()));
    repo.save(e);
}
```

On failure the previous metric values are kept (only `fetchError` + `fetchedAt` change).

### 7. `RefreshMetricsUseCase`

`publication/application/usecases/RefreshMetricsUseCase.java` (`@Component`, **not**
`@Transactional` over the loop):

```java
public sealed interface MetricsRefreshScope {
    record Campaign(String label) implements MetricsRefreshScope {}
    record RecentDays(int days) implements MetricsRefreshScope {}
}

public MetricsRefreshResult execute(MetricsRefreshScope scope) {
    List<PublicationEntity> targets = switch (scope) {
        case MetricsRefreshScope.Campaign c -> publicationRepository.findPublishedWithPostIdByCampaignLabel(c.label());
        case MetricsRefreshScope.RecentDays r -> publicationRepository
                .findPublishedWithPostIdSince(clock.instant().minus(Duration.ofDays(r.days())));
    };
    int refreshed = 0, failed = 0;
    for (PublicationEntity p : targets) {
        var result = fetcher.fetch(p.getPlatform(), p.getExternalPostId());
        metricsUpsertService.upsert(p.getId(), result, clock.instant());
        if (result.metrics().isPresent()) refreshed++; else failed++;
    }
    return new MetricsRefreshResult(refreshed, failed);
}

public record MetricsRefreshResult(int refreshed, int failed) {}
```

Injected `java.time.Clock` (package-private ctor for tests, `@Autowired` ctor passes
`Clock.systemUTC()`) — same pattern as `PublishDueScheduledPublicationsUseCase`.

New `PublicationJpaRepository` queries:
- `findPublishedWithPostIdByCampaignLabel(String label)` — `@Query` joining `publication_batches`
  on `batch_id`, `status = 'PUBLISHED'`, `external_post_id IS NOT NULL`,
  `pb.campaign_label = :label`.
- `findPublishedWithPostIdSince(Instant since)` — `status = 'PUBLISHED'`,
  `external_post_id IS NOT NULL`, `published_at >= :since`.

### 8. `RefreshRecentMetricsScheduler`

`infrastructure/jobs/RefreshRecentMetricsScheduler.java` (`@Component`), mirrors
`PublishDueScheduledPublicationsScheduler`:

```java
@Scheduled(cron = "${app.social-publishing.metrics.refresh-cron:0 0 6 * * *}")
public void run() {
    var result = useCase.execute(new MetricsRefreshScope.RecentDays(maxAgeDays));
    if (result.refreshed() + result.failed() > 0) {
        log.info("Refreshed metrics for {} posts ({} failed)", result.refreshed(), result.failed());
    }
}
```

`maxAgeDays` from `@Value("${app.social-publishing.metrics.max-age-days:30}")`.

**Config registration** — `application.yml` under `app.social-publishing.metrics`:
`refresh-cron: ${APP_SOCIAL_PUBLISHING_METRICS_REFRESH_CRON:0 0 6 * * *}`,
`max-age-days: ${APP_SOCIAL_PUBLISHING_METRICS_MAX_AGE_DAYS:30}`. Add both to
`additional-spring-configuration-metadata.json`, `infra/.env.example` (commented, defaults shown),
and `infra/docker-compose.yml` backend `environment:` block.

### 9. `CampaignReportService` + DTOs

`publication/application/CampaignReportService.java` (`@Service`, `@Transactional(readOnly = true)`).

- `listCampaigns()` → `List<CampaignSummaryDto>`:
  1. `publicationBatchRepository.findAll()` (or `findAllByOrderByCreatedAtDesc`), keep those with a
     non-blank `campaign_label`.
  2. `publicationRepository.findByBatchIdInOrderByCreatedAtAsc(batchIds)` → group by the batch's
     label.
  3. `publicationMetricsJpaRepository.findByPublicationIdIn(allPublicationIds)` → map by
     publicationId.
  4. Per label: batch count, min/max `createdAt` of the publications, total posts,
     published/failed/scheduled counts, `EnumSet<PublicationPlatform>`, `MetricsTotals` (sum each
     metric over the metrics rows, treating null as 0), count of rows whose `fetchError != null`.
- `getCampaign(String label)` → `CampaignDetailDto` with a `List<PostRow>`, one per publication in
  the campaign, ordered by `createdAt`.

```java
public record CampaignSummaryDto(
        String label, Instant firstPostAt, Instant lastPostAt, int batchCount, int totalPosts,
        int published, int failed, int scheduled, Set<PublicationPlatform> platforms,
        MetricsTotals totals, int postsWithError) {}

public record MetricsTotals(long impressions, long reach, long likes, long comments,
                            long shares, long saved) {}

public record CampaignDetailDto(String label, Instant firstPostAt, Instant lastPostAt,
                                List<PostRow> posts) {
    public record PostRow(
            UUID publicationId, UUID productId, String productName, String thumbnailUrl,
            PublicationPlatform platform, PublicationStatus status, String externalPermalink,
            PostMetrics metrics, String fetchError, Instant fetchedAt) {}
}
```

`productName` / `thumbnailUrl` resolved from `productRepository.findAllByIds(...)` like
`PublicationService.getBatch` does. `metrics` is `null` when there is no `publication_metrics` row
yet.

### 10. `PublicationController` endpoints

```java
@GetMapping("/campaigns")
@PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(...PermissionRegistry).PUBLICATIONS_READ)")
public List<CampaignSummaryDto> campaigns() { return campaignReportService.listCampaigns(); }

@GetMapping("/campaigns/detail")
@PreAuthorize(... PUBLICATIONS_READ)
public CampaignDetailDto campaignDetail(@RequestParam String label) {
    return campaignReportService.getCampaign(label);
}

@PostMapping("/campaigns/refresh-metrics")
@PreAuthorize(... PUBLICATIONS_UPDATE)
public RefreshMetricsUseCase.MetricsRefreshResult refreshCampaignMetrics(@RequestParam String label) {
    return refreshMetricsUseCase.execute(new MetricsRefreshScope.Campaign(label));
}
```

`label` is a URL-encoded query param (free text — spaces, accents). `getCampaign` on an unknown
label returns an empty `CampaignDetailDto` (empty `posts`), not a 404 — a campaign is not a
first-class entity.

### 11. Frontend — `api.ts`

```ts
export interface PostMetricsDto {
  impressions: number | null; reach: number | null; likes: number | null;
  comments: number | null; shares: number | null; saved: number | null;
}
export interface MetricsTotals {
  impressions: number; reach: number; likes: number; comments: number; shares: number; saved: number;
}
export interface CampaignSummary {
  label: string; firstPostAt: string; lastPostAt: string; batchCount: number; totalPosts: number;
  published: number; failed: number; scheduled: number;
  platforms: Array<'INSTAGRAM' | 'FACEBOOK'>; totals: MetricsTotals; postsWithError: number;
}
export interface CampaignPostRow {
  publicationId: string; productId: string | null; productName: string; thumbnailUrl: string | null;
  platform: 'INSTAGRAM' | 'FACEBOOK'; status: string; externalPermalink: string | null;
  metrics: PostMetricsDto | null; fetchError: string | null; fetchedAt: string | null;
}
export interface CampaignDetail {
  label: string; firstPostAt: string; lastPostAt: string; posts: CampaignPostRow[];
}
export async function getCampaigns(): Promise<CampaignSummary[]>;
export async function getCampaignDetail(label: string): Promise<CampaignDetail>;
export async function refreshCampaignMetrics(label: string): Promise<{ refreshed: number; failed: number }>;
```

`getCampaignDetail` / `refreshCampaignMetrics` `encodeURIComponent(label)` into the query string.

### 12. Frontend — `PublicacionesPage` + `CampanasTab`

- `PublicacionesPage`: `Tab` type gains `'campanas'`; `parseTab` maps `?tab=campanas`; the tab
  list renders `Publicar | Historial | Campañas`; `{tab === 'campanas' && <CampanasTab />}`.
- `CampanasTab.tsx` (new island):
  - On mount `getCampaigns()`. A row per campaign (collapsed): label, `firstPostAt`–`lastPostAt`
    range, "N posts · X publicados · Y fallidos", IG/FB badges, headline metrics as
    labelled numbers (Impresiones / Reach / Likes / Comentarios) formatted with
    `Intl.NumberFormat('es-CL', { notation: 'compact' })`, a "Actualizar métricas" button.
  - Expand → `getCampaignDetail(label)` → a row per post: thumbnail, product name, platform, a
    status pill (same shape as `HistorialTab`'s `StatusPill`), "Ver en Instagram/Facebook" link
    when `externalPermalink`, and the metrics inline. Metric cell states: values when
    `metrics != null`; "Sin métricas aún" when `metrics == null && fetchError == null`; "No
    disponible" (with `fetchError` as `title`) when `fetchError != null`.
  - "Actualizar métricas" → `refreshCampaignMetrics(label)` with a spinner → on resolve re-fetch
    the list and the open detail → a brief notice "Métricas actualizadas (N, N con error)".
  - Empty state: "Aún no hay campañas. Poné una etiqueta de campaña al publicar."
- Design pass with `ui-ux-pro-max` + `impeccable` before writing the markup.

## Error handling

- Any Graph failure in a sub-fetcher → `Result.failed(msg)`; the FB insights 403 is caught
  separately so like/comment counts still land.
- `RefreshMetricsUseCase` never throws for a single post — it counts it as `failed` and moves on.
- The scheduled job logs the counts; a run where every post fails (token lost all scopes) is a
  log line, not an alert (out of scope: alerting).
- `getCampaign` / `listCampaigns` never call Meta — they read local rows only, so a page load is
  never slow or dependent on Graph.
- Unknown campaign label → empty detail, HTTP 200.
- A publication with no `external_post_id` (never dispatched, or FAILED) is not selected for a
  refresh and shows "Sin métricas aún" in the detail.

## Testing

**Backend**
- `InstagramMetricsFetcherTest` / `FacebookMetricsFetcherTest` (`MockRestServiceServer`):
  parses `like_count`/`comments_count` + the insights `data[]`; an absent metric → `null`; a 5xx
  or a `text/javascript` `{"error":{"code":190}}` → `Result.failed`; FB: an insights 403 with a
  200 summary still yields likes/comments.
- `MetaMetricsFetcherTest` — routes INSTAGRAM/FACEBOOK to the right sub-fetcher.
- `MetricsUpsertServiceTest` (Mockito) — inserts a new row; updates an existing one; a failed
  result keeps old values and sets `fetchError`.
- `RefreshMetricsUseCaseTest` (Mockito, injected `Clock`) — `Campaign` scope selects by label,
  `RecentDays` selects by `published_at`; `refreshed`/`failed` counts; one fetch failure does not
  stop the loop.
- `CampaignReportServiceTest` (Mockito) — groups batches by label; `MetricsTotals` sums with
  null-as-zero; `postsWithError` counts; unknown label → empty.
- `PublicationControllerIT` — `GET /campaigns`, `GET /campaigns/detail?label=Liquidación%20primavera`,
  `POST /campaigns/refresh-metrics?label=...` (200 for admin, 403 without `publications.update`).
- `RefreshRecentMetricsSchedulerTest` — invokes the use case with a `RecentDays` scope.
- `PublicationJpaRepositoryIT` (or the existing repo IT) — the two new queries against real
  Postgres.

**Frontend**
- `CampanasTab.test.tsx` — list renders compact metric numbers; expand fetches and renders the
  detail; "Actualizar métricas" calls `refreshCampaignMetrics` and re-fetches; a post with
  `metrics == null` shows "Sin métricas aún"; a post with `fetchError` shows "No disponible";
  empty state.
- `PublicacionesPage.test.tsx` — the third tab renders and `?tab=campanas` opens it.
- `api.ts` test — `getCampaignDetail` / `refreshCampaignMetrics` URL-encode the label.

## Out of scope (H-5)

- Time-series metrics / trend charts — only the current value per post.
- Account-level metrics (follower count, profile reach).
- CSV / PDF export of a campaign report.
- Comparing campaigns against each other.
- Alerting when a refresh run fails wholesale.
- Reels / video posts.
- Editing or deleting a campaign as an entity (it is just a label on batches).

## Build order

1. V103 migration + `PublicationMetricsEntity` + `PublicationMetricsJpaRepository` + repo IT.
2. `PostMetrics` value object + `PublicationMetricsFetcher` port.
3. `InstagramMetricsFetcher` + test.
4. `FacebookMetricsFetcher` + test.
5. `MetaMetricsFetcher` router + test.
6. `MetricsUpsertService` + test.
7. `RefreshMetricsUseCase` + the two `PublicationJpaRepository` queries + test.
8. `RefreshRecentMetricsScheduler` + config keys (yml, metadata json, .env.example,
   docker-compose) + test.
9. `CampaignReportService` + DTOs + test.
10. `PublicationController` three endpoints + `PublicationControllerIT`.
11. `api.ts` types + functions + test.
12. `PublicacionesPage` third tab + `CampanasTab` + tests (design pass first).
