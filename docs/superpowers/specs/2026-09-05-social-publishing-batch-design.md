# Social Publishing — Batch Posting to Instagram + Facebook (Increment H, Etapa 1)

## Context

The `publication` backend module (product → social media post, with an approval workflow) has
existed since V65 but was never wired to a working dispatch mechanism: its only dispatcher
(`N8nPublicationWebhookDispatcher`) targets an n8n webhook that was never actually deployed —
confirmed by code inspection (no n8n service in `infra/docker-compose.yml`, no webhook URL ever
set, `APP_SOCIAL_PUBLISHING_*` absent from `infra/.env.example`, zero frontend consumers of
`/api/admin/publications`). The module is fully built on the backend and completely invisible on
the frontend.

Separately, Increment D (see memory `increment-d-meta-app`) obtained a working Instagram
Graph API token, and this session obtained a working, permanent Facebook Page access token (memory
`facebook-page-token-howto`) — both platforms now have real, usable credentials.

This increment (H, Etapa 1) connects the two: replace the dead n8n path with direct calls to the
Meta Graph APIs, and build the first admin screen that lets the shop's owner pick one or more
products and publish them to Instagram and/or Facebook in one action.

The `/admin/publicaciones` route name was freed for this in the same session (the unrelated
AI photo-drafting tool that previously lived there was renamed to `/admin/fichas-ia`).

## Scope (Etapa 1 only)

In scope:
- Pick 1+ products from the catalog, pick platform(s) (Instagram and/or Facebook, both by default),
  write one caption template with `{producto}`/`{precio}` variables, optional hashtags, optional
  campaign label.
- Preview the interpolated caption per product before publishing.
- Publish immediately (synchronous, no queue) — one `Publication` row per product×platform
  combination, independent success/failure per row.
- Direct Meta API calls from the Spring Boot backend. n8n is fully removed, not bypassed.

Out of scope (future increments, sequenced but not designed here — each gets its own brainstorm
when reached):
- **Etapa 2 — Carousel:** one post spanning several products. Needs no new table: the existing
  `publication_media_bundles.asset_manifest` (JSONB, already used to store lists of derived asset
  ids) can hold an ordered product/asset list. A `publication_items` join table was considered and
  rejected — see "Data model" below.
- **Etapa 3 — Real scheduling:** a `@Scheduled` job publishing rows in `SCHEDULED` status at
  `scheduledAt`. The `Publication` model already has both fields; only the job is missing.
- **Etapa 4 — Campaigns:** grouping/reporting UI over `campaignLabel`, which Etapa 1 already
  populates.

## Data model

**No new tables, no new enum values.** `PublicationPlatform` already has exactly `INSTAGRAM` and
`FACEBOOK`. `CreatePublicationCommand`, the `publications` table, and the approval-workflow status
machine (`DRAFT → APPROVED → PUBLISHING → PUBLISHED/FAILED`) are reused as-is.

A `publication_items` table (product_id + sort_order, for a future carousel) was proposed and then
rejected during design review: `publication_media_bundles.asset_manifest` already stores ordered
asset lists as JSONB (the existing `PublicationControllerIT` already writes
`derivedAssetIds: ["asset-1"]` into it), so the carousel capability already exists at the schema
level. An empty, unused table would be dead schema the project's own no-dead-code rule forbids.
Etapa 2's own brainstorm can revisit this if the JSONB approach proves insufficient once designed
in detail, but nothing needs to be added speculatively now.

**One migration is needed, but not for products/publications — for credentials:**

`V99__social_publishing_meta_credentials.sql` adds to `system_settings`:
- `meta_instagram_user_id` (text, nullable)
- `meta_instagram_access_token_encrypted` (text, nullable)
- `meta_facebook_page_id` (text, nullable)
- `meta_facebook_page_access_token_encrypted` (text, nullable)

Mirrors the existing `n8n_webhook_url` / `n8n_api_key_encrypted` columns (V27) in shape and intent:
an admin can rotate a token from the panel without a redeploy, which matters here because the
Instagram token is a ~60-day refreshable credential. Encrypted columns use the existing
`SystemSettingsCryptoService`, same as `MercadoPagoPaymentGatewayAdapter` and
`SocialPublishingN8nConfigResolver` already do.

## Backend architecture

### Port change: widen `PublicationWebhookDispatcher` into `PublicationDispatcher`

The current port is shaped for fire-and-forget async dispatch — its `DispatchResult` carries only
`requestId`/`payloadHash`, with the real outcome meant to arrive later via
`POST /api/publications/{id}/external-result`. A synchronous Meta call has its result immediately
and needs somewhere to put it. Rename the port and widen the result:

```java
public interface PublicationDispatcher {
    DispatchResult dispatch(UUID publicationId, String idempotencyKey, PublicationDispatchPayload payload);

    record DispatchResult(
        String requestId,
        String payloadHash,
        PublicationAttemptStatus status,      // SUCCEEDED | FAILED
        String remotePostId,                   // null on failure
        String errorCode,                      // null on success
        String errorMessage                    // null on success
    ) {}
}
```

`PublicationDispatchPayload` replaces the n8n-shaped `PublicationDispatchWebhookPayload` (which
carried `eventType` and HMAC header fields nothing else needs) with the fields a Meta adapter
actually needs: `productId`, `platform`, `channelType`, `caption`, `hashtags`, and one resolved
absolute media URL (`mediaUrl: String`, singular — Etapa 1 is one photo per post; a carousel's
multiple URLs is Etapa 2's concern, not modeled here).

### Fix: the existing dispatch/retry rollback bug

`PublicationService.dispatch` (and `retry`) are `@Transactional`. On a dispatcher exception, the
current catch block sets `FAILED` + error fields and calls `save()`, then **rethrows** — which
rolls back that same save. This is invisible today only because the n8n dispatcher never throws
(it returns a no-op result when unconfigured instead). Once real Meta HTTP calls are wired in,
4xx/429 responses become the common case, and every one of them would vanish from the database
silently.

Fix, required for this increment (not deferrable — it's the direct consequence of making dispatch
actually call something real): the dispatcher **returns** a failed `DispatchResult` instead of
throwing. `PublicationService.dispatchInternal` reads `status` from the result and persists
`PUBLISHED`+`externalPostId` or `FAILED`+`errorCode`/`errorMessage` accordingly, inside the same
transaction, with nothing left to roll back. Add a regression test that exercises this path
directly (Testing section).

### Adapter selection: concrete injection + exhaustive switch, not a lookup map

No `Map<Enum, Interface>` bean-selection pattern exists anywhere in this codebase. The existing
precedent for "one call, fan out to several enum-keyed integrations" is
`SystemSettingsNotificationSender` (services/notification-service): inject each concrete adapter,
select with a private method and an exhaustive `switch`. Follow that:

```java
@Component
class MetaDirectPublicationDispatcher implements PublicationDispatcher {
    private final InstagramGraphPublisherAdapter instagram;
    private final FacebookPagePublisherAdapter facebook;

    public DispatchResult dispatch(UUID id, String idempotencyKey, PublicationDispatchPayload payload) {
        SocialPlatformPublisher publisher = publisherFor(payload.platform());
        return publisher.publish(payload);
    }

    private SocialPlatformPublisher publisherFor(PublicationPlatform platform) {
        return switch (platform) {
            case INSTAGRAM -> instagram;
            case FACEBOOK -> facebook;
        };
    }
}
```

`SocialPlatformPublisher` is a small internal interface (`PublishResult publish(PublicationDispatchPayload payload)`)
implemented by the two adapters — not a public domain port, since only the dispatcher above calls
it. An exhaustive switch on `PublicationPlatform` makes adding a third platform without an adapter
a compile error, not a runtime NPE.

- **`InstagramGraphPublisherAdapter`** — `graph.instagram.com`, two calls: `POST /{ig-user-id}/media`
  (creates a container from the image URL + caption) → `POST /{ig-user-id}/media_publish` (publishes
  the container).
- **`FacebookPagePublisherAdapter`** — `graph.facebook.com`, one call: `POST /{page-id}/photos`
  with the image URL + caption.

Both use `RestClient` (already the project's HTTP client of choice per
`spring-boot-restclient`), configured the same way `MercadoPagoPaymentGatewayAdapter` and
`ProductAiOpenAiClient` are: `@Value`-injected fields, not `@ConfigurationProperties` (that
annotation is reserved for datasources/Kafka in this codebase).

### Config: `MetaPublishingConfigResolver`

Mirrors `SocialPublishingN8nConfigResolver` exactly: resolve each value from `system_settings`
first (decrypted via `SystemSettingsCryptoService`), falling back to env when the DB column is
null. New `app.social-publishing.meta.*` keys (env-derived form, matching the rest of the config —
not free-form `META_*`):

| Property | Env var | Default |
|---|---|---|
| `app.social-publishing.meta.instagram.user-id` | `APP_SOCIAL_PUBLISHING_META_INSTAGRAM_USER_ID` | — |
| `app.social-publishing.meta.instagram.access-token` | `APP_SOCIAL_PUBLISHING_META_INSTAGRAM_ACCESS_TOKEN` | — |
| `app.social-publishing.meta.instagram.base-url` | `APP_SOCIAL_PUBLISHING_META_INSTAGRAM_BASE_URL` | `https://graph.instagram.com/v23.0` |
| `app.social-publishing.meta.facebook.page-id` | `APP_SOCIAL_PUBLISHING_META_FACEBOOK_PAGE_ID` | — |
| `app.social-publishing.meta.facebook.page-access-token` | `APP_SOCIAL_PUBLISHING_META_FACEBOOK_PAGE_ACCESS_TOKEN` | — |
| `app.social-publishing.meta.facebook.base-url` | `APP_SOCIAL_PUBLISHING_META_FACEBOOK_BASE_URL` | `https://graph.facebook.com/v23.0` |
| `app.social-publishing.meta.public-media-base-url` | `APP_SOCIAL_PUBLISHING_META_PUBLIC_MEDIA_BASE_URL` | — (required, no safe default) |

Every key gets an entry in `additional-spring-configuration-metadata.json` (a `groups` entry for
`app.social-publishing.meta` plus one `properties` entry per key, no `defaultValue` for secrets)
and a documented (blank/example) line in `infra/.env.example` — the n8n path's absence from that
file is exactly why nobody noticed it was dead, so this must not repeat.

### Absolute media URLs

`LocalFileStorageAdapter.store` returns a relative path (`/api/media/...`); that's what's stored on
`products.image_url`. Meta fetches `image_url` itself, server-side, and requires an absolute public
HTTPS URL. The new `public-media-base-url` config resolves relative product image paths to
absolute URLs before they're sent to either adapter. If a resolved URL is still not absolute
(config missing), fail loudly with a `DomainException` naming the product — never send Meta a URL
that will 404 and come back as an opaque Meta error.

Operational note: `localhost:4321` in local dev is not reachable from Meta's servers. Testing the
real publish path end-to-end locally needs a tunnel (e.g. ngrok) pointed at the dev media path, or
must happen against the deployed environment. Unit/IT tests stub the HTTP call, so this doesn't
block automated testing — only manual end-to-end smoke testing.

### New use case: `PublishProductsBatchUseCase`

```java
public record PublishProductsBatchCommand(
    List<UUID> productIds,
    Set<PublicationPlatform> platforms,
    String captionTemplate,     // may contain {producto}, {precio}
    List<String> hashtags,
    String campaignLabel        // nullable
) {}

public record PublishProductsBatchResult(
    List<PublicationItemResult> items
) {
    public record PublicationItemResult(
        UUID productId, PublicationPlatform platform,
        boolean success, UUID publicationId, String errorMessage
    ) {}
}
```

For each `productId × platform` in the command: resolve the product, interpolate
`captionTemplate` with that product's name and formatted price, call
`PublicationService.create(...)` with `approvalRequired=false` (goes straight to `APPROVED`), then
`PublicationService.dispatch(...)`. Catch any exception **per item** and record it in the result
list rather than letting it propagate.

**Must not be `@Transactional` at the orchestrator level.** `create`/`dispatch` are separate
`@Transactional` calls on `PublicationService` (a different Spring bean, so each gets its own
transaction via the proxy). If the orchestrator itself opened one encompassing transaction, any
exception escaping an inner `@Transactional` method marks that whole transaction rollback-only —
catching it in the loop would not prevent an `UnexpectedRollbackException` at commit, and the
entire batch would be lost, exactly contradicting "each row independent." The orchestrator stays
plain (non-transactional); each `create`/`dispatch` pair commits on its own.

### Controller: one new endpoint on the existing `PublicationController`

```
POST /api/admin/publications/batch
Body: PublishProductsBatchCommand (as JSON)
Returns: 200, PublishProductsBatchResult
```

Guarded by the same permission check already on the other endpoints
(`hasRole('ADMIN') or @rbac.hasPermission(..., PUBLICATIONS_UPDATE)`) — that permission already
exists and is already granted to ADMIN; no RBAC migration needed.

### Removing n8n (full scope, not just the dispatcher)

Delete: `N8nPublicationWebhookDispatcher`, `SocialPublishingN8nConfigResolver`,
`PublicationWebhookController`, `PublicationExternalResultRequest`,
`PublicationExternalResultCommand`, `PublicationExternalResultDto`,
`PublicationService.registerExternalResult`, the n8n-shaped `PublicationDispatchWebhookPayload`
(replaced per above), the `permitAll` line for the webhook path in `SecurityConfig`, the
`app.social-publishing.n8n.*` block in `application.yml` and its metadata/`.env.example` entries,
the corresponding tests in `PublicationServiceTest`/`PublicationControllerIT`, and section 5 of
`docs/n8n-integration.md`.

**Do not touch:**
- `system_settings.n8n_webhook_url` / `n8n_api_key_encrypted` / `n8n_token_header_name` columns —
  `notification-service`'s own `N8nWebhookNotificationSender` reads these independently for an
  unrelated purpose. Dropping them breaks that service.
- `publication_attempts.workflow_run_id` column — becomes unused but a drop needs its own
  migration for zero benefit; leave it.
- `PublicationAttemptTriggerType.WEBHOOK` / `PublicationSnapshotType.OUTBOUND_WEBHOOK` enum values
  — persisted as `EnumType.STRING`; renaming needs a data migration. Verify the `publications`
  table is actually empty in every environment (very likely, given zero frontend consumers ever
  existed) before deciding whether to rename or leave them as harmless unused values.

## Frontend

**Route:** `/admin/publicaciones` (`PublicacionesPage.tsx`) — confirmed free this session (prior
occupant renamed to `/admin/fichas-ia`). Sidebar entry uses `viewKey: 'productos'`, matching how
`LegacyViewPermissionMapper` already maps that key to `publications.read` — no new permission
plumbing needed on the frontend side either.

Single-screen flow:

1. **Product picker** — search/filter input + checkbox list (thumbnail, name, price), running
   count of selected products.
2. **Platforms** — Instagram / Facebook checkboxes, both checked by default.
3. **Caption template** — textarea, with a visible legend for the two supported variables:
   `{producto}` (product name) and `{precio}` (formatted CLP price).
4. **Hashtags** — free-text input (same comma/space-separated convention as the existing
   `hashtagsJson` field), applied identically to every post in the batch.
5. **Campaign label** (optional) — free text, stored on every `Publication` row created by this
   batch for later grouping/reporting (Etapa 4).
6. **Preview** — before publishing, render each selected product's card with its *already
   interpolated* caption (not the raw template) and its catalog photo, one per platform. This is
   where a broken template (e.g. a typo'd variable) gets caught before anything goes out.
7. **"Publicar ahora"** — calls `POST /api/admin/publications/batch`. Button disabled + spinner
   while in flight (synchronous call, no polling — batch sizes here are tens of items, not
   thousands). On response, render a per-item, per-platform result list: ✓ Publicado / ✗ Falló +
   reason. Partial success is an expected, normal outcome, not an error state — the UI must show a
   mixed result clearly rather than a single pass/fail toast.

No approval workflow UI in Etapa 1 (`approvalRequired=false` always) — the preview step serves that
purpose for a single-operator shop. No scheduling UI (Etapa 3).

## Testing

- **Backend unit:**
  - `PublishProductsBatchUseCaseTest` — verifies one item failing does not affect the others'
    persisted state (direct test of the non-transactional-orchestrator requirement above).
  - `InstagramGraphPublisherAdapterTest` / `FacebookPagePublisherAdapterTest` — HTTP mocked
    (`MockRestServiceServer` or equivalent), verify outgoing request shape and error-response
    mapping to a failed `DispatchResult`.
  - A regression test on `PublicationService.dispatch` proving a failed dispatch actually persists
    `FAILED` + error fields (the rollback-bug fix from "Backend architecture" above).
- **Backend integration:** extend `PublicationControllerIT` (existing Testcontainers Postgres
  pattern) with the new batch endpoint, `@DynamicPropertySource`-wiring the Meta base URLs to a
  local stub, covering a mixed-outcome batch (one product succeeds, one fails) end to end.
- **Frontend:** `PublicacionesPage.test.tsx` (RTL) — product selection, caption template
  interpolation shown correctly in preview, submit calls the batch endpoint, mixed results render
  correctly (not collapsed into a single status).

## Roadmap after Etapa 1 (not designed here)

- **Etapa 2 — Carousel:** one post, several products, using the existing `asset_manifest` JSONB
  field noted above.
- **Etapa 3 — Scheduling:** a `@Scheduled` job publishing `SCHEDULED` rows at `scheduledAt`
  (fields already exist on `Publication`).
- **Etapa 4 — Campaigns (reporting):** dashboard grouped by `campaignLabel` (already populated by
  Etapa 1). Organic only — no ad spend, no Marketing API. Not to be confused with the paid-ads idea
  below, which despite the shared word "campaign" is a completely different feature.
- **Etapa 5 — Reels:** owner generates the video externally with an AI image-to-video tool (Kling
  AI recommended for garment/fabric motion specifically; CapCut or Runway/Luma as alternatives —
  export vertical 9:16 mp4, under 90s) and uploads it by hand as product media. Once product media
  accepts video, the publishing flow gains a `REELS` option using each platform's existing Reels
  publish endpoint (Instagram: `media_type=REELS` + `video_url` through the same two-call flow;
  Facebook Reels needs a resumable upload session, more involved than the photo flow). No in-app
  video generation — sourcing stays manual and external.
- **Meta Ads (separate future increment, not part of Increment H):** actual paid promotion via
  Meta's Marketing API — needs a connected ad account (none exists yet; Business Settings currently
  shows zero ad accounts), a billing method, budget/audience/duration UI, and spending guardrails
  since real money is at stake per launch (unlike every increment above, which is free organic
  posting). Large enough in its own right to need a full separate brainstorm — architecture,
  guardrails, and account setup — before any design work starts.

Each gets its own brainstorm before implementation, per the sequencing agreed for Increment H.
