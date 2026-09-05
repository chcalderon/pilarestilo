# Increment H, Etapa H-4b — Carousel (multi-image) social posts

**Status:** design approved in chat 2026-09-05, pending written-spec review.

**Parent:** Increment H (social publishing). H-4 was split during brainstorming:

- **H-4a (shipped)** — `Product.galleryImageUrls`, an admin gallery editor, and the SEO consumers.
- **H-4b (this spec)** — a publication posts an ordered set of images as an Instagram / Facebook
  carousel instead of the current single image. Consumes the gallery H-4a added.

Reels / video stays a separate future increment. H-5 (campaign reporting) follows this.

## Why

Every publication posts exactly one image today (`PublicationDispatchPayload.mediaUrl`, a single
`String`; both Meta adapters do a single-image call). A product now carries several photos
(`products.image_url` cover + `product_images` gallery). H-4b lets a post carry 2–10 of them as a
native IG/FB carousel, chosen per-post without touching the product.

## Global constraints

- Backend: Spring Boot 4 (Java 25), hexagonal. One use-case class per action. Jackson 3
  (`tools.jackson.databind.ObjectMapper`), never `com.fasterxml`.
- **No migration.** `publication_media_bundles.asset_manifest` is already `jsonb NOT NULL`
  (default `{}`); the ordered image list lives there. Current highest migration stays **V102**.
- The single-image path is unchanged behaviour: a `mediaUrls` list of size 1 makes each adapter
  take the exact call sequence it makes today.
- Instagram carousel limit is 2–10 items; H-4a already caps the gallery at 9 additional
  (cover + 9 = 10). The frontend never sends more than 10.
- Meta Graph API serves JSON as `Content-Type: text/javascript`; every Graph call reads
  `.body(String.class)` then `objectMapper.readTree(raw)` (the H-2/H-3 fix). New calls follow suit.
- Tests: Testcontainers + MockMvc ITs, JUnit 5 + Mockito, `MockRestServiceServer` for HTTP.
- `notification-service` maps none of the publication tables that change here (it reads `orders`,
  `users`, `payments`, `sales_documents`, `return_requests`, `system_settings`). No `*RoEntity`
  impact.
- Only `MetaDirectPublicationDispatcher` + its two adapters are in scope. The n8n dispatcher and
  the in-process `KafkaDomainEventPublisher` listeners are untouched.
- Caveman mode is chat-only; code, comments, commits, spec prose stay normal.

## Architecture

The ordered image list is carried, not derived twice:

```
PublishProductsBatchRequest.imageSelections: Map<String, List<String>>   (HTTP in; per productId)
  -> PublishProductsBatchCommand.imageSelections: Map<UUID, List<String>>
  -> BatchPublicationFactory.buildCreateCommand
       images = imageSelections.getOrDefault(productId, List.of(product.getImageUrl()))
       one MediaBundleCommand: primaryAssetUrl = images.get(0),
                               assetManifest   = Map.of("imageUrls", images)
  -> PublicationService.create -> toBundleEntity (already copies primaryAssetUrl + assetManifest)
  -> publication_media_bundles row  (asset_manifest jsonb: {"imageUrls": [...]})

  -> PublicationService.buildDispatchPayload
       imageUrls = assetManifest.get("imageUrls")  (fallback [primaryAssetUrl] for pre-H-4b rows)
  -> PublicationDispatchPayload.mediaUrls: List<String>   (replaces the singular mediaUrl)
  -> MetaDirectPublicationDispatcher.dispatch
       resolves EACH url to absolute (was: one)
  -> InstagramGraphPublisherAdapter / FacebookPagePublisherAdapter
       size 1  -> current single-image call sequence, unchanged
       size >1 -> carousel call sequence
```

## Component detail

### 1. `PublishProductsBatchCommand` + request

- `PublishProductsBatchCommand`: replace
  `Map<UUID, String> imageOverrides` with `Map<UUID, List<String>> imageSelections`. Update the
  doc comment: "Per-product ordered image list for this post (cover/override first, then any extra
  carousel images). A product missing from this map posts a single image, its catalog photo."
- `PublishProductsBatchRequest`: replace `Map<String, String> imageOverrides` with
  `Map<String, List<String>> imageSelections` (JSON object keys are strings; the controller parses
  them to `UUID`).
- `PublicationController.toBatchCommand` (line ~172, where `imageOverrides` is parsed to
  `Map<UUID, String>` today): parse `imageSelections` to `Map<UUID, List<String>>` —
  `UUID.fromString(key) -> value`, with each list filtered to non-blank trimmed entries in order,
  and an entry dropped entirely if its list ends up empty (so it falls through to the factory's
  default). `null` request field -> `Map.of()`.

### 2. `BatchPublicationFactory`

`buildCreateCommand(command, productId, product, platform, caption, batchId)` — the media bundle
construction changes from:

```java
List.of(new CreatePublicationCommand.MediaBundleCommand(
        PublicationMediaBundleType.SOCIAL_FEED,
        command.imageOverrides().getOrDefault(productId, product.getImageUrl()),
        Map.of()))
```

to:

```java
List<String> images = command.imageSelections().getOrDefault(productId, List.of(product.getImageUrl()));
// images is guaranteed non-empty: getOrDefault fallback has one element, and the controller
// drops empty lists to fall through to the default.
List.of(new CreatePublicationCommand.MediaBundleCommand(
        PublicationMediaBundleType.SOCIAL_FEED,
        images.get(0),
        Map.of("imageUrls", images)))
```

`assetManifest` is `Map<String, Object>`; `"imageUrls"` holds a `List<String>`.

### 3. `PublicationService`

- `buildDispatchPayload(entity)` (currently reads `mediaBundles.get(0).getPrimaryAssetUrl()` into a
  single `mediaUrl`): now reads the ordered list.

  ```java
  private List<String> bundleImageUrls(PublicationEntity entity) {
      if (entity.getMediaBundles().isEmpty()) {
          return List.of();
      }
      PublicationMediaBundleEntity bundle = entity.getMediaBundles().get(0);
      Object raw = bundle.getAssetManifest() == null ? null : bundle.getAssetManifest().get("imageUrls");
      if (raw instanceof List<?> list && !list.isEmpty()) {
          return list.stream().map(String::valueOf).toList();
      }
      // Pre-H-4b rows have no imageUrls key — fall back to the single primary asset.
      return bundle.getPrimaryAssetUrl() == null ? List.of() : List.of(bundle.getPrimaryAssetUrl());
  }
  ```

  `buildDispatchPayload` passes `bundleImageUrls(entity)` as `mediaUrls`.

- `getBatch(...)` `Row` construction (line ~209) — add `imageUrls` per row: the rows are
  `PublicationEntity` instances, so call the same `bundleImageUrls(r)` helper. `thumbnailUrl`
  stays `p.getImageUrl()`.

- `buildContentSnapshot` already records `mediaBundleCount`; no change.

### 4. `PublicationDispatchPayload`

```java
public record PublicationDispatchPayload(
        UUID productId,
        PublicationPlatform platform,
        PublicationChannelType channelType,
        String caption,
        List<String> hashtags,
        List<String> mediaUrls          // was: String mediaUrl
) {
    public String fullCaptionText() { /* unchanged */ }
}
```

`mediaUrls` is never empty when it reaches an adapter (the dispatcher throws on an empty/unresolvable
list, same as today's single-url check). Three construction sites change: `buildDispatchPayload`,
`MetaDirectPublicationDispatcher.dispatch` (the re-wrapped payload), and every test that builds one.

### 5. `MetaDirectPublicationDispatcher`

`dispatch(...)` resolves each url:

```java
List<String> absolute = payload.mediaUrls().stream()
        .map(u -> resolveAbsoluteUrl(u, base))
        .toList();
```

`resolveAbsoluteUrl` is unchanged (still throws `DomainException` on a blank url or a missing
`public-media-base-url`). An empty `mediaUrls` throws "Cannot dispatch publication without a media
URL" (move the guard to before the stream).

### 6. `InstagramGraphPublisherAdapter` — carousel

Extract the container-build step. `publish(payload)`:

```java
String creationId = buildPublishableContainer(client, config, payload.mediaUrls(), payload.fullCaptionText());
JsonNode published = postJson(client, "/{userId}/media_publish?creation_id={creationId}&access_token={token}",
        config.instagramUserId(), creationId, config.instagramAccessToken());
// ... remotePostId + permalink, unchanged
```

```java
private String buildPublishableContainer(RestClient client, EffectiveConfig config,
                                         List<String> mediaUrls, String caption) {
    if (mediaUrls.size() == 1) {
        JsonNode created = postJson(client,
                "/{userId}/media?image_url={imageUrl}&caption={caption}&access_token={token}",
                config.instagramUserId(), mediaUrls.get(0), caption, config.instagramAccessToken());
        String id = created.hasNonNull("id") ? created.get("id").asString() : null;
        if (id == null) throw new IllegalStateException("Instagram did not return a media container id");
        awaitContainerReady(client, id, config.instagramAccessToken());
        return id;
    }
    List<String> childIds = new ArrayList<>();
    for (String url : mediaUrls) {
        JsonNode child = postJson(client,
                "/{userId}/media?image_url={imageUrl}&is_carousel_item=true&access_token={token}",
                config.instagramUserId(), url, config.instagramAccessToken());
        String childId = child.hasNonNull("id") ? child.get("id").asString() : null;
        if (childId == null) throw new IllegalStateException("Instagram did not return a carousel child container id");
        awaitContainerReady(client, childId, config.instagramAccessToken());
        childIds.add(childId);
    }
    JsonNode parent = postJson(client,
            "/{userId}/media?media_type=CAROUSEL&caption={caption}&children={children}&access_token={token}",
            config.instagramUserId(), caption, String.join(",", childIds), config.instagramAccessToken());
    String parentId = parent.hasNonNull("id") ? parent.get("id").asString() : null;
    if (parentId == null) throw new IllegalStateException("Instagram did not return a carousel parent container id");
    awaitContainerReady(client, parentId, config.instagramAccessToken());
    return parentId;
}
```

`awaitContainerReady`, `postJson`, `getJson`, the poll-interval config and the `failed(...)` /
`catch (RuntimeException)` wrapping are all unchanged from H-3.

### 7. `FacebookPagePublisherAdapter` — carousel

`publish(payload)` branches on `payload.mediaUrls().size()`:

```java
if (payload.mediaUrls().size() == 1) {
    // current path: POST /{pageId}/photos?url=&caption=&access_token=  -> {post_id, id}
    // remotePostId = post_id ?? id ;  permalink = post_id == null ? null : "https://www.facebook.com/" + post_id
}
```

```java
// carousel:
List<String> photoIds = new ArrayList<>();
for (String url : payload.mediaUrls()) {
    JsonNode photo = parse(client.post()
            .uri("/{pageId}/photos?url={url}&published=false&access_token={token}",
                    config.facebookPageId(), url, config.facebookPageAccessToken())
            .retrieve().body(String.class));
    String photoId = photo.hasNonNull("id") ? photo.get("id").asString() : null;
    if (photoId == null) throw new IllegalStateException("Facebook did not return an unpublished photo id");
    photoIds.add(photoId);
}
String attachedMedia = objectMapper.writeValueAsString(
        photoIds.stream().map(id -> Map.of("media_fbid", id)).toList());
JsonNode feed = parse(client.post()
        .uri("/{pageId}/feed?message={message}&attached_media={attachedMedia}&access_token={token}",
                config.facebookPageId(), payload.fullCaptionText(), attachedMedia, config.facebookPageAccessToken())
        .retrieve().body(String.class));
String postId = feed.hasNonNull("id") ? feed.get("id").asString() : null;   // "{pageId}_{postId}" form
// remotePostId = postId ;  permalink = postId == null ? null : "https://www.facebook.com/" + postId
```

`parse(...)` is the shared helper (`raw == null || blank ? createObjectNode() : objectMapper.readTree(raw)`).
RestClient URL-encodes each `{var}`, so the JSON `attachedMedia` string is safe as a query param.

### 8. `PublicationMediaBundleDto` / `PublicationDto` — no shape change

`PublicationMediaBundleDto` already carries `assetManifest` (a `Map<String, Object>`), so
`{"imageUrls": [...]}` reaches any DTO consumer with no contract change. `toDto`'s media-bundle
mapping is untouched.

### 9. `PublicationBatchDetailDto.Row` — add `imageUrls`

```java
public record Row(
        UUID publicationId, UUID productId, String productName, String thumbnailUrl,
        PublicationPlatform platform, PublicationStatus status, String externalPermalink,
        String lastErrorCode, String lastErrorMessage,
        List<String> imageUrls           // NEW, from the publication's own bundle
) {}
```

Populated in `getBatch(...)` from the row entity's bundle (the `bundleImageUrls`-style helper).
Frontend `PublicationBatchDetailRow` gains `imageUrls: string[]`.

### 10. Frontend — `api.ts`

- `PublishProductsBatchRequest`: `imageOverrides?: Record<string, string>` becomes
  `imageSelections?: Record<string, string[]>`.
- `publishProductsBatch` and `updateScheduledBatch` bodies forward `imageSelections` unchanged
  (both already spread the request object).
- `PublicationBatchDetailRow`: add `imageUrls: string[]`.

### 11. Frontend — `PublicarTab.tsx`

State per selected product:
- Keep `imageOverrides: Map<string, string>` — the first/cover image for this post (set by the
  existing "subir foto editada" upload and the "reusar del historial" buttons).
- Add `carouselProducts: Set<string>`.

Per-product row:
- A "Carrusel" toggle (checkbox), **shown only when `(product.galleryImageUrls ?? []).length > 0`**.
  When the gallery is empty, show the hint "Agregá fotos a la galería del producto para hacer
  carrusel" and no toggle.
- Preview: when the toggle is on, render a thumbnail strip of
  `[firstImage(product), ...(product.galleryImageUrls ?? [])]` with the label "Carrusel · N fotos";
  otherwise the single thumbnail as today. `firstImage(product) = imageOverrides.get(id) ?? product.imageUrl`.

Submit — build `imageSelections: Record<string, string[]>` for every selected product:
```ts
const first = imageOverrides.get(p.id) ?? p.imageUrl;
imageSelections[p.id] = carouselProducts.has(p.id)
  ? [first, ...(p.galleryImageUrls ?? [])]
  : [first];
```
Replace the current `imageOverrides` field in the request body with this `imageSelections`.

Edit-scheduled preload (`editScheduled` in `PublicacionesPage` -> `PublicarTab`): from each detail
row's `imageUrls`, reconstruct
- `carouselProducts`: add the productId when `imageUrls.length > 1`;
- `imageOverrides`: set `imageUrls[0]` when it differs from the product's current `imageUrl`.

Republish ("editar y volver a publicar"): unchanged — it does not preload images, so the compose
form re-derives `[cover, ...gallery]` from the product's current state.

### 12. Frontend — `HistorialTab.tsx`

When an expanded batch's row has `imageUrls.length > 1`, show "Carrusel · N" next to the platform
badge (small, `text-pe-muted`). Single-image rows are unchanged.

## Error handling

- Any child-container / photo-upload failure mid-carousel throws -> the publication ends `FAILED`
  with the adapter's error code, retryable. No partial-post state: IG child containers expire in
  24 h and a retry rebuilds from scratch; FB `published=false` photos linger harmlessly and a
  retry re-uploads.
- An empty `mediaUrls` at dispatch throws `DomainException` "Cannot dispatch publication without a
  media URL" (guard moved ahead of the resolve stream).
- Pre-H-4b publication rows (no `imageUrls` in `asset_manifest`) fall back to `[primaryAssetUrl]`
  everywhere the list is read — `RetryFailedBatchUseCase` on an old FAILED row still works.
- A gallery URL that 404s when Meta fetches it: Meta returns an error on that child/photo call ->
  `FAILED`, retryable. Not pre-validated (same as the single-image path today).

## Testing

**Backend**
- `BatchPublicationFactoryTest` — `imageSelections` with a 3-url list -> one `MediaBundleCommand`,
  `primaryAssetUrl == list[0]`, `assetManifest.get("imageUrls")` equals the list; a productId
  absent from the map -> `[product.getImageUrl()]`.
- `PublicationServiceTest` — `buildDispatchPayload` returns `mediaUrls` from
  `assetManifest.imageUrls`; a bundle with an empty/absent manifest -> `[primaryAssetUrl]`.
- `MetaDirectPublicationDispatcherTest` — a payload with two relative urls -> both resolved to
  absolute and passed through; existing single-url + already-absolute + no-base tests still pass
  with the list shape.
- `InstagramGraphPublisherAdapterTest` — carousel: expects N
  `/media?...&is_carousel_item=true` calls each followed by a `?fields=status_code` poll, then one
  `/media?media_type=CAROUSEL&...&children=` call + poll, then `/media_publish`; asserts the
  caption is on the parent call, not the children; a child returning 500 -> `FAILED`
  `INSTAGRAM_PUBLISH_ERROR`; the single-image tests are updated only for the `mediaUrls` list shape
  and otherwise assert the identical call sequence.
- `FacebookPagePublisherAdapterTest` — carousel: N `/photos?...&published=false` calls, then one
  `/feed?...&attached_media=` call whose param decodes to `[{"media_fbid":...}, ...]` in order;
  `remotePostId` and permalink come from the feed response `id`; a photo upload 500 -> `FAILED`;
  single-image tests keep the `/photos` path.
- `PublicationControllerIT` — `POST /admin/publications/batch` with an `imageSelections` entry of
  two urls; `GET /batches/{id}` returns rows whose `imageUrls` has both.

**Frontend**
- `PublicarTab.test.tsx` — the carousel toggle is absent for a product with no gallery and present
  for one with a gallery; toggling it on renders an N-thumbnail preview; submit sends
  `imageSelections` with `[first, ...gallery]` for the toggled product and `[first]` for an
  untoggled one; "reusar del historial" still sets the first image.
- `HistorialTab.test.tsx` — a detail row with `imageUrls.length > 1` shows "Carrusel · N".
- an `api.ts` test — `publishProductsBatch` forwards `imageSelections` in the body.

## Out of scope (H-4b)

- Reels / video posts — separate future increment.
- Per-post reordering of the carousel images — order is the product gallery's (H-4a); a post can
  only drop the trailing images (toggle off) or override the first.
- A single post spanning multiple products.
- Rendering the carousel on the storefront.
- The n8n dispatcher and the Kafka listener transport adapters.
- Backfilling `asset_manifest.imageUrls` onto historical publication rows.

## Build order

1. `PublicationDispatchPayload.mediaUrls` + `MetaDirectPublicationDispatcher` (resolve each url) +
   fix the three construction sites and their tests.
2. `PublishProductsBatchCommand`/`Request` `imageSelections` + controller parse + factory bundle
   construction + `BatchPublicationFactoryTest`.
3. `PublicationService.buildDispatchPayload` reads `assetManifest.imageUrls` + `PublicationServiceTest`.
4. `InstagramGraphPublisherAdapter` carousel + test.
5. `FacebookPagePublisherAdapter` carousel + test.
6. `PublicationBatchDetailDto.Row.imageUrls` + `getBatch` + `PublicationControllerIT`.
7. `api.ts` types + `PublicarTab` toggle/preview/submit + edit-scheduled preload + tests.
8. `HistorialTab` "Carrusel · N" + test.
