# Carousel (Multi-Image) Social Posts — Implementation Plan (H-4b)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a publication post an ordered set of 2–10 images as a native Instagram / Facebook carousel instead of a single image, chosen per-post without touching the product.

**Architecture:** The ordered image list is carried, not re-derived: the frontend builds `imageSelections: {productId: [url, ...]}`, the batch command carries it, `BatchPublicationFactory` stores the list in the single media bundle's `assetManifest.imageUrls` (jsonb, no migration), `PublicationService.buildDispatchPayload` reads it into `PublicationDispatchPayload.mediaUrls: List<String>`, `MetaDirectPublicationDispatcher` resolves each URL to absolute, and each Meta adapter branches: size 1 → its exact current call sequence; size > 1 → a carousel sequence (IG: child containers + a CAROUSEL parent; FB: unpublished photos + a `/feed` post with `attached_media`).

**Tech Stack:** Spring Boot 4 (Java 25), JPA/Hibernate, `RestClient`, Jackson 3; Testcontainers + MockMvc ITs, JUnit 5 + Mockito, `MockRestServiceServer`; Astro 5 SSR + React islands, Vitest + React Testing Library.

**Spec:** `docs/superpowers/specs/2026-09-05-social-publishing-h4b-carousel-design.md`

## Global Constraints

- Hexagonal: one use-case class per action; domain objects carry no framework annotations.
- **No migration.** `publication_media_bundles.asset_manifest` is already `jsonb NOT NULL` (default `{}`); the list lives there. Current highest migration stays **V102**.
- The single-image path is unchanged behaviour: a `mediaUrls` list of size 1 makes each adapter take the exact call sequence it makes today.
- Instagram carousel limit is 2–10 items; H-4a caps the gallery at 9 additional (cover + 9 = 10). The frontend never sends more than 10.
- Jackson 3: `tools.jackson.databind.ObjectMapper` / `JsonNode`, never `com.fasterxml`.
- Every Meta Graph call reads `.body(String.class)` then `objectMapper.readTree(raw)` (Graph serves JSON as `Content-Type: text/javascript`).
- Only `MetaDirectPublicationDispatcher` + `InstagramGraphPublisherAdapter` + `FacebookPagePublisherAdapter` are in scope. The n8n dispatcher and Kafka listener adapters are untouched.
- `notification-service` maps none of these tables — no `*RoEntity` change.
- Backend test: `cd backend && mvn test -Dtest=<Class>`. Frontend: `cd frontend && npx vitest run <path>` and `cd frontend && ./node_modules/.bin/tsc --noEmit` (not `npx tsc`).
- Caveman mode is chat-only; code, comments, commits, spec prose stay normal.

---

## Task 1: `PublicationDispatchPayload.mediaUrls` + dispatcher resolves each URL

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationDispatchPayload.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/MetaDirectPublicationDispatcher.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapter.java:56` (mechanical: `payload.mediaUrl()` → `payload.mediaUrls().get(0)`)
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapter.java:41` (mechanical, same)
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java:462-472` (`buildDispatchPayload` builds a 1-element list for now — Task 3 makes it read the manifest)
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/MetaDirectPublicationDispatcherTest.java`

**Interfaces:**
- Produces: `PublicationDispatchPayload` record's last component is `List<String> mediaUrls` (was `String mediaUrl`). `fullCaptionText()` unchanged. Never empty when it reaches an adapter.

- [ ] **Step 1: Update the failing test**

In `MetaDirectPublicationDispatcherTest.java`: the 3 `new PublicationDispatchPayload(...)` calls (lines ~45, ~66, ~84) currently pass a `String` last arg; change each to `List.of(...)`. The 2 assertions `captor.getValue().mediaUrl()` (lines ~55, ~75) become `captor.getValue().mediaUrls().get(0)`. Then add:

```java
    @Test
    void resolves_every_relative_url_in_the_list_to_absolute() {
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "ig-user", "ig-token", "https://graph.instagram.com/v23.0", null, null,
                "https://graph.facebook.com/v23.0", "https://pilarestilo.com"));
        when(instagram.publish(any())).thenReturn(new PublicationDispatcher.DispatchResult(
                "req-1", null, PublicationAttemptStatus.SUCCEEDED, "post-1", null, null, null));

        PublicationDispatchPayload payload = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "Caption", List.of(), List.of("/api/media/products/a.jpg", "https://cdn.example.com/b.jpg"));

        dispatcher.dispatch(UUID.randomUUID(), "idem-1", payload);

        org.mockito.ArgumentCaptor<PublicationDispatchPayload> captor =
                org.mockito.ArgumentCaptor.forClass(PublicationDispatchPayload.class);
        org.mockito.Mockito.verify(instagram).publish(captor.capture());
        assertEquals(
                List.of("https://pilarestilo.com/api/media/products/a.jpg", "https://cdn.example.com/b.jpg"),
                captor.getValue().mediaUrls());
    }
```

(Add `import java.util.List;` if missing.)

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=MetaDirectPublicationDispatcherTest`
Expected: compile failure — `PublicationDispatchPayload` still has `String mediaUrl`.

- [ ] **Step 3: Change the record**

`PublicationDispatchPayload.java`:

```java
public record PublicationDispatchPayload(
        UUID productId,
        PublicationPlatform platform,
        PublicationChannelType channelType,
        String caption,
        List<String> hashtags,
        List<String> mediaUrls
) {
    public String fullCaptionText() {
        String base = caption == null ? "" : caption.trim();
        if (hashtags == null || hashtags.isEmpty()) {
            return base;
        }
        String tags = String.join(" ", hashtags);
        return base.isEmpty() ? tags : base + "\n\n" + tags;
    }
}
```

- [ ] **Step 4: Update `MetaDirectPublicationDispatcher`**

```java
    @Override
    public DispatchResult dispatch(UUID publicationId, String idempotencyKey, PublicationDispatchPayload payload) {
        if (payload.mediaUrls() == null || payload.mediaUrls().isEmpty()) {
            throw new DomainException("Cannot dispatch publication without a media URL");
        }
        String base = configResolver.resolve().publicMediaBaseUrl();
        List<String> absolute = payload.mediaUrls().stream()
                .map(u -> resolveAbsoluteUrl(u, base))
                .toList();
        PublicationDispatchPayload resolvedPayload = new PublicationDispatchPayload(
                payload.productId(), payload.platform(), payload.channelType(),
                payload.caption(), payload.hashtags(), absolute);
        return publisherFor(payload.platform()).publish(resolvedPayload);
    }
```

`resolveAbsoluteUrl` keeps its blank-url / missing-base `DomainException` checks; drop only the now-redundant `mediaUrl == null || isBlank` early branch is fine to leave — a blank entry mid-list should still throw. Add `import java.util.List;`.

- [ ] **Step 5: Mechanical adapter + `buildDispatchPayload` updates (compile only, no behaviour change)**

- `InstagramGraphPublisherAdapter.java:56` — `payload.mediaUrl()` → `payload.mediaUrls().get(0)`.
- `FacebookPagePublisherAdapter.java:41` — `payload.mediaUrl()` → `payload.mediaUrls().get(0)`.
- `PublicationService.buildDispatchPayload` (line ~464) — last arg:
  `bundle == null ? List.of() : List.of(bundle.getPrimaryAssetUrl())` (Task 3 replaces this).
  Add `import java.util.List;` if not present.

- [ ] **Step 6: Update the adapter test payload fields**

`InstagramGraphPublisherAdapterTest.java:27` and `FacebookPagePublisherAdapterTest.java:24` — the shared `private final PublicationDispatchPayload payload = new PublicationDispatchPayload(...)` — change the last arg from `"https://cdn.example.com/chaqueta.jpg"` to `List.of("https://cdn.example.com/chaqueta.jpg")`. (Both files already `import java.util.List;`.)

- [ ] **Step 7: Run the affected tests, verify they pass**

Run: `cd backend && mvn test -Dtest='MetaDirectPublicationDispatcherTest,InstagramGraphPublisherAdapterTest,FacebookPagePublisherAdapterTest,PublicationServiceTest'`
Expected: PASS (all existing + the 1 new dispatcher test).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationDispatchPayload.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/MetaDirectPublicationDispatcher.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapter.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapter.java \
        backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/
git commit -m "refactor(publication): PublicationDispatchPayload carries a mediaUrls list"
```

---

## Task 2: `imageSelections` through command → request → controller → factory

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/commands/PublishProductsBatchCommand.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/requests/PublishProductsBatchRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/controllers/PublicationController.java:172-197` (`toBatchCommand`)
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/usecases/BatchPublicationFactory.java:87-100` (`buildCreateCommand`)
- Test: `backend/src/test/java/com/pilarestilo/publication/application/usecases/BatchPublicationFactoryTest.java`
- Test fixups: `backend/src/test/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCaseTest.java`, `.../UpdateScheduledBatchUseCaseTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `PublishProductsBatchCommand` — component 6 is `Map<UUID, List<String>> imageSelections` (was `Map<UUID, String> imageOverrides`). Positional order otherwise unchanged (8 components).
  - `PublishProductsBatchRequest` — `Map<String, List<String>> imageSelections` (was `Map<String, String> imageOverrides`).
  - `BatchPublicationFactory.buildCreateCommand` unchanged signature; its one `MediaBundleCommand` now has `assetManifest = Map.of("imageUrls", images)` and `primaryAssetUrl = images.get(0)`.

- [ ] **Step 1: Write the failing test**

Replace `BatchPublicationFactoryTest.build_create_command_threads_scheduled_at_and_batch_id` and add a gallery case. The existing test builds `new PublishProductsBatchCommand(List.of(product.getId()), Set.of(INSTAGRAM), "{producto}", List.of("#x"), "Camp", Map.of(), Map.of(), when)` — the 6th arg `Map.of()` stays valid (empty map). Add:

```java
    @Test
    void build_create_command_stores_the_image_selection_list_in_the_manifest() {
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img/cover.jpg", ProductCondition.NEW, "Pilar", 5);
        UUID batchId = UUID.randomUUID();
        PublishProductsBatchCommand cmd = new PublishProductsBatchCommand(
                List.of(product.getId()), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of("#x"), "Camp",
                Map.of(product.getId(), List.of("https://img/cover.jpg", "https://img/2.jpg", "https://img/3.jpg")),
                Map.of(), null);

        CreatePublicationCommand out = factory.buildCreateCommand(
                cmd, product.getId(), product, PublicationPlatform.INSTAGRAM, "Zapatos", batchId);

        var bundle = out.mediaBundles().get(0);
        assertEquals("https://img/cover.jpg", bundle.primaryAssetUrl());
        assertEquals(
                List.of("https://img/cover.jpg", "https://img/2.jpg", "https://img/3.jpg"),
                bundle.assetManifest().get("imageUrls"));
    }

    @Test
    void build_create_command_falls_back_to_the_product_cover_when_no_selection() {
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img/cover.jpg", ProductCondition.NEW, "Pilar", 5);
        PublishProductsBatchCommand cmd = new PublishProductsBatchCommand(
                List.of(product.getId()), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of("#x"), "Camp", Map.of(), Map.of(), null);

        CreatePublicationCommand out = factory.buildCreateCommand(
                cmd, product.getId(), product, PublicationPlatform.INSTAGRAM, "Zapatos", UUID.randomUUID());

        assertEquals("https://img/cover.jpg", out.mediaBundles().get(0).primaryAssetUrl());
        assertEquals(List.of("https://img/cover.jpg"), out.mediaBundles().get(0).assetManifest().get("imageUrls"));
    }
```

In the existing `build_create_command_threads_scheduled_at_and_batch_id`, update its assertion `assertEquals("https://img/z.jpg", out.mediaBundles().get(0).primaryAssetUrl());` — still true — and note `assetManifest` now has an `imageUrls` key (no assertion needed there).

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=BatchPublicationFactoryTest`
Expected: compile failure — `PublishProductsBatchCommand`'s 6th arg is still `Map<UUID, String>`, so `Map.of(id, List.of(...))` won't compile.

- [ ] **Step 3: Change the command record**

`PublishProductsBatchCommand.java` — replace the `imageOverrides` component:

```java
        /** Per-product ordered image list for this post: cover/override first, then any extra
         *  carousel images. A product missing from this map posts a single image, its catalog
         *  photo as-is. */
        Map<UUID, List<String>> imageSelections,
```

- [ ] **Step 4: Change the request record**

`PublishProductsBatchRequest.java`:

```java
        /** Per-product ordered image list, keyed by productId (string keys — JSON objects can't
         *  key by UUID). */
        Map<String, List<String>> imageSelections,
```

- [ ] **Step 5: `PublicationController.toBatchCommand`**

Replace the `imageOverrides` block (lines ~176-179):

```java
        Map<UUID, List<String>> imageSelections = request.imageSelections() == null
                ? Map.of()
                : request.imageSelections().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                e -> UUID.fromString(e.getKey()),
                                e -> e.getValue().stream()
                                        .filter(java.util.Objects::nonNull)
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty())
                                        .toList()))
                .entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
```

and pass `imageSelections` (instead of `imageOverrides`) as the 6th arg of `new PublishProductsBatchCommand(...)`.

- [ ] **Step 6: `BatchPublicationFactory.buildCreateCommand`**

The `List.of(new CreatePublicationCommand.MediaBundleCommand(...))` block (line ~96) becomes:

```java
        List<String> images = command.imageSelections().getOrDefault(productId, List.of(product.getImageUrl()));
        return new CreatePublicationCommand(
                productId,
                PublicationSourceType.PRODUCT,
                productId,
                platform,
                PublicationChannelType.FEED_POST,
                "es-CL",
                command.campaignLabel(),
                caption,
                command.hashtags(),
                false,
                command.scheduledAt(),
                "pub-batch-" + productId + "-" + platform.name() + "-" + UUID.randomUUID(),
                List.of(new CreatePublicationCommand.MediaBundleCommand(
                        PublicationMediaBundleType.SOCIAL_FEED,
                        images.get(0),
                        Map.of("imageUrls", images))),
                batchId);
```

(The `command.imageOverrides().getOrDefault(productId, product.getImageUrl())` line is gone.)

- [ ] **Step 7: Fix the other test construction sites**

`PublishProductsBatchUseCaseTest.java` — all `new PublishProductsBatchCommand(...)` calls pass `Map.of()` for the 6th arg except line ~185: `Map.of(productId, "https://cdn.example.com/edited.jpg")` → `Map.of(productId, List.of("https://cdn.example.com/edited.jpg"))`. Its assertion at line ~192 (`captor.getValue().mediaBundles().get(0).primaryAssetUrl()` equals the edited URL) stays true.

`UpdateScheduledBatchUseCaseTest.java` line ~71 — `Map.of()` 6th arg stays valid.

- [ ] **Step 8: Run the tests, verify they pass**

Run: `cd backend && mvn test -Dtest='BatchPublicationFactoryTest,PublishProductsBatchUseCaseTest,UpdateScheduledBatchUseCaseTest,PublicationControllerIT,RetryFailedBatchUseCaseTest'`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/commands/PublishProductsBatchCommand.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/web/requests/PublishProductsBatchRequest.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/web/controllers/PublicationController.java \
        backend/src/main/java/com/pilarestilo/publication/application/usecases/BatchPublicationFactory.java \
        backend/src/test/java/com/pilarestilo/publication/application/usecases/
git commit -m "feat(publication): imageSelections list per product, stored in the bundle manifest"
```

---

## Task 3: `buildDispatchPayload` reads `assetManifest.imageUrls`

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java` (`buildDispatchPayload` ~line 462; add a private `bundleImageUrls(PublicationEntity)` helper)
- Test: `backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java`

**Interfaces:**
- Consumes: `PublicationDispatchPayload.mediaUrls` (Task 1); `assetManifest.get("imageUrls")` written by Task 2.
- Produces: `private List<String> PublicationService.bundleImageUrls(PublicationEntity entity)` — the ordered list from bundle[0]'s `assetManifest.imageUrls`, falling back to `[primaryAssetUrl]`, or `[]` when there is no bundle.

- [ ] **Step 1: Write the failing test**

In `PublicationServiceTest.java`, extend `approvedPublication` usage. Add a helper and a test:

```java
    private static PublicationMediaBundleEntity bundleWithManifest(String primary, java.util.List<String> imageUrls) {
        PublicationMediaBundleEntity b = new PublicationMediaBundleEntity();
        b.setId(UUID.randomUUID());
        b.setBundleType(PublicationMediaBundleType.SOCIAL_FEED);
        b.setPrimaryAssetUrl(primary);
        b.setAssetManifest(imageUrls == null ? java.util.Map.of() : java.util.Map.of("imageUrls", imageUrls));
        b.setRenderStatus(com.pilarestilo.publication.domain.enums.PublicationMediaRenderStatus.READY);
        b.setCreatedAt(Instant.now());
        b.setUpdatedAt(Instant.now());
        return b;
    }

    @Test
    void dispatch_passes_the_full_image_list_from_the_manifest_to_the_dispatcher() {
        UUID publicationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, productId);
        entity.setMediaBundles(new java.util.ArrayList<>(java.util.List.of(
                bundleWithManifest("https://img/a.jpg", java.util.List.of("https://img/a.jpg", "https://img/b.jpg")))));
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                Product.create("Chaqueta", "d", new Money(BigDecimal.valueOf(49990), "CLP"),
                        "https://img", ProductCondition.NEW, "Pilar", 2)));
        when(publicationDispatcher.dispatch(any(), anyString(), any()))
                .thenReturn(new PublicationDispatcher.DispatchResult(
                        "req-1", null, PublicationAttemptStatus.SUCCEEDED, "remote-1", null, null, null));

        service.dispatch(publicationId, UUID.randomUUID());

        org.mockito.ArgumentCaptor<PublicationDispatchPayload> captor =
                org.mockito.ArgumentCaptor.forClass(PublicationDispatchPayload.class);
        verify(publicationDispatcher).dispatch(any(), anyString(), captor.capture());
        assertEquals(java.util.List.of("https://img/a.jpg", "https://img/b.jpg"), captor.getValue().mediaUrls());
    }

    @Test
    void dispatch_falls_back_to_the_primary_url_when_the_manifest_has_no_image_list() {
        UUID publicationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(publicationId, productId);
        entity.setMediaBundles(new java.util.ArrayList<>(java.util.List.of(
                bundleWithManifest("https://img/only.jpg", null))));
        when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                Product.create("Chaqueta", "d", new Money(BigDecimal.valueOf(49990), "CLP"),
                        "https://img", ProductCondition.NEW, "Pilar", 2)));
        when(publicationDispatcher.dispatch(any(), anyString(), any()))
                .thenReturn(new PublicationDispatcher.DispatchResult(
                        "req-1", null, PublicationAttemptStatus.SUCCEEDED, "remote-1", null, null, null));

        service.dispatch(publicationId, UUID.randomUUID());

        org.mockito.ArgumentCaptor<PublicationDispatchPayload> captor =
                org.mockito.ArgumentCaptor.forClass(PublicationDispatchPayload.class);
        verify(publicationDispatcher).dispatch(any(), anyString(), captor.capture());
        assertEquals(java.util.List.of("https://img/only.jpg"), captor.getValue().mediaUrls());
    }
```

Add `import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMediaBundleEntity;` and `import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;` if not present.

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=PublicationServiceTest`
Expected: FAIL — `buildDispatchPayload` currently wraps only `[primaryAssetUrl]` (Task 1's stopgap), so the first new test gets `["https://img/a.jpg"]` not `["https://img/a.jpg", "https://img/b.jpg"]`.

- [ ] **Step 3: Implement**

In `PublicationService.java`, add:

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
        return bundle.getPrimaryAssetUrl() == null ? List.of() : List.of(bundle.getPrimaryAssetUrl());
    }
```

`buildDispatchPayload` last arg becomes `bundleImageUrls(entity)`:

```java
    private PublicationDispatchPayload buildDispatchPayload(PublicationEntity entity) {
        return new PublicationDispatchPayload(
                entity.getProductId(),
                entity.getPlatform(),
                entity.getChannelType(),
                entity.getCaption(),
                readHashtags(entity.getHashtagsJson()),
                bundleImageUrls(entity)
        );
    }
```

(Delete the now-unused `PublicationMediaBundleEntity bundle = ...get(0)` local if it is only used here.)

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=PublicationServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java \
        backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java
git commit -m "feat(publication): buildDispatchPayload reads the ordered image list from the manifest"
```

---

## Task 4: `InstagramGraphPublisherAdapter` carousel

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapter.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapterTest.java`

**Interfaces:**
- Consumes: `PublicationDispatchPayload.mediaUrls` (Task 1).
- Produces: `publish(payload)` posts a carousel when `mediaUrls.size() > 1`, unchanged single-image path when size 1.

- [ ] **Step 1: Write the failing test**

Add to `InstagramGraphPublisherAdapterTest.java` (the shared `payload` field posts a single image; build a local 2-image payload here):

```java
    @Test
    void publishes_a_two_image_carousel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        PublicationDispatchPayload carousel = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "Mira esto", List.of("#pilarestilo"),
                List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"));

        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("/17841423631997093/media"),
                        org.hamcrest.Matchers.containsString("is_carousel_item=true"))))
                .andRespond(withSuccess("{\"id\":\"child-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("child-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("is_carousel_item=true")))
                .andRespond(withSuccess("{\"id\":\"child-2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("child-2?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("media_type=CAROUSEL"),
                        org.hamcrest.Matchers.containsString("children=child-1,child-2"))))
                .andRespond(withSuccess("{\"id\":\"parent-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("parent-1?fields=status_code")))
                .andRespond(withSuccess("{\"status_code\":\"FINISHED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media_publish?creation_id=parent-1")))
                .andRespond(withSuccess("{\"id\":\"178999\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("178999?fields=permalink")))
                .andRespond(withSuccess("{\"permalink\":\"https://www.instagram.com/p/ZZZ/\"}", MediaType.APPLICATION_JSON));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(carousel);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("178999", result.remotePostId());
        server.verify();
    }

    @Test
    void a_failed_carousel_child_fails_the_whole_publication() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        PublicationDispatchPayload carousel = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "c", List.of(), List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("is_carousel_item=true")))
                .andRespond(withServerError());

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, instagramConfig(), new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(carousel);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
        assertEquals("INSTAGRAM_PUBLISH_ERROR", result.errorCode());
    }
```

The existing single-image tests already assert a `/media?image_url=...&caption=...` call then `status_code` poll then `/media_publish` — those pass unchanged because size-1 keeps that path. (Check: `instagramConfig()` is the existing helper in the file that returns an `EffectiveConfig` with `instagramUserId = "17841423631997093"`.)

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=InstagramGraphPublisherAdapterTest`
Expected: FAIL — `publish` sends a single `/media?image_url=...` for `mediaUrls.get(0)` and ignores the rest.

- [ ] **Step 3: Implement**

In `InstagramGraphPublisherAdapter.publish`, replace the block from `JsonNode created = postJson(...)` through `awaitContainerReady(client, creationId, ...)` with:

```java
            String creationId = buildPublishableContainer(client, config, payload.mediaUrls(), payload.fullCaptionText());
```

Keep the `media_publish` + `remotePostId` + `fetchPermalink` + return exactly as they are (they use `creationId`).

Add the private method:

```java
    private String buildPublishableContainer(RestClient client, MetaPublishingConfigResolver.EffectiveConfig config,
                                             List<String> mediaUrls, String caption) {
        if (mediaUrls.size() == 1) {
            JsonNode created = postJson(client,
                    "/{userId}/media?image_url={imageUrl}&caption={caption}&access_token={token}",
                    config.instagramUserId(), mediaUrls.get(0), caption, config.instagramAccessToken());
            String id = created.hasNonNull("id") ? created.get("id").asString() : null;
            if (id == null) {
                throw new IllegalStateException("Instagram did not return a media container id");
            }
            awaitContainerReady(client, id, config.instagramAccessToken());
            return id;
        }
        List<String> childIds = new ArrayList<>();
        for (String url : mediaUrls) {
            JsonNode child = postJson(client,
                    "/{userId}/media?image_url={imageUrl}&is_carousel_item=true&access_token={token}",
                    config.instagramUserId(), url, config.instagramAccessToken());
            String childId = child.hasNonNull("id") ? child.get("id").asString() : null;
            if (childId == null) {
                throw new IllegalStateException("Instagram did not return a carousel child container id");
            }
            awaitContainerReady(client, childId, config.instagramAccessToken());
            childIds.add(childId);
        }
        JsonNode parent = postJson(client,
                "/{userId}/media?media_type=CAROUSEL&caption={caption}&children={children}&access_token={token}",
                config.instagramUserId(), caption, String.join(",", childIds), config.instagramAccessToken());
        String parentId = parent.hasNonNull("id") ? parent.get("id").asString() : null;
        if (parentId == null) {
            throw new IllegalStateException("Instagram did not return a carousel parent container id");
        }
        awaitContainerReady(client, parentId, config.instagramAccessToken());
        return parentId;
    }
```

Add `import java.util.ArrayList;` (`java.util.List` is already imported). The `if (creationId == null) return failed(...)` guard that used to sit inside `publish` is now inside `buildPublishableContainer` as the `IllegalStateException` throws, caught by the existing `catch (RuntimeException ex)` in `publish`.

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=InstagramGraphPublisherAdapterTest`
Expected: PASS (existing single-image tests + 2 new carousel tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapter.java \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapterTest.java
git commit -m "feat(publication): Instagram carousel — child containers + a CAROUSEL parent"
```

---

## Task 5: `FacebookPagePublisherAdapter` carousel

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapter.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapterTest.java`

**Interfaces:**
- Consumes: `PublicationDispatchPayload.mediaUrls` (Task 1).
- Produces: `publish(payload)` posts a carousel when `mediaUrls.size() > 1` (unpublished photos + `/feed` with `attached_media`), unchanged `/photos` path when size 1.

- [ ] **Step 1: Write the failing test**

Add to `FacebookPagePublisherAdapterTest.java`:

```java
    @Test
    void publishes_a_carousel_via_unpublished_photos_and_a_feed_post() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "1023624300843445", "token-fb", "https://graph.facebook.com/v23.0", null));

        PublicationDispatchPayload carousel = new PublicationDispatchPayload(
                java.util.UUID.randomUUID(), PublicationPlatform.FACEBOOK, PublicationChannelType.FEED_POST,
                "Mira esto", List.of("#pilarestilo"),
                List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"));

        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("/1023624300843445/photos"),
                        org.hamcrest.Matchers.containsString("published=false"))))
                .andRespond(withSuccess("{\"id\":\"photo-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("published=false")))
                .andRespond(withSuccess("{\"id\":\"photo-2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("/1023624300843445/feed"),
                        org.hamcrest.Matchers.containsString("media_fbid"))))
                .andRespond(withSuccess("{\"id\":\"1023624300843445_9999\"}", MediaType.APPLICATION_JSON));

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(builder, configResolver, new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(carousel);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("1023624300843445_9999", result.remotePostId());
        assertEquals("https://www.facebook.com/1023624300843445_9999", result.remotePermalink());
        server.verify();
    }

    @Test
    void a_failed_carousel_photo_upload_fails_the_publication() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "1023624300843445", "token-fb", "https://graph.facebook.com/v23.0", null));

        PublicationDispatchPayload carousel = new PublicationDispatchPayload(
                java.util.UUID.randomUUID(), PublicationPlatform.FACEBOOK, PublicationChannelType.FEED_POST,
                "c", List.of(), List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("published=false")))
                .andRespond(withServerError());

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(builder, configResolver, new tools.jackson.databind.ObjectMapper());
        PublicationDispatcher.DispatchResult result = adapter.publish(carousel);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
        assertEquals("FACEBOOK_PUBLISH_ERROR", result.errorCode());
    }
```

The existing single-image tests already assert a `/photos?url=...&caption=...` call — those pass unchanged (size 1).

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=FacebookPagePublisherAdapterTest`
Expected: FAIL — `publish` sends one `/photos` for `mediaUrls.get(0)` and ignores the rest.

- [ ] **Step 3: Implement**

In `FacebookPagePublisherAdapter.publish`, after the credentials guard, branch:

```java
        RestClient client = restClientBuilder.baseUrl(config.facebookBaseUrl()).build();
        try {
            if (payload.mediaUrls().size() == 1) {
                return publishSinglePhoto(client, config, payload.mediaUrls().get(0), payload.fullCaptionText());
            }
            return publishCarousel(client, config, payload.mediaUrls(), payload.fullCaptionText());
        } catch (RuntimeException ex) {
            return failed(ex.getMessage());
        }
```

`publishSinglePhoto` is the current body (the `/photos?url=&caption=` call + `post_id`/`id` extraction + `https://www.facebook.com/` + postId permalink), lifted verbatim into a private method returning `DispatchResult`.

Add:

```java
    private PublicationDispatcher.DispatchResult publishCarousel(RestClient client,
                                                                MetaPublishingConfigResolver.EffectiveConfig config,
                                                                List<String> mediaUrls, String caption) {
        List<String> photoIds = new ArrayList<>();
        for (String url : mediaUrls) {
            String raw = client.post()
                    .uri("/{pageId}/photos?url={url}&published=false&access_token={token}",
                            config.facebookPageId(), url, config.facebookPageAccessToken())
                    .retrieve().body(String.class);
            JsonNode photo = parse(raw);
            String photoId = photo.hasNonNull("id") ? photo.get("id").asString() : null;
            if (photoId == null) {
                throw new IllegalStateException("Facebook did not return an unpublished photo id");
            }
            photoIds.add(photoId);
        }
        String attachedMedia = objectMapper.writeValueAsString(
                photoIds.stream().map(id -> java.util.Map.of("media_fbid", id)).toList());
        String raw = client.post()
                .uri("/{pageId}/feed?message={message}&attached_media={attachedMedia}&access_token={token}",
                        config.facebookPageId(), caption, attachedMedia, config.facebookPageAccessToken())
                .retrieve().body(String.class);
        JsonNode feed = parse(raw);
        String postId = feed.hasNonNull("id") ? feed.get("id").asString() : null;
        String permalink = postId == null ? null : "https://www.facebook.com/" + postId;
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED, postId, null, null, permalink);
    }
```

Add `import java.util.ArrayList;` and `import java.util.List;`. `parse(...)`, `objectMapper`, `failed(...)` are existing members. `objectMapper.writeValueAsString` on Jackson 3 throws the unchecked `JacksonException`, caught by the `catch (RuntimeException ex)` in `publish`.

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=FacebookPagePublisherAdapterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapter.java \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapterTest.java
git commit -m "feat(publication): Facebook carousel — unpublished photos + a feed post with attached_media"
```

---

## Task 6: `PublicationBatchDetailDto.Row.imageUrls` + `getBatch`

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationBatchDetailDto.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java` (`getBatch` ~line 207-215)
- Modify: `frontend/src/lib/api.ts` (`PublicationBatchDetailRow` ~line 1585)
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationControllerIT.java`

**Interfaces:**
- Consumes: `PublicationService.bundleImageUrls(PublicationEntity)` (Task 3).
- Produces: `PublicationBatchDetailDto.Row` gains a trailing `List<String> imageUrls`; frontend `PublicationBatchDetailRow` gains `imageUrls: string[]`.

- [ ] **Step 1: Write the failing test**

`PublicationControllerIT.java` — find the existing batch test that creates a batch and GETs `/batches/{id}` (from H-2/H-3, `admin_sees_a_published_batch_in_the_history` or similar). Add:

```java
    @Test
    @WithMockUser(username = "admin@pilarestilo.com", roles = {"ADMIN"}, authorities = {"publications.read", "publications.update"})
    void batch_detail_rows_carry_the_full_image_list() throws Exception {
        String productId = createProductReturningId();   // helper the IT already uses, or inline a POST /api/products
        String body = objectMapper.writeValueAsString(Map.of(
                "productIds", List.of(productId),
                "platforms", List.of("INSTAGRAM"),
                "captionTemplate", "{producto}",
                "imageSelections", Map.of(productId, List.of("https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"))));

        String batchJson = mvc.perform(post("/api/admin/publications/batch")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/admin/publications/batches"))
                .andExpect(status().isOk());
        // fetch the detail of the just-created batch and assert the row imageUrls
        // (use the batchId from the list response; the IT's existing batch tests show the pattern)
    }
```

Match this to the IT's existing batch-test style (it already has helpers for auth + creating products + reading the batch list). The single assertion that matters: a detail row's `$.rows[0].imageUrls` contains both URLs.

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=PublicationControllerIT`
Expected: FAIL — the `Row` record has no `imageUrls`.

- [ ] **Step 3: Add the field to the DTO**

`PublicationBatchDetailDto.java` `Row`:

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
            List<String> imageUrls
    ) {}
```

- [ ] **Step 4: Populate it in `getBatch`**

The rows in `getBatch` are `PublicationEntity` (`r`), so call `bundleImageUrls(r)` (Task 3's helper). The `Row` construction (line ~209):

```java
            return new PublicationBatchDetailDto.Row(
                    r.getId(), r.getProductId(),
                    p != null ? p.getName() : "(producto eliminado)",
                    p != null ? p.getImageUrl() : null,
                    r.getPlatform(), r.getStatus(), r.getExternalPermalink(),
                    r.getLastErrorCode(), r.getLastErrorMessage(),
                    bundleImageUrls(r));
```

- [ ] **Step 5: Frontend type**

`frontend/src/lib/api.ts` `PublicationBatchDetailRow` interface — add:

```ts
  imageUrls: string[];
```

- [ ] **Step 6: Run the tests, verify they pass**

Run: `cd backend && mvn test -Dtest=PublicationControllerIT`
Run: `cd frontend && ./node_modules/.bin/tsc --noEmit`
Expected: backend PASS; tsc clean.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationBatchDetailDto.java \
        backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationControllerIT.java \
        frontend/src/lib/api.ts
git commit -m "feat(publication): batch detail rows carry the per-post image list"
```

---

## Task 7: `api.ts` + `PublicarTab` carousel toggle, preview, submit, edit-preload

**Files:**
- Modify: `frontend/src/lib/api.ts` (`PublishProductsBatchRequest` ~line 1523-1535)
- Modify: `frontend/src/islands/admin/PublicarTab.tsx` (state ~117; `PublicarTabPreload` ~line 20; preload `useEffect` ~123-148; per-product row ~495-616; `handleSubmit` payload ~288-297)
- Test: `frontend/src/islands/admin/__tests__/PublicarTab.test.tsx`

**Interfaces:**
- Consumes: `ProductDto.galleryImageUrls` (H-4a); `PublicationBatchDetailRow.imageUrls` (Task 6).
- Produces: `PublishProductsBatchRequest.imageSelections?: Record<string, string[]>` (replaces `imageOverrides`). `PublicarTabPreload.imageSelections?: Record<string, string[]>`.

- [ ] **Step 1: Write the failing test**

Add to `PublicarTab.test.tsx` (it already mocks `publishProductsBatch`, `searchProducts`, `getProduct`, `uploadMediaFile`). Use a product with a gallery:

```tsx
  it('hides the carousel toggle for a product with no gallery', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);   // 'Chaqueta', no galleryImageUrls in the fixture
    expect(screen.queryByRole('checkbox', { name: /carrusel/i })).not.toBeInTheDocument();
    expect(screen.getByText(/agregá fotos a la galería/i)).toBeInTheDocument();
  });

  it('sends imageSelections with the gallery when carousel is on', async () => {
    const user = userEvent.setup();
    vi.mocked(searchProducts).mockResolvedValue({
      content: [{
        id: 'p1', name: 'Chaqueta', price: { amount: 49990, currency: 'CLP' },
        imageUrl: 'https://img/cover.jpg', galleryImageUrls: ['https://img/g1.jpg', 'https://img/g2.jpg'],
      } as never],
      totalElements: 1, totalPages: 1, size: 24, number: 0,
    } as never);
    render(<PublicarTab />);
    await user.type(screen.getByPlaceholderText(/buscar producto/i), 'cha');
    await user.click(await screen.findByRole('button', { name: /chaqueta/i }));

    await user.click(screen.getByRole('checkbox', { name: /carrusel/i }));
    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));

    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({
          imageSelections: { p1: ['https://img/cover.jpg', 'https://img/g1.jpg', 'https://img/g2.jpg'] },
        }),
        't',
      ),
    );
  });

  it('sends a single-element imageSelections when carousel is off', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);
    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));
    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({ imageSelections: { p1: ['https://img/chaqueta.jpg'] } }),
        't',
      ),
    );
  });
```

(Adjust the fixture URLs to match the file's `searchProducts` mock — `imageUrl` is `https://img/chaqueta.jpg` there.)

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/PublicarTab.test.tsx`
Expected: FAIL — no carousel checkbox; payload has `imageOverrides` not `imageSelections`.

- [ ] **Step 3: `api.ts`**

`PublishProductsBatchRequest` — replace the `imageOverrides` line:

```ts
  /** productId -> ordered image list for this post (cover/override first, then carousel extras). */
  imageSelections?: Record<string, string[]>;
```

- [ ] **Step 4: `PublicarTab` — state + preload type**

- Add state after `imageOverrides` (line ~117): `const [carouselProducts, setCarouselProducts] = useState<Set<string>>(new Set());`
- `PublicarTabPreload` (line ~20) — add `imageSelections?: Record<string, string[]>;`
- Preload `useEffect` (after the `.then((loaded) => {` sets `selected`, line ~142): reconstruct from `preload.imageSelections`:

  ```ts
      if (preload.imageSelections) {
        const nextCarousel = new Set<string>();
        const nextOverrides = new Map<string, string>();
        for (const [pid, urls] of Object.entries(preload.imageSelections)) {
          if (urls.length > 1) nextCarousel.add(pid);
          const prod = loaded.find((x) => x?.id === pid);
          if (urls[0] && prod && urls[0] !== prod.imageUrl) nextOverrides.set(pid, urls[0]);
        }
        setCarouselProducts(nextCarousel);
        setImageOverrides(nextOverrides);
      }
  ```

- [ ] **Step 5: `PublicarTab` — per-product row UI**

In the `selectedProducts.map((product) => { ... })` block (line ~495), inside the `<div className="flex-1 flex flex-col gap-2">`, after the image controls (`<div className="flex items-center gap-3">...</div>`, ~line 591), add:

```tsx
                    {(product.galleryImageUrls ?? []).length > 0 ? (
                      <label className="inline-flex items-center gap-2 text-[0.72rem] text-pe-muted">
                        <input
                          type="checkbox"
                          checked={carouselProducts.has(product.id)}
                          onChange={(e) =>
                            setCarouselProducts((prev) => {
                              const next = new Set(prev);
                              if (e.target.checked) next.add(product.id);
                              else next.delete(product.id);
                              return next;
                            })
                          }
                        />
                        Carrusel ({1 + (product.galleryImageUrls ?? []).length} fotos)
                      </label>
                    ) : (
                      <p className="text-[0.68rem] text-pe-muted">
                        Agregá fotos a la galería del producto para hacer carrusel.
                      </p>
                    )}
                    {carouselProducts.has(product.id) && (
                      <ul className="flex gap-1.5">
                        {[effectiveImageUrl(product), ...(product.galleryImageUrls ?? [])].map((url, i) => (
                          <li key={`${url}-${i}`}>
                            <img src={url} alt="" className="w-8 h-10 object-cover border border-pe-border" />
                          </li>
                        ))}
                      </ul>
                    )}
```

- [ ] **Step 6: `PublicarTab` — submit payload**

`handleSubmit` (line ~288) — replace the `imageOverrides:` line in `payload` with:

```ts
        imageSelections: Object.fromEntries(
          selectedProducts.map((p) => {
            const first = imageOverrides.get(p.id) ?? p.imageUrl;
            return [p.id, carouselProducts.has(p.id) ? [first, ...(p.galleryImageUrls ?? [])] : [first]];
          }),
        ),
```

- [ ] **Step 7: Run the tests, verify they pass**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/PublicarTab.test.tsx && ./node_modules/.bin/tsc --noEmit`
Expected: PASS; tsc clean. (Existing PublicarTab tests that asserted `imageOverrides` in the payload — update them to `imageSelections` with the single-element list shape.)

- [ ] **Step 8: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/islands/admin/PublicarTab.tsx \
        frontend/src/islands/admin/__tests__/PublicarTab.test.tsx
git commit -m "feat(admin): PublicarTab carousel toggle + per-post image selection"
```

---

## Task 8: `HistorialTab` — "Carrusel · N" + edit-scheduled carries `imageSelections`

**Files:**
- Modify: `frontend/src/islands/admin/HistorialTab.tsx` (`Preload` type ~line 20; `onEditScheduled` prop type ~line 26; the `onEditScheduled(...)` call ~line 288; the row render ~line 337-348)
- Modify: `frontend/src/islands/admin/PublicacionesPage.tsx` (`editScheduled` passes the preload through — already does; only the type widens)
- Test: `frontend/src/islands/admin/__tests__/HistorialTab.test.tsx`

**Interfaces:**
- Consumes: `PublicationBatchDetailRow.imageUrls` (Task 6); `PublicarTabPreload.imageSelections` (Task 7).
- Produces: an expanded batch row shows "Carrusel · N" when its `imageUrls.length > 1`; "Editar" on a scheduled batch passes `imageSelections` in the preload.

- [ ] **Step 1: Write the failing test**

Add to `HistorialTab.test.tsx` (it mocks `getPublicationBatches` / `getPublicationBatchDetail`). Give a detail row `imageUrls` of length 3:

```tsx
  it('marks a row as a carousel when it has more than one image', async () => {
    // configure the getPublicationBatchDetail mock so rows[0].imageUrls has 3 urls
    // expand the batch (click its summary button), then:
    expect(await screen.findByText(/carrusel · 3/i)).toBeInTheDocument();
  });

  it('editar y volver a publicar carries imageSelections from the rows', async () => {
    const onEditScheduled = vi.fn();
    // scheduled batch, rows[0].imageUrls = ['a','b'] for product 'p1'
    // render, expand, click "Editar"
    expect(onEditScheduled).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({ imageSelections: { p1: ['a', 'b'] } }),
    );
  });
```

Match the mock-data shape the file already uses for `getPublicationBatchDetail` (add `imageUrls` to its `rows`).

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/HistorialTab.test.tsx`
Expected: FAIL — no "Carrusel · N" text; `onEditScheduled` payload has no `imageSelections`.

- [ ] **Step 3: Widen the `Preload` / prop types**

`HistorialTab.tsx` — the `Preload` type (imported or local, ~line 20) — the `onEditScheduled` prop type at line 26 is
`(batchId: string, p: Required<Pick<Preload, 'productIds' | 'captionTemplate' | 'hashtags' | 'campaignLabel' | 'scheduledAt'>>) => void`.
Add `imageSelections` to the `Pick` (it stays optional on `Preload`, so add it as a plain extra):

```ts
  onEditScheduled: (
    batchId: string,
    p: Required<Pick<Preload, 'productIds' | 'captionTemplate' | 'hashtags' | 'campaignLabel' | 'scheduledAt'>>
      & { imageSelections?: Record<string, string[]> },
  ) => void;
```

- [ ] **Step 4: Build `imageSelections` in the "Editar" handler**

At the `onEditScheduled(b.batchId as string, { ... })` call (~line 288), add:

```ts
                          imageSelections: Object.fromEntries(
                            detail.productIds.map((pid) => {
                              const row = detail.rows.find((r) => r.productId === pid);
                              return [pid, row?.imageUrls ?? []];
                            }).filter(([, urls]) => (urls as string[]).length > 0),
                          ),
```

- [ ] **Step 5: "Carrusel · N" in the row render**

In `detail.rows.map((r) => { ... })` (~line 337), next to the platform `<span>` (line ~347), add:

```tsx
                          {r.imageUrls.length > 1 && (
                            <span className="text-[0.68rem] text-pe-muted shrink-0">Carrusel · {r.imageUrls.length}</span>
                          )}
```

- [ ] **Step 6: Run the tests, verify they pass**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/HistorialTab.test.tsx && ./node_modules/.bin/tsc --noEmit`
Expected: PASS; tsc clean.

- [ ] **Step 7: Full regression**

Run: `cd backend && mvn test`
Run: `cd frontend && npx vitest run && ./node_modules/.bin/tsc --noEmit`
Expected: all green.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/islands/admin/HistorialTab.tsx frontend/src/islands/admin/PublicacionesPage.tsx \
        frontend/src/islands/admin/__tests__/HistorialTab.test.tsx
git commit -m "feat(admin): HistorialTab shows carousel size + edit-scheduled keeps the image list"
```

---

## Self-Review

**Spec coverage:**

| Spec section | Task |
|---|---|
| §1 command/request `imageSelections` + `toBatchCommand` parse | Task 2 |
| §2 `BatchPublicationFactory` bundle with `assetManifest.imageUrls` | Task 2 |
| §3 `buildDispatchPayload` reads the list + `getBatch` `imageUrls` | Tasks 3, 6 |
| §4 `PublicationDispatchPayload.mediaUrls` | Task 1 |
| §5 `MetaDirectPublicationDispatcher` resolves each URL | Task 1 |
| §6 Instagram carousel | Task 4 |
| §7 Facebook carousel | Task 5 |
| §8 `PublicationMediaBundleDto` — no shape change | (nothing to do — noted) |
| §9 `PublicationBatchDetailDto.Row.imageUrls` | Task 6 |
| §10 `api.ts` `imageSelections` + `PublicationBatchDetailRow.imageUrls` | Tasks 6, 7 |
| §11 `PublicarTab` toggle / preview / submit / edit-preload | Task 7 |
| §12 `HistorialTab` "Carrusel · N" | Task 8 |
| §Error handling — child/photo failure -> FAILED retryable; empty `mediaUrls` -> DomainException; pre-H-4b fallback | Tasks 1, 3, 4, 5 |
| §Testing | every task |

No gaps. §8 is a no-op by design (the DTO already carries `assetManifest`).

**Placeholder scan:** Task 6 Step 1 and Task 8 Step 1 defer parts of the test body to "match the IT's existing style" / "match the mock-data shape the file already uses" rather than reproducing ~40 lines of existing harness. This is deliberate: `PublicationControllerIT` and `HistorialTab.test.tsx` each have a working batch-flow test to copy, and a blind second copy here would drift from the real file. The assertion that matters is stated literally in each. All other steps carry literal code.

**Type consistency:**
- `PublicationDispatchPayload.mediaUrls: List<String>` — Task 1; read as `payload.mediaUrls()` in Tasks 4, 5; built by `buildDispatchPayload` in Tasks 1 (stopgap) and 3 (real).
- `PublishProductsBatchCommand.imageSelections: Map<UUID, List<String>>` / request `Map<String, List<String>>` — Task 2; consumed by `BatchPublicationFactory` in Task 2.
- `assetManifest.get("imageUrls")` — written in Task 2, read by `bundleImageUrls` in Task 3, reused in Task 6.
- `PublicationService.bundleImageUrls(PublicationEntity)` — Task 3, called in Task 6.
- `PublicationBatchDetailDto.Row` trailing `List<String> imageUrls` — Task 6; frontend `PublicationBatchDetailRow.imageUrls: string[]` — Task 6, read in Tasks 7 (preload) and 8 (row render).
- `PublishProductsBatchRequest.imageSelections?: Record<string, string[]>` (frontend) — Task 7; `PublicarTabPreload.imageSelections?: Record<string, string[]>` — Task 7, populated by Task 8's `onEditScheduled`.
- `carouselProducts: Set<string>` — Task 7 only.

Consistent throughout.
