# Publication History View (Increment H, Etapa H-2) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Historial" tab to `/admin/publicaciones` showing past publish batches grouped and expandable, with per-row retry / re-publish / error detail and a link to the live post.

**Architecture:** A new `publication_batches` table becomes the container for one `PublishProductsBatchUseCase.execute()` call (caption template, hashtags, campaign label, actor). `publications` gains `batch_id` + `external_permalink`. Both Meta adapters capture the live-post permalink at publish time. Three new read/action endpoints on the existing `PublicationController` back a new React `HistorialTab`; the current compose UI moves under a `PublicarTab` behind a URL-synced tab shell.

**Tech Stack:** Spring Boot 4 (Java 25), Flyway, JPA, Testcontainers + MockMvc, JUnit 5 + Mockito, `MockRestServiceServer` for HTTP; Astro 5 SSR + React islands, Zustand, Tailwind `pe-*` tokens, Vitest + React Testing Library.

**Spec:** `docs/superpowers/specs/2026-09-05-social-publishing-h2-history-design.md`

## Global Constraints

- Hexagonal module layout: `publication` module keeps `productId`/`sourceId` as **bare `UUID` fields, not JPA associations** — `batchId` follows that (no `@ManyToOne`).
- Flyway: never edit an applied migration. Current highest is **V99**; this adds **V100**. `V100` must be expand-only (new table + nullable columns + indexes), safe against the running old app.
- No `Map<Enum,Interface>` bean-selection anywhere. Orchestrators that loop over `@Transactional` calls on `PublicationService` are **not `@Transactional` themselves** (rollback-only hazard) — see `PublishProductsBatchUseCase`.
- Jackson 3: `tools.jackson.databind.ObjectMapper`, not `com.fasterxml`.
- Spanish (Chilean, tú-form) for all user-facing copy. No voseo ("Elige", not "Elegí"). No em dashes in UI copy.
- Status must never be conveyed by color alone — every status pill carries an icon + a word.
- Frontend copy: `pe-*` semantic tokens only, no literal hex, no generic Tailwind colors. Reuse `text-pe-warning-ink` / `text-pe-positive-ink` / `text-pe-danger-ink` and their `-surface` pairs.
- `notification-service` maps read-only views of `publications` under `ddl-auto: validate`. Adding **nullable** columns does not break `validate` (it only fails on missing/mistyped mapped columns). No `*RoEntity` change needed for V100.
- Pre-flight before merging V100: confirm `SELECT count(*) FROM publications` is `0` in local, staging and prod. If any row exists, it keeps `batch_id = NULL` and surfaces under a single non-expandable "Sin tanda" group — display-only, not a blocker.
- Permission model: `PUBLICATIONS_READ` (GETs) and `PUBLICATIONS_UPDATE` (retry) already exist and are granted to ADMIN. No RBAC migration.

---

## File Structure

**Backend — create:**
- `backend/src/main/resources/db/migration/V100__publication_batches.sql` — the migration.
- `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/entities/PublicationBatchEntity.java` — JPA entity for `publication_batches`.
- `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/repositories/PublicationBatchJpaRepository.java` — its repository.
- `backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationBatchSummaryDto.java` — list row.
- `backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationBatchDetailDto.java` — detail + nested `Row`.
- `backend/src/main/java/com/pilarestilo/publication/application/usecases/RetryFailedBatchUseCase.java` — non-transactional retry orchestrator.
- `backend/src/test/java/com/pilarestilo/publication/application/usecases/RetryFailedBatchUseCaseTest.java`.

**Backend — modify:**
- `PublicationEntity.java` — add `batchId`, `externalPermalink` fields + accessors.
- `PublicationJpaRepository.java` — add two batch finders.
- `PublicationDispatcher.java` (port) — `DispatchResult` gains `remotePermalink`.
- `InstagramGraphPublisherAdapter.java` / `FacebookPagePublisherAdapter.java` — capture permalink.
- `CreatePublicationCommand.java` — add `batchId`.
- `PublicationService.java` — `create` copies `batchId`; `dispatchInternal` persists `externalPermalink`; `toDto` maps it; new `listBatches()` / `getBatch(UUID)`; constructor gains `PublicationBatchJpaRepository`.
- `PublicationDto.java` — add `externalPermalink`.
- `PublishProductsBatchUseCase.java` — create the batch row, thread `batchId`.
- `PublicationController.java` — three endpoints + `RetryFailedBatchUseCase` dependency.
- Tests: `PublicationServiceTest`, `MetaDirectPublicationDispatcherTest`, `InstagramGraphPublisherAdapterTest`, `FacebookPagePublisherAdapterTest`, `PublishProductsBatchUseCaseTest`, `PublicationControllerIT`.

**Frontend — create:**
- `frontend/src/islands/admin/PublicarTab.tsx` — the current `PublicacionesPage` body, renamed, `+ preload` prop.
- `frontend/src/islands/admin/HistorialTab.tsx` — the history view.
- `frontend/src/islands/admin/__tests__/HistorialTab.test.tsx`.
- `frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx` — NEW shell/tab test (the current file of that name is renamed, see below).

**Frontend — modify:**
- `frontend/src/islands/admin/PublicacionesPage.tsx` — becomes the tab shell.
- `frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx` → **rename to** `PublicarTab.test.tsx`, change import to `../PublicarTab`.
- `frontend/src/lib/api.ts` — 4 functions + 3 interfaces.
- `frontend/src/pages/admin/publicaciones.astro` — intro copy.

---

## Task 1: `publication_batches` schema + entities

**Files:**
- Create: `backend/src/main/resources/db/migration/V100__publication_batches.sql`
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/entities/PublicationBatchEntity.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/repositories/PublicationBatchJpaRepository.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/entities/PublicationEntity.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/repositories/PublicationJpaRepository.java`

**Interfaces:**
- Produces:
  - `PublicationBatchEntity` — bean-style entity, fields `UUID id`, `String captionTemplate`, `String hashtagsJson`, `String campaignLabel`, `UUID createdBy`, `Instant createdAt`, all with getters/setters.
  - `PublicationBatchJpaRepository extends JpaRepository<PublicationBatchEntity, UUID>` with `List<PublicationBatchEntity> findAllByOrderByCreatedAtDesc()`.
  - `PublicationEntity.getBatchId()/setBatchId(UUID)`, `PublicationEntity.getExternalPermalink()/setExternalPermalink(String)`.
  - `PublicationJpaRepository.findByBatchIdOrderByCreatedAtAsc(UUID)` → `List<PublicationEntity>`, `findByBatchIdInOrderByCreatedAtAsc(Collection<UUID>)` → `List<PublicationEntity>`.

- [ ] **Step 1: Write the migration**

Create `V100__publication_batches.sql`:

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

- [ ] **Step 2: Add the entity**

Create `PublicationBatchEntity.java` (mirror `PublicationEntity`'s style — `@Entity`, `@Table(name = "publication_batches")`, `@Id` on `id`, `@Column` names in snake_case, plain getters/setters):

```java
package com.pilarestilo.publication.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publication_batches")
public class PublicationBatchEntity {

    @Id
    private UUID id;

    @Column(name = "caption_template", columnDefinition = "text", nullable = false)
    private String captionTemplate;

    @Column(name = "hashtags_json", columnDefinition = "text", nullable = false)
    private String hashtagsJson;

    @Column(name = "campaign_label", length = 120)
    private String campaignLabel;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCaptionTemplate() { return captionTemplate; }
    public void setCaptionTemplate(String captionTemplate) { this.captionTemplate = captionTemplate; }
    public String getHashtagsJson() { return hashtagsJson; }
    public void setHashtagsJson(String hashtagsJson) { this.hashtagsJson = hashtagsJson; }
    public String getCampaignLabel() { return campaignLabel; }
    public void setCampaignLabel(String campaignLabel) { this.campaignLabel = campaignLabel; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: Add the repository**

Create `PublicationBatchJpaRepository.java`:

```java
package com.pilarestilo.publication.infrastructure.persistence.repositories;

import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PublicationBatchJpaRepository extends JpaRepository<PublicationBatchEntity, UUID> {
    List<PublicationBatchEntity> findAllByOrderByCreatedAtDesc();
}
```

- [ ] **Step 4: Add `batchId` + `externalPermalink` to `PublicationEntity`**

In `PublicationEntity.java`, after the `externalPostId` field (line ~75) add:

```java
    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "external_permalink", columnDefinition = "text")
    private String externalPermalink;
```

And in the accessors block (after `getExternalPostId`/`setExternalPostId`):

```java
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public String getExternalPermalink() { return externalPermalink; }
    public void setExternalPermalink(String externalPermalink) { this.externalPermalink = externalPermalink; }
```

- [ ] **Step 5: Add the batch finders to `PublicationJpaRepository`**

Replace the file body with (keeps the three existing methods, adds two):

```java
package com.pilarestilo.publication.infrastructure.persistence.repositories;

import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicationJpaRepository extends JpaRepository<PublicationEntity, UUID> {
    Optional<PublicationEntity> findByIdempotencyKey(String idempotencyKey);
    List<PublicationEntity> findAllByOrderByCreatedAtDesc();
    List<PublicationEntity> findTop20ByProductIdOrderByCreatedAtDesc(UUID productId);
    List<PublicationEntity> findByBatchIdOrderByCreatedAtAsc(UUID batchId);
    List<PublicationEntity> findByBatchIdInOrderByCreatedAtAsc(Collection<UUID> batchIds);
}
```

- [ ] **Step 6: Verify migration + mapping boot**

Run: `cd backend && mvn -q test-compile && mvn -q -Dtest=PublicationControllerIT test`
Expected: PASS. `PublicationControllerIT` boots the full app against a fresh Testcontainers Postgres — it runs `V100` and `ddl-auto: validate` against the new columns/table. A mapping typo fails here.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V100__publication_batches.sql \
  backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/
git commit -m "feat(publication): V100 publication_batches table + batch_id/permalink columns"
```

---

## Task 2: `batchId` on the create command; `externalPermalink` on the DTO

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/commands/CreatePublicationCommand.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationDto.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCase.java` (call-site only; batch-row creation is Task 4)
- Test: `backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java`

**Interfaces:**
- Consumes: `PublicationEntity.setBatchId` / `getExternalPermalink` (Task 1).
- Produces:
  - `CreatePublicationCommand` gains a **trailing** `UUID batchId` component (nullable).
  - `PublicationDto` gains a **trailing** `String externalPermalink` component.

- [ ] **Step 1: Update `PublicationServiceTest` construction sites (failing compile is the "test")**

There are two `new CreatePublicationCommand(...)` calls (lines ~85 and ~136) and DTO assertions. Add `, null` as the final arg to both command constructions. No behavioral assertion change yet.

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q test-compile`
Expected: FAIL — `CreatePublicationCommand` constructor arity mismatch (the test now passes 14 args, the record has 13).

- [ ] **Step 3: Add `batchId` to `CreatePublicationCommand`**

```java
public record CreatePublicationCommand(
        UUID productId,
        PublicationSourceType sourceType,
        UUID sourceId,
        PublicationPlatform platform,
        PublicationChannelType channelType,
        String locale,
        String campaignLabel,
        String caption,
        List<String> hashtags,
        boolean approvalRequired,
        Instant scheduledAt,
        String idempotencyKey,
        List<MediaBundleCommand> mediaBundles,
        UUID batchId
) {
    public record MediaBundleCommand(
            PublicationMediaBundleType bundleType,
            String primaryAssetUrl,
            Map<String, Object> assetManifest
    ) {}
}
```

- [ ] **Step 4: Add `externalPermalink` to `PublicationDto`**

Append `String externalPermalink` as the final component before `List<PublicationMediaBundleDto> mediaBundles` — no: append it as the **last** component after `snapshots` to keep the media/attempt/review/snapshot block contiguous. Final field list ends:

```java
        Instant createdAt,
        Instant updatedAt,
        List<PublicationMediaBundleDto> mediaBundles,
        List<PublicationAttemptDto> attempts,
        List<PublicationReviewDto> reviews,
        List<PublicationSnapshotDto> snapshots,
        String externalPermalink
) {
}
```

- [ ] **Step 5: Thread both through `PublicationService`**

In `create(...)`, after `entity.setCampaignLabel(...)` add:
```java
entity.setBatchId(command.batchId());
```

In `toDto(...)`, add the trailing argument to the `new PublicationDto(...)` call:
```java
                )).toList(),
                entity.getExternalPermalink()
        );
```
(immediately after the `snapshots` `.toList()` closing.)

- [ ] **Step 6: Fix the `PublishProductsBatchUseCase` call site**

In `publishOne(...)`, the `new CreatePublicationCommand(...)` currently ends with the `mediaBundles` list. Append `, null` as the final arg for now (Task 4 replaces it with the real batch id):
```java
                    List.of(new CreatePublicationCommand.MediaBundleCommand(
                            PublicationMediaBundleType.SOCIAL_FEED,
                            command.imageOverrides().getOrDefault(productId, product.getImageUrl()),
                            Map.of()
                    )),
                    null
            );
```

- [ ] **Step 7: Run the affected tests**

Run: `cd backend && mvn -q -Dtest='PublicationServiceTest,PublishProductsBatchUseCaseTest' test`
Expected: `PublicationServiceTest` PASS. `PublishProductsBatchUseCaseTest` FAIL to compile — it also constructs `CreatePublicationCommand` indirectly? No: it constructs `PublishProductsBatchCommand` and asserts on captured `CreatePublicationCommand`. Its `ArgumentCaptor<CreatePublicationCommand>` assertions still compile (they read fields, don't construct). Should PASS. If any test constructs `CreatePublicationCommand` directly, add the trailing `null`.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/ backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java
git commit -m "feat(publication): batchId on CreatePublicationCommand, externalPermalink on PublicationDto"
```

---

## Task 3: capture the live-post permalink in both Meta adapters

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/ports/PublicationDispatcher.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapter.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapter.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java` (`dispatchInternal` catch block + success branch)
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/MetaDirectPublicationDispatcher.java` (only if it constructs `DispatchResult` — it does not; skip)
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapterTest.java`, `FacebookPagePublisherAdapterTest.java`, `MetaDirectPublicationDispatcherTest.java`, `backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java`

**Interfaces:**
- Consumes: `PublicationEntity.setExternalPermalink` (Task 1).
- Produces: `PublicationDispatcher.DispatchResult` gains a **trailing** `String remotePermalink` component (nullable). New arity: 7.

- [ ] **Step 1: Write the failing Instagram permalink test**

In `InstagramGraphPublisherAdapterTest.java`, add:

```java
    @Test
    void fetches_the_permalink_after_publishing_and_returns_it() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "17841423631997093", "token-ig", "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null
        ));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/17841423631997093/media")))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media_publish")))
                .andRespond(withSuccess("{\"id\":\"178923456\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/178923456?fields=permalink")))
                .andRespond(withSuccess("{\"permalink\":\"https://www.instagram.com/p/ABC123/\"}", MediaType.APPLICATION_JSON));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, configResolver);
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("https://www.instagram.com/p/ABC123/", result.remotePermalink());
        server.verify();
    }

    @Test
    void still_succeeds_when_the_permalink_fetch_fails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "17841423631997093", "token-ig", "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null
        ));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media")))
                .andRespond(withSuccess("{\"id\":\"creation-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media_publish")))
                .andRespond(withSuccess("{\"id\":\"178923456\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("fields=permalink")))
                .andRespond(withServerError());

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, configResolver);
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals(null, result.remotePermalink());
    }
```

Also update the two existing tests' assertions in this file: the success-path constructions are the adapter's job (nothing to change), but if any test constructs `DispatchResult` directly here it must gain the 7th arg (none do — leave them).

- [ ] **Step 2: Write the failing Facebook permalink test**

In `FacebookPagePublisherAdapterTest.java`, add:

```java
    @Test
    void builds_the_permalink_from_the_post_id() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "1023624300843445", "token-fb", "https://graph.facebook.com/v23.0", null
        ));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/1023624300843445/photos")))
                .andRespond(withSuccess("{\"post_id\":\"1023624300843445_555\",\"id\":\"555\"}", MediaType.APPLICATION_JSON));

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(builder, configResolver);
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals("https://www.facebook.com/1023624300843445_555", result.remotePermalink());
    }

    @Test
    void leaves_the_permalink_null_when_only_a_bare_photo_id_is_returned() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "1023624300843445", "token-fb", "https://graph.facebook.com/v23.0", null
        ));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/photos")))
                .andRespond(withSuccess("{\"id\":\"555\"}", MediaType.APPLICATION_JSON));

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(builder, configResolver);
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(null, result.remotePermalink());
    }
```

- [ ] **Step 3: Run to verify they fail**

Run: `cd backend && mvn -q -Dtest='InstagramGraphPublisherAdapterTest,FacebookPagePublisherAdapterTest' test`
Expected: FAIL to compile — `result.remotePermalink()` does not exist on `DispatchResult`.

- [ ] **Step 4: Widen `DispatchResult`**

```java
public interface PublicationDispatcher {
    DispatchResult dispatch(UUID publicationId, String idempotencyKey, PublicationDispatchPayload payload);

    record DispatchResult(
            String requestId,
            String payloadHash,
            PublicationAttemptStatus status,
            String remotePostId,
            String errorCode,
            String errorMessage,
            String remotePermalink
    ) {}
}
```

- [ ] **Step 5: Fix every `DispatchResult` construction site**

Add a trailing arg to each:

- `InstagramGraphPublisherAdapter.publish` success return → append the fetched `permalink` (Step 6 below).
- `InstagramGraphPublisherAdapter.failed(...)` → append `null`.
- `FacebookPagePublisherAdapter.publish` success return → append the built `permalink` (Step 7 below).
- `FacebookPagePublisherAdapter.failed(...)` → append `null`.
- `PublicationService.dispatchInternal` catch block (`new PublicationDispatcher.DispatchResult(null, null, PublicationAttemptStatus.FAILED, null, DISPATCH_ERROR_CODE, ex.getMessage())`) → append `, null`.
- `MetaDirectPublicationDispatcherTest` — two sites (lines ~42-43 and ~63-64), append `, null`.
- `PublicationServiceTest` — two sites: the SUCCEEDED result (~168) append `, "https://www.instagram.com/p/x/"`, the FAILED result (~203) append `, null`.

- [ ] **Step 6: Instagram adapter — fetch the permalink**

In `InstagramGraphPublisherAdapter.publish`, after `String remotePostId = String.valueOf(published.get("id"));`:

```java
            String permalink = null;
            try {
                Map<String, Object> permalinkResponse = client.get()
                        .uri("/{mediaId}?fields=permalink&access_token={token}", remotePostId, config.instagramAccessToken())
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
                Object raw = permalinkResponse == null ? null : permalinkResponse.get("permalink");
                permalink = raw == null ? null : String.valueOf(raw);
            } catch (RestClientException permalinkError) {
                // The post is already live; a permalink lookup failure must not flip it to failed.
            }

            return new PublicationDispatcher.DispatchResult(
                    UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED, remotePostId, null, null, permalink);
```

- [ ] **Step 7: Facebook adapter — build the permalink**

In `FacebookPagePublisherAdapter.publish`, replace the success block:

```java
            Object postIdRaw = response == null ? null : response.get("post_id");
            String remotePostId = postIdRaw != null ? String.valueOf(postIdRaw)
                    : (response != null && response.get("id") != null ? String.valueOf(response.get("id")) : null);
            String permalink = postIdRaw == null ? null : "https://www.facebook.com/" + postIdRaw;

            return new PublicationDispatcher.DispatchResult(
                    UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED,
                    remotePostId, null, null, permalink);
```

- [ ] **Step 8: `PublicationService.dispatchInternal` persists the permalink**

In the `SUCCEEDED` branch, after `entity.setExternalPostId(result.remotePostId());`:
```java
            entity.setExternalPermalink(result.remotePermalink());
```

- [ ] **Step 9: Run the affected tests**

Run: `cd backend && mvn -q -Dtest='InstagramGraphPublisherAdapterTest,FacebookPagePublisherAdapterTest,MetaDirectPublicationDispatcherTest,PublicationServiceTest' test`
Expected: all PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/ backend/src/test/java/com/pilarestilo/publication/
git commit -m "feat(publication): capture live-post permalink from Instagram and Facebook on publish"
```

---

## Task 4: `PublishProductsBatchUseCase` creates the batch row

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCase.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCaseTest.java`

**Interfaces:**
- Consumes: `PublicationBatchJpaRepository` (Task 1), `CreatePublicationCommand.batchId` (Task 2).
- Produces: every `PublishProductsBatchUseCase.execute` call now persists one `PublicationBatchEntity` and every child publication carries its `batchId`.

- [ ] **Step 1: Write the failing test**

In `PublishProductsBatchUseCaseTest.java`, add a `@Mock PublicationBatchJpaRepository publicationBatchRepository;`, pass it into the `new PublishProductsBatchUseCase(...)` in `setUp` (the constructor changes in Step 3 — for now this makes it fail to compile, which is the failing state), and add:

```java
    @Test
    void creates_one_batch_row_and_stamps_its_id_on_every_publication() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(49990), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 2);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any())).thenReturn(publishedDto(publicationId));
        when(publicationBatchRepository.save(any(PublicationBatchEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto} a solo {precio}", List.of("#pilarestilo"), "Liquidacion", Map.of(), Map.of()
        ), UUID.randomUUID());

        ArgumentCaptor<PublicationBatchEntity> batchCaptor = ArgumentCaptor.forClass(PublicationBatchEntity.class);
        verify(publicationBatchRepository).save(batchCaptor.capture());
        assertEquals("{producto} a solo {precio}", batchCaptor.getValue().getCaptionTemplate());
        assertEquals("Liquidacion", batchCaptor.getValue().getCampaignLabel());

        ArgumentCaptor<CreatePublicationCommand> cmdCaptor = ArgumentCaptor.forClass(CreatePublicationCommand.class);
        verify(publicationService).create(cmdCaptor.capture(), any());
        assertEquals(batchCaptor.getValue().getId(), cmdCaptor.getValue().batchId());
    }
```

Add imports: `com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity`, `com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository`.

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q -Dtest=PublishProductsBatchUseCaseTest test`
Expected: FAIL — constructor arity / `getCaptionTemplate` unresolved.

- [ ] **Step 3: Implement**

`PublishProductsBatchUseCase`:

```java
    private final PublicationService publicationService;
    private final ProductRepository productRepository;
    private final PublicationBatchJpaRepository publicationBatchRepository;

    public PublishProductsBatchUseCase(PublicationService publicationService,
                                       ProductRepository productRepository,
                                       PublicationBatchJpaRepository publicationBatchRepository) {
        this.publicationService = publicationService;
        this.productRepository = productRepository;
        this.publicationBatchRepository = publicationBatchRepository;
    }

    public PublishProductsBatchResult execute(PublishProductsBatchCommand command, UUID actorUserId) {
        List<PublishProductsBatchResult.PublicationItemResult> items = new ArrayList<>();

        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCaptionTemplate(command.captionTemplate());
        batch.setHashtagsJson(serializeHashtags(command.hashtags()));
        batch.setCampaignLabel(trimToNull(command.campaignLabel()));
        batch.setCreatedBy(actorUserId);
        batch.setCreatedAt(Instant.now());
        publicationBatchRepository.save(batch);

        for (UUID productId : command.productIds()) {
            // ... unchanged, but publishOne now takes batch.getId()
        }
        return new PublishProductsBatchResult(items);
    }
```

Add helpers (copy the JSON-array convention — a bare `String.join` is wrong, hashtags need to be a JSON array string to match `PublicationService.readHashtags`):

```java
    private static final tools.jackson.databind.ObjectMapper HASHTAG_MAPPER = new tools.jackson.databind.ObjectMapper();

    private String serializeHashtags(List<String> hashtags) {
        List<String> clean = hashtags == null ? List.of()
                : hashtags.stream().map(this::trimToNull).filter(Objects::nonNull).distinct().toList();
        try {
            return HASHTAG_MAPPER.writeValueAsString(clean);
        } catch (RuntimeException e) {
            return "[]";
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
```

In `publishOne`, add a `UUID batchId` parameter and pass it as the trailing `CreatePublicationCommand` arg (replacing the `null` from Task 2 Step 6). Update the call in `execute`: `items.add(publishOne(productId, product, platform, caption, command, actorUserId, batch.getId()));`.

Import `Instant`, `PublicationBatchEntity`, `PublicationBatchJpaRepository`. Keep `Objects` (already imported).

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && mvn -q -Dtest=PublishProductsBatchUseCaseTest test`
Expected: PASS (all existing tests + the new one — existing tests need the `publicationBatchRepository` mock `.save` stub; add `lenient().when(publicationBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));` to `setUp`).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCase.java \
  backend/src/test/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCaseTest.java
git commit -m "feat(publication): PublishProductsBatchUseCase records a publication_batches row"
```

---

## Task 5: `listBatches()` + `getBatch()` on `PublicationService`

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationBatchSummaryDto.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationBatchDetailDto.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java`

**Interfaces:**
- Consumes: `PublicationBatchJpaRepository.findAllByOrderByCreatedAtDesc`, `PublicationJpaRepository.findByBatchIdInOrderByCreatedAtAsc` / `findByBatchIdOrderByCreatedAtAsc`, `ProductRepository.findAllByIds(Collection<UUID>)`.
- Produces:
  - `PublicationBatchSummaryDto(UUID batchId, String campaignLabel, Instant createdAt, Set<PublicationPlatform> platforms, int total, int published, int failed, int scheduled, int pending)`.
  - `PublicationBatchDetailDto(UUID batchId, String campaignLabel, String captionTemplate, List<String> hashtags, Instant createdAt, List<UUID> productIds, List<Row> rows)` with `Row(UUID publicationId, UUID productId, String productName, String thumbnailUrl, PublicationPlatform platform, PublicationStatus status, String externalPermalink, String lastErrorCode, String lastErrorMessage)`.
  - `PublicationService.listBatches()` → `List<PublicationBatchSummaryDto>`, `PublicationService.getBatch(UUID)` → `PublicationBatchDetailDto` (throws `NoSuchElementException` for an unknown id).

- [ ] **Step 1: Write the DTOs**

`PublicationBatchSummaryDto.java`:
```java
package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PublicationBatchSummaryDto(
        UUID batchId,
        String campaignLabel,
        Instant createdAt,
        Set<PublicationPlatform> platforms,
        int total,
        int published,
        int failed,
        int scheduled,
        int pending
) {}
```

`PublicationBatchDetailDto.java`:
```java
package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicationBatchDetailDto(
        UUID batchId,
        String campaignLabel,
        String captionTemplate,
        List<String> hashtags,
        Instant createdAt,
        List<UUID> productIds,
        List<Row> rows
) {
    public record Row(
            UUID publicationId,
            UUID productId,
            String productName,
            String thumbnailUrl,
            PublicationPlatform platform,
            PublicationStatus status,
            String externalPermalink,
            String lastErrorCode,
            String lastErrorMessage
    ) {}
}
```

- [ ] **Step 2: Write the failing test**

In `PublicationServiceTest.java`, add a `@Mock PublicationBatchJpaRepository publicationBatchRepository;`, pass it as the first constructor arg in `setUp` (see Step 4 for the new constructor order), and add:

```java
    @Test
    void list_batches_summarizes_each_batch_by_status() {
        UUID batchId = UUID.randomUUID();
        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(batchId);
        batch.setCaptionTemplate("{producto}");
        batch.setHashtagsJson("[\"#pilarestilo\"]");
        batch.setCampaignLabel("Liquidacion");
        batch.setCreatedAt(Instant.now());
        when(publicationBatchRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(batch));

        PublicationEntity ok = batchRow(batchId, PublicationStatus.PUBLISHED, PublicationPlatform.INSTAGRAM);
        PublicationEntity bad = batchRow(batchId, PublicationStatus.FAILED, PublicationPlatform.FACEBOOK);
        when(publicationRepository.findByBatchIdInOrderByCreatedAtAsc(List.of(batchId)))
                .thenReturn(List.of(ok, bad));

        List<PublicationBatchSummaryDto> result = service.listBatches();

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).total());
        assertEquals(1, result.get(0).published());
        assertEquals(1, result.get(0).failed());
        assertTrue(result.get(0).platforms().contains(PublicationPlatform.INSTAGRAM));
        assertTrue(result.get(0).platforms().contains(PublicationPlatform.FACEBOOK));
    }

    @Test
    void get_batch_resolves_product_names_and_rows() {
        UUID batchId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(batchId);
        batch.setCaptionTemplate("{producto}");
        batch.setHashtagsJson("[\"#pilarestilo\"]");
        batch.setCreatedAt(Instant.now());
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        PublicationEntity row = batchRow(batchId, PublicationStatus.FAILED, PublicationPlatform.INSTAGRAM);
        row.setProductId(productId);
        row.setLastErrorCode("INSTAGRAM_PUBLISH_ERROR");
        row.setLastErrorMessage("Rate limited");
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(row));
        when(productRepository.findAllByIds(java.util.Set.of(productId))).thenReturn(List.of(
                Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(1000), "CLP"),
                        "https://img/x.jpg", ProductCondition.NEW, "Pilar", 1)
        ));

        PublicationBatchDetailDto detail = service.getBatch(batchId);

        assertEquals(1, detail.rows().size());
        assertEquals("Chaqueta", detail.rows().get(0).productName());
        assertEquals("Rate limited", detail.rows().get(0).lastErrorMessage());
        assertEquals(List.of(productId), detail.productIds());
    }

    @Test
    void get_batch_throws_for_unknown_id() {
        UUID unknown = UUID.randomUUID();
        when(publicationBatchRepository.findById(unknown)).thenReturn(Optional.empty());
        assertThrows(java.util.NoSuchElementException.class, () -> service.getBatch(unknown));
    }
```

Add a helper to the test class:
```java
    private PublicationEntity batchRow(UUID batchId, PublicationStatus status, PublicationPlatform platform) {
        PublicationEntity e = approvedPublication(UUID.randomUUID(), UUID.randomUUID());
        e.setBatchId(batchId);
        e.setStatus(status);
        e.setPlatform(platform);
        return e;
    }
```
Add imports: `PublicationBatchEntity`, `PublicationBatchJpaRepository`, `PublicationBatchSummaryDto`, `PublicationBatchDetailDto`, `PublicationStatus` (already there), `java.util.Set`.

- [ ] **Step 3: Run to verify it fails**

Run: `cd backend && mvn -q -Dtest=PublicationServiceTest test`
Expected: FAIL to compile — `service.listBatches()` / `getBatch` undefined, constructor arity.

- [ ] **Step 4: Implement**

`PublicationService` constructor gains `PublicationBatchJpaRepository publicationBatchRepository` as the **first** parameter (keep the rest in order); store it. Then:

```java
    @Transactional(readOnly = true)
    public List<PublicationBatchSummaryDto> listBatches() {
        List<PublicationBatchEntity> batches = publicationBatchRepository.findAllByOrderByCreatedAtDesc();
        List<UUID> ids = batches.stream().map(PublicationBatchEntity::getId).toList();
        Map<UUID, List<PublicationEntity>> byBatch = ids.isEmpty() ? Map.of()
                : publicationRepository.findByBatchIdInOrderByCreatedAtAsc(ids).stream()
                        .collect(java.util.stream.Collectors.groupingBy(PublicationEntity::getBatchId));

        List<PublicationBatchSummaryDto> out = new ArrayList<>();
        for (PublicationBatchEntity b : batches) {
            List<PublicationEntity> rows = byBatch.getOrDefault(b.getId(), List.of());
            out.add(summarize(b.getId(), b.getCampaignLabel(), b.getCreatedAt(), rows));
        }
        // Legacy rows with no batch: one synthetic group, last.
        List<PublicationEntity> orphans = publicationRepository.findByBatchIdOrderByCreatedAtAsc(null);
        if (!orphans.isEmpty()) {
            out.add(summarize(null, null, orphans.get(orphans.size() - 1).getCreatedAt(), orphans));
        }
        return out;
    }

    private PublicationBatchSummaryDto summarize(UUID batchId, String label, Instant createdAt, List<PublicationEntity> rows) {
        java.util.EnumSet<PublicationPlatform> platforms = java.util.EnumSet.noneOf(PublicationPlatform.class);
        int published = 0, failed = 0, scheduled = 0, pending = 0;
        for (PublicationEntity r : rows) {
            platforms.add(r.getPlatform());
            switch (r.getStatus()) {
                case PUBLISHED -> published++;
                case FAILED -> failed++;
                case SCHEDULED -> scheduled++;
                default -> pending++;
            }
        }
        return new PublicationBatchSummaryDto(batchId, label, createdAt, platforms,
                rows.size(), published, failed, scheduled, pending);
    }

    @Transactional(readOnly = true)
    public PublicationBatchDetailDto getBatch(UUID batchId) {
        PublicationBatchEntity batch = publicationBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Publication batch not found: " + batchId));
        List<PublicationEntity> rows = publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        java.util.Set<UUID> productIds = rows.stream()
                .map(PublicationEntity::getProductId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<UUID, com.pilarestilo.product.domain.model.Product> products = productIds.isEmpty() ? Map.of()
                : productRepository.findAllByIds(productIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.pilarestilo.product.domain.model.Product::getId, p -> p));

        List<PublicationBatchDetailDto.Row> dtoRows = rows.stream().map(r -> {
            com.pilarestilo.product.domain.model.Product p = r.getProductId() == null ? null : products.get(r.getProductId());
            return new PublicationBatchDetailDto.Row(
                    r.getId(), r.getProductId(),
                    p != null ? p.getName() : "(producto eliminado)",
                    p != null ? p.getImageUrl() : null,
                    r.getPlatform(), r.getStatus(), r.getExternalPermalink(),
                    r.getLastErrorCode(), r.getLastErrorMessage());
        }).toList();

        return new PublicationBatchDetailDto(
                batch.getId(), batch.getCampaignLabel(), batch.getCaptionTemplate(),
                readHashtags(batch.getHashtagsJson()), batch.getCreatedAt(),
                new ArrayList<>(productIds), dtoRows);
    }
```

Note: `findByBatchIdOrderByCreatedAtAsc(null)` for orphans works with Spring Data derived queries translating to `WHERE batch_id IS NULL`. If it does not (some versions require `findByBatchIdIsNull`), add `List<PublicationEntity> findByBatchIdIsNullOrderByCreatedAtAsc();` to the repository and use that.

- [ ] **Step 5: Run to verify it passes**

Run: `cd backend && mvn -q -Dtest=PublicationServiceTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/ backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java
git commit -m "feat(publication): listBatches + getBatch read models on PublicationService"
```

---

## Task 6: `RetryFailedBatchUseCase`

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/application/usecases/RetryFailedBatchUseCase.java`
- Create: `backend/src/test/java/com/pilarestilo/publication/application/usecases/RetryFailedBatchUseCaseTest.java`

**Interfaces:**
- Consumes: `PublicationService.retry(UUID id, UUID actorUserId)` (existing — guards `status == FAILED`, throws `DomainException` otherwise), `PublicationService.getBatch(UUID)` (Task 5), `PublicationJpaRepository.findByBatchIdOrderByCreatedAtAsc` (Task 1).
- Produces: `RetryFailedBatchUseCase.execute(UUID batchId, UUID actorUserId)` → `PublicationBatchDetailDto`.

- [ ] **Step 1: Write the failing test**

`RetryFailedBatchUseCaseTest.java`:

```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.dto.PublicationBatchDetailDto;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryFailedBatchUseCaseTest {

    @Mock PublicationService publicationService;
    @Mock PublicationJpaRepository publicationRepository;

    RetryFailedBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RetryFailedBatchUseCase(publicationService, publicationRepository);
    }

    @Test
    void retries_only_failed_rows_and_returns_the_refreshed_detail() {
        UUID batchId = UUID.randomUUID();
        PublicationEntity failed = row(batchId, PublicationStatus.FAILED);
        PublicationEntity published = row(batchId, PublicationStatus.PUBLISHED);
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(failed, published));
        when(publicationService.getBatch(batchId)).thenReturn(
                new PublicationBatchDetailDto(batchId, null, "{producto}", List.of(), Instant.now(), List.of(), List.of()));

        useCase.execute(batchId, UUID.randomUUID());

        verify(publicationService).retry(eq(failed.getId()), any());
        verify(publicationService, never()).retry(eq(published.getId()), any());
    }

    @Test
    void a_row_that_raced_out_of_failed_is_skipped_without_throwing() {
        UUID batchId = UUID.randomUUID();
        PublicationEntity failed = row(batchId, PublicationStatus.FAILED);
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(failed));
        when(publicationService.retry(any(), any())).thenThrow(new DomainException("Only FAILED publications can be retried"));
        when(publicationService.getBatch(batchId)).thenReturn(
                new PublicationBatchDetailDto(batchId, null, "{producto}", List.of(), Instant.now(), List.of(), List.of()));

        useCase.execute(batchId, UUID.randomUUID()); // must not throw
    }

    private PublicationEntity row(UUID batchId, PublicationStatus status) {
        PublicationEntity e = new PublicationEntity();
        e.setId(UUID.randomUUID());
        e.setBatchId(batchId);
        e.setStatus(status);
        e.setPlatform(PublicationPlatform.INSTAGRAM);
        return e;
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q -Dtest=RetryFailedBatchUseCaseTest test`
Expected: FAIL — `RetryFailedBatchUseCase` does not exist.

- [ ] **Step 3: Implement**

```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.dto.PublicationBatchDetailDto;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Re-dispatches the FAILED rows of a batch. Deliberately not @Transactional: each retry is its
 * own @Transactional call on PublicationService (a different bean), so a failure in one does not
 * roll back the others — same reasoning as PublishProductsBatchUseCase.
 */
@Component
public class RetryFailedBatchUseCase {

    private final PublicationService publicationService;
    private final PublicationJpaRepository publicationRepository;

    public RetryFailedBatchUseCase(PublicationService publicationService,
                                   PublicationJpaRepository publicationRepository) {
        this.publicationService = publicationService;
        this.publicationRepository = publicationRepository;
    }

    public PublicationBatchDetailDto execute(UUID batchId, UUID actorUserId) {
        publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream()
                .filter(p -> p.getStatus() == PublicationStatus.FAILED)
                .forEach(p -> {
                    try {
                        publicationService.retry(p.getId(), actorUserId);
                    } catch (RuntimeException raced) {
                        // Row is no longer FAILED (retried elsewhere, or state changed) — skip it.
                    }
                });
        return publicationService.getBatch(batchId);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && mvn -q -Dtest=RetryFailedBatchUseCaseTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/usecases/RetryFailedBatchUseCase.java \
  backend/src/test/java/com/pilarestilo/publication/application/usecases/RetryFailedBatchUseCaseTest.java
git commit -m "feat(publication): RetryFailedBatchUseCase (non-transactional retry orchestrator)"
```

---

## Task 7: three endpoints on `PublicationController`

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/controllers/PublicationController.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationControllerIT.java`

**Interfaces:**
- Consumes: `PublicationService.listBatches()` / `getBatch(UUID)` (Task 5), `RetryFailedBatchUseCase.execute(UUID, UUID)` (Task 6).
- Produces: `GET /api/admin/publications/batches`, `GET /api/admin/publications/batches/{batchId}`, `POST /api/admin/publications/batches/{batchId}/retry-failed`.

- [ ] **Step 1: Write the failing integration tests**

In `PublicationControllerIT.java` add:

```java
    @Test
    void admin_sees_a_published_batch_in_the_history() throws Exception {
        String adminToken = loginAdmin();
        Product product = productRepository.save(Product.create("Falda historial", "desc",
                new Money(BigDecimal.valueOf(29990), "CLP"), "https://cdn.example.com/falda.jpg",
                ProductCondition.NEW, "Pilar", 3));

        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(product.getId().toString()),
                                "platforms", List.of("INSTAGRAM", "FACEBOOK"),
                                "captionTemplate", "{producto} a solo {precio}",
                                "hashtags", List.of("#pilarestilo"),
                                "campaignLabel", "Historial Test"))))
                .andExpect(status().isOk());

        MvcResult batches = mvc.perform(get("/api/admin/publications/batches")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].campaignLabel").value("Historial Test"))
                .andExpect(jsonPath("$[0].total").value(2))
                .andExpect(jsonPath("$[0].failed").value(2))
                .andExpect(jsonPath("$[0].published").value(0))
                .andReturn();

        String batchId = om.readTree(batches.getResponse().getContentAsString()).get(0).get("batchId").asString();

        mvc.perform(get("/api/admin/publications/batches/{id}", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captionTemplate").value("{producto} a solo {precio}"))
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].status").value("FAILED"))
                .andExpect(jsonPath("$.rows[0].lastErrorCode").exists())
                .andExpect(jsonPath("$.productIds", hasItem(product.getId().toString())));
    }

    @Test
    void retry_failed_in_batch_redispatches_only_failed_rows() throws Exception {
        String adminToken = loginAdmin();
        Product product = productRepository.save(Product.create("Blusa retry", "desc",
                new Money(BigDecimal.valueOf(19990), "CLP"), "https://cdn.example.com/blusa.jpg",
                ProductCondition.NEW, "Pilar", 2));

        MvcResult batchResult = mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(product.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}"))))
                .andExpect(status().isOk())
                .andReturn();
        String publicationId = om.readTree(batchResult.getResponse().getContentAsString())
                .get("items").get(0).get("publicationId").asString();

        MvcResult batches = mvc.perform(get("/api/admin/publications/batches")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        String batchId = om.readTree(batches.getResponse().getContentAsString()).get(0).get("batchId").asString();

        mvc.perform(post("/api/admin/publications/batches/{id}/retry-failed", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].status").value("FAILED"));

        mvc.perform(get("/api/admin/publications/{id}", publicationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retryCount").value(1));
    }

    @Test
    void retry_failed_in_batch_requires_update_permission() throws Exception {
        String sellerToken = jwtTokenProvider.generateAccessToken(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "seller-retry@pilarestilo.com", UserRole.SELLER,
                List.of("productos"), List.of("publications.read"));

        mvc.perform(post("/api/admin/publications/batches/{id}/retry-failed", UUID.randomUUID())
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknown_batch_id_returns_404() throws Exception {
        String adminToken = loginAdmin();
        mvc.perform(get("/api/admin/publications/batches/{id}", UUID.randomUUID())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && mvn -q -Dtest=PublicationControllerIT test`
Expected: FAIL — 404 on the new routes (endpoints don't exist).

- [ ] **Step 3: Implement the endpoints**

In `PublicationController.java`, add the `RetryFailedBatchUseCase` constructor dependency (append to the existing constructor params + assignment), and:

```java
    @GetMapping("/batches")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_READ)")
    public List<PublicationBatchSummaryDto> listBatches() {
        return publicationService.listBatches();
    }

    @GetMapping("/batches/{batchId}")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_READ)")
    public PublicationBatchDetailDto getBatch(@PathVariable UUID batchId) {
        return publicationService.getBatch(batchId);
    }

    @PostMapping("/batches/{batchId}/retry-failed")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublicationBatchDetailDto retryFailed(@PathVariable UUID batchId,
                                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return retryFailedBatchUseCase.execute(batchId, currentUser == null ? null : currentUser.id());
    }
```

Verify `NoSuchElementException` maps to 404 — check the project's `@RestControllerAdvice` / `ExceptionHandler`. If `NoSuchElementException` is not already mapped (the existing `get(UUID id)` throws it too), and the `unknown_batch_id_returns_404` test comes back 500, add `@ExceptionHandler(NoSuchElementException.class)` → 404 to the publication controller or the global advice, matching how `DomainException` is already handled.

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && mvn -q -Dtest=PublicationControllerIT test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/infrastructure/web/controllers/PublicationController.java \
  backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationControllerIT.java
git commit -m "feat(publication): batches list/detail/retry-failed endpoints"
```

- [ ] **Step 6: Full backend suite gate**

Run: `cd backend && mvn -q test`
Expected: BUILD SUCCESS. Fix any fallout (most likely other `DispatchResult` / `CreatePublicationCommand` / `PublicationDto` construction sites in unrelated tests — grep `new PublicationDto(` and `new CreatePublicationCommand(` and `new PublicationDispatcher.DispatchResult(` across `src/test` and add the trailing args).

```bash
git commit -am "test(publication): fix remaining record-construction call sites for H-2" # only if changes
```

---

## Task 8: `api.ts` — history client functions

**Files:**
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Consumes: `apiFetch<T>(path, init)`, `authHeaders(token)` (existing helpers).
- Produces (exact names/types the frontend tasks import):
  - `interface PublicationBatchSummary { batchId: string | null; campaignLabel: string | null; createdAt: string; platforms: Array<'INSTAGRAM' | 'FACEBOOK'>; total: number; published: number; failed: number; scheduled: number; pending: number; }`
  - `interface PublicationBatchDetailRow { publicationId: string; productId: string | null; productName: string; thumbnailUrl: string | null; platform: 'INSTAGRAM' | 'FACEBOOK'; status: string; externalPermalink: string | null; lastErrorCode: string | null; lastErrorMessage: string | null; }`
  - `interface PublicationBatchDetail { batchId: string | null; campaignLabel: string | null; captionTemplate: string | null; hashtags: string[]; createdAt: string; productIds: string[]; rows: PublicationBatchDetailRow[]; }`
  - `getPublicationBatches(token: string): Promise<PublicationBatchSummary[]>`
  - `getPublicationBatchDetail(batchId: string, token: string): Promise<PublicationBatchDetail>`
  - `retryBatchFailed(batchId: string, token: string): Promise<PublicationBatchDetail>`
  - `retryPublication(publicationId: string, token: string): Promise<unknown>`

- [ ] **Step 1: Add the interfaces + functions**

After `getProductPublicationImageHistory` (around line 1560) add:

```ts
export interface PublicationBatchSummary {
  batchId: string | null;
  campaignLabel: string | null;
  createdAt: string;
  platforms: Array<'INSTAGRAM' | 'FACEBOOK'>;
  total: number;
  published: number;
  failed: number;
  scheduled: number;
  pending: number;
}

export interface PublicationBatchDetailRow {
  publicationId: string;
  productId: string | null;
  productName: string;
  thumbnailUrl: string | null;
  platform: 'INSTAGRAM' | 'FACEBOOK';
  status: string;
  externalPermalink: string | null;
  lastErrorCode: string | null;
  lastErrorMessage: string | null;
}

export interface PublicationBatchDetail {
  batchId: string | null;
  campaignLabel: string | null;
  captionTemplate: string | null;
  hashtags: string[];
  createdAt: string;
  productIds: string[];
  rows: PublicationBatchDetailRow[];
}

/** Past publish batches, newest first — the "Historial" tab list. */
export async function getPublicationBatches(token: string): Promise<PublicationBatchSummary[]> {
  return apiFetch<PublicationBatchSummary[]>('/admin/publications/batches', {
    headers: authHeaders(token),
  });
}

export async function getPublicationBatchDetail(batchId: string, token: string): Promise<PublicationBatchDetail> {
  return apiFetch<PublicationBatchDetail>(`/admin/publications/batches/${encodeURIComponent(batchId)}`, {
    headers: authHeaders(token),
  });
}

/** Re-dispatch only the FAILED rows of a batch; returns the refreshed detail. */
export async function retryBatchFailed(batchId: string, token: string): Promise<PublicationBatchDetail> {
  return apiFetch<PublicationBatchDetail>(`/admin/publications/batches/${encodeURIComponent(batchId)}/retry-failed`, {
    method: 'POST',
    headers: authHeaders(token),
  });
}

/** Re-dispatch a single FAILED publication. */
export async function retryPublication(publicationId: string, token: string): Promise<unknown> {
  return apiFetch<unknown>(`/admin/publications/${encodeURIComponent(publicationId)}/retry`, {
    method: 'POST',
    headers: authHeaders(token),
  });
}
```

- [ ] **Step 2: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(frontend): api client for publication history (batches list/detail/retry)"
```

---

## Task 9: tab shell — extract `PublicarTab`, add `PublicacionesPage` shell

**Files:**
- Create: `frontend/src/islands/admin/PublicarTab.tsx` (moved body of the current `PublicacionesPage.tsx`)
- Modify: `frontend/src/islands/admin/PublicacionesPage.tsx` (becomes the shell)
- Rename: `frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx` → `PublicarTab.test.tsx` (and change the import)
- Create: `frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx` (NEW — shell/tab behavior)
- Modify: `frontend/src/pages/admin/publicaciones.astro` (intro copy)

**Interfaces:**
- Produces:
  - `PublicarTab` — default export, accepts optional prop
    `{ preload?: { productIds: string[]; captionTemplate: string; hashtags: string[]; campaignLabel: string | null }; onPreloadConsumed?: () => void }`.
  - `PublicacionesPage` — default export, the shell. No props. Owns `tab: 'publicar' | 'historial'` (URL-synced via `?tab=`) and `preload` state.

- [ ] **Step 1: Move the file**

```bash
cd frontend
git mv src/islands/admin/PublicacionesPage.tsx src/islands/admin/PublicarTab.tsx
git mv src/islands/admin/__tests__/PublicacionesPage.test.tsx src/islands/admin/__tests__/PublicarTab.test.tsx
```

In `PublicarTab.test.tsx`, change `import PublicacionesPage from '../PublicacionesPage';` to
`import PublicarTab from '../PublicarTab';` and replace every `<PublicacionesPage />` /
`render(<PublicacionesPage`  with `PublicarTab`. Rename the local variable if the file aliases it.
The `describe('PublicacionesPage', ...)` label can stay or be renamed — cosmetic.

- [ ] **Step 2: Rename the component + add the `preload` prop in `PublicarTab.tsx`**

- Rename `export default function PublicacionesPage()` → `export default function PublicarTab(props: PublicarTabProps)`.
- Add above it:

```tsx
type PublicarTabProps = {
  preload?: {
    productIds: string[];
    captionTemplate: string;
    hashtags: string[];
    campaignLabel: string | null;
  };
  onPreloadConsumed?: () => void;
};
```

- Add a mount effect that consumes `preload` once (place it after the existing state declarations,
  before the search `useEffect`):

```tsx
  useEffect(() => {
    const p = props.preload;
    if (!p) return;
    let cancelled = false;
    setCaptionTemplate(p.captionTemplate);
    setHashtagsInput(p.hashtags.join(' '));
    setCampaignLabel(p.campaignLabel ?? '');
    void Promise.all(p.productIds.map((id) => getProduct(id).catch(() => null))).then((loaded) => {
      if (cancelled) return;
      setSelected(() => {
        const next = new Map<string, ProductDto>();
        loaded.forEach((prod) => { if (prod) next.set(prod.id, prod); });
        return next;
      });
      props.onPreloadConsumed?.();
    });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [props.preload]);
```

- Add `getProduct` to the `import { ... } from '../../lib/api'` list.

- [ ] **Step 3: Write the shell + its test**

Create the NEW `frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx`:

```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import PublicacionesPage from '../PublicacionesPage';
import { getPublicationBatches } from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  getPublicationBatches: vi.fn().mockResolvedValue([]),
  getPublicationBatchDetail: vi.fn(),
  retryBatchFailed: vi.fn(),
  retryPublication: vi.fn(),
  searchProducts: vi.fn().mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, size: 24, number: 0 }),
  publishProductsBatch: vi.fn(),
  uploadMediaFile: vi.fn(),
  getProductPublicationImageHistory: vi.fn().mockResolvedValue([]),
  getProduct: vi.fn(),
}));
vi.mock('../../../lib/authStore', () => ({
  useAuthStore: () => ({ token: 't' }),
  readAuthTokenCookie: () => 't',
}));

beforeEach(() => {
  window.history.replaceState({}, '', '/admin/publicaciones');
  vi.mocked(getPublicationBatches).mockResolvedValue([]);
});

describe('PublicacionesPage shell', () => {
  it('shows the Publicar tab by default', () => {
    render(<PublicacionesPage />);
    expect(screen.getByRole('tab', { name: /publicar/i })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByPlaceholderText(/buscar producto/i)).toBeInTheDocument();
  });

  it('switches to Historial and writes ?tab=historial', async () => {
    const user = userEvent.setup();
    render(<PublicacionesPage />);
    await user.click(screen.getByRole('tab', { name: /historial/i }));
    expect(screen.getByRole('tab', { name: /historial/i })).toHaveAttribute('aria-selected', 'true');
    expect(new URLSearchParams(window.location.search).get('tab')).toBe('historial');
  });

  it('opens on Historial when the URL says so', () => {
    window.history.replaceState({}, '', '/admin/publicaciones?tab=historial');
    render(<PublicacionesPage />);
    expect(screen.getByRole('tab', { name: /historial/i })).toHaveAttribute('aria-selected', 'true');
  });
});
```

- [ ] **Step 4: Run to verify it fails**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/PublicacionesPage.test.tsx`
Expected: FAIL — `PublicacionesPage` still is the compose component (no tabs).

- [ ] **Step 5: Write the shell**

Replace `frontend/src/islands/admin/PublicacionesPage.tsx` entirely:

```tsx
import { useEffect, useState } from 'react';
import PublicarTab from './PublicarTab';
import HistorialTab from './HistorialTab';

type Tab = 'publicar' | 'historial';
type Preload = {
  productIds: string[];
  captionTemplate: string;
  hashtags: string[];
  campaignLabel: string | null;
};

function parseTab(raw: string | null): Tab {
  return raw?.toLowerCase() === 'historial' ? 'historial' : 'publicar';
}

export default function PublicacionesPage() {
  const [tab, setTab] = useState<Tab>('publicar');
  const [synced, setSynced] = useState(false);
  const [preload, setPreload] = useState<Preload | undefined>(undefined);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    setTab(parseTab(new URLSearchParams(window.location.search).get('tab')));
    setSynced(true);
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined' || !synced) return;
    const url = new URL(window.location.href);
    if (parseTab(url.searchParams.get('tab')) === tab) return;
    url.searchParams.set('tab', tab);
    window.history.replaceState(window.history.state, '', `${url.pathname}?${url.searchParams.toString()}`);
  }, [tab, synced]);

  function republish(next: Preload) {
    setPreload(next);
    setTab('publicar');
  }

  return (
    <div className="flex flex-col gap-5">
      <div role="tablist" aria-label="Publicaciones" className="flex gap-1 border-b border-pe-border">
        {(['publicar', 'historial'] as const).map((id) => (
          <button
            key={id}
            role="tab"
            type="button"
            aria-selected={tab === id}
            onClick={() => setTab(id)}
            className={[
              'px-3 py-2 text-sm -mb-px border-b-2 transition-colors',
              tab === id ? 'border-pe-rose text-pe-black' : 'border-transparent text-pe-muted hover:text-pe-black',
            ].join(' ')}
          >
            {id === 'publicar' ? 'Publicar' : 'Historial'}
          </button>
        ))}
      </div>

      {tab === 'publicar' && (
        <PublicarTab preload={preload} onPreloadConsumed={() => setPreload(undefined)} />
      )}
      {tab === 'historial' && <HistorialTab onRepublish={republish} onGoToPublish={() => setTab('publicar')} />}
    </div>
  );
}
```

(`HistorialTab` is Task 10 — this file will not typecheck until it exists. That is expected; do
Task 10 next, then return here for Step 6.)

- [ ] **Step 6: Update the astro intro copy**

`frontend/src/pages/admin/publicaciones.astro` — change the `<p>`:

```html
    <p class="font-sans text-[0.78rem] sm:text-sm text-pe-muted mt-1">
      Publica productos en Instagram y Facebook, y revisa el historial de lo que ya publicaste.
    </p>
```

- [ ] **Step 7: Run to verify (after Task 10)**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/PublicacionesPage.test.tsx src/islands/admin/__tests__/PublicarTab.test.tsx && npx tsc --noEmit`
Expected: PASS + clean.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/islands/admin/PublicarTab.tsx frontend/src/islands/admin/PublicacionesPage.tsx \
  frontend/src/islands/admin/__tests__/ frontend/src/pages/admin/publicaciones.astro
git commit -m "feat(frontend): Publicar|Historial tab shell on /admin/publicaciones"
```

---

## Task 10: `HistorialTab`

**Files:**
- Create: `frontend/src/islands/admin/HistorialTab.tsx`
- Create: `frontend/src/islands/admin/__tests__/HistorialTab.test.tsx`

**Interfaces:**
- Consumes: `getPublicationBatches`, `getPublicationBatchDetail`, `retryBatchFailed`, `retryPublication` (Task 8); `useAuthStore` / `readAuthTokenCookie` from `../../lib/authStore`.
- Produces: `HistorialTab` — default export, props
  `{ onRepublish: (p: { productIds: string[]; captionTemplate: string; hashtags: string[]; campaignLabel: string | null }) => void; onGoToPublish: () => void }`.

- [ ] **Step 1: Write the failing tests**

`frontend/src/islands/admin/__tests__/HistorialTab.test.tsx`:

```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import HistorialTab from '../HistorialTab';
import {
  getPublicationBatches,
  getPublicationBatchDetail,
  retryBatchFailed,
} from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  getPublicationBatches: vi.fn(),
  getPublicationBatchDetail: vi.fn(),
  retryBatchFailed: vi.fn(),
  retryPublication: vi.fn(),
}));
vi.mock('../../../lib/authStore', () => ({
  useAuthStore: () => ({ token: 't' }),
  readAuthTokenCookie: () => 't',
}));

const summary = {
  batchId: 'b1',
  campaignLabel: 'Liquidacion primavera',
  createdAt: new Date().toISOString(),
  platforms: ['INSTAGRAM', 'FACEBOOK'] as Array<'INSTAGRAM' | 'FACEBOOK'>,
  total: 2, published: 1, failed: 1, scheduled: 0, pending: 0,
};
const detail = {
  batchId: 'b1', campaignLabel: 'Liquidacion primavera', captionTemplate: '{producto}',
  hashtags: ['#pilarestilo'], createdAt: summary.createdAt, productIds: ['p1'],
  rows: [
    { publicationId: 'pub1', productId: 'p1', productName: 'Chaqueta', thumbnailUrl: 'https://img/x.jpg',
      platform: 'INSTAGRAM' as const, status: 'PUBLISHED', externalPermalink: 'https://www.instagram.com/p/ABC/',
      lastErrorCode: null, lastErrorMessage: null },
    { publicationId: 'pub2', productId: 'p1', productName: 'Chaqueta', thumbnailUrl: 'https://img/x.jpg',
      platform: 'FACEBOOK' as const, status: 'FAILED', externalPermalink: null,
      lastErrorCode: 'FACEBOOK_PUBLISH_ERROR', lastErrorMessage: 'OAuthException 190' },
  ],
};

beforeEach(() => {
  vi.mocked(getPublicationBatches).mockResolvedValue([summary] as never);
  vi.mocked(getPublicationBatchDetail).mockResolvedValue(detail as never);
  vi.mocked(retryBatchFailed).mockResolvedValue(detail as never);
});

describe('HistorialTab', () => {
  it('renders a batch card with its status summary', async () => {
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} />);
    expect(await screen.findByText('Liquidacion primavera')).toBeInTheDocument();
    expect(screen.getByText(/1 publicados/i)).toBeInTheDocument();
    expect(screen.getByText(/1 fallidos/i)).toBeInTheDocument();
  });

  it('shows an empty state with a link to Publicar', async () => {
    vi.mocked(getPublicationBatches).mockResolvedValue([]);
    const onGoToPublish = vi.fn();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={onGoToPublish} />);
    const btn = await screen.findByRole('button', { name: /publicar/i });
    await userEvent.setup().click(btn);
    expect(onGoToPublish).toHaveBeenCalled();
  });

  it('expands to rows and reveals the error detail on a failed row', async () => {
    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));

    expect(await screen.findByText('Chaqueta')).toBeInTheDocument();
    expect(screen.getAllByText(/publicado|falló/i).length).toBeGreaterThan(0);

    await user.click(screen.getByRole('button', { name: /ver detalle/i }));
    expect(screen.getByText(/OAuthException 190/)).toBeInTheDocument();
  });

  it('a published row links to the live post', async () => {
    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));
    const link = await screen.findByRole('link', { name: /ver en instagram/i });
    expect(link).toHaveAttribute('href', 'https://www.instagram.com/p/ABC/');
    expect(link).toHaveAttribute('target', '_blank');
  });

  it('retry-failed calls the endpoint and re-renders from its response', async () => {
    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));
    await user.click(await screen.findByRole('button', { name: /reintentar fallidos/i }));
    await waitFor(() => expect(retryBatchFailed).toHaveBeenCalledWith('b1', 't'));
  });

  it('volver a publicar esta tanda calls onRepublish with the batch data', async () => {
    const onRepublish = vi.fn();
    const user = userEvent.setup();
    render(<HistorialTab onRepublish={onRepublish} onGoToPublish={vi.fn()} />);
    await user.click(await screen.findByRole('button', { name: /liquidacion primavera/i }));
    await user.click(await screen.findByRole('button', { name: /volver a publicar esta tanda/i }));
    expect(onRepublish).toHaveBeenCalledWith(expect.objectContaining({
      productIds: ['p1'], captionTemplate: '{producto}', campaignLabel: 'Liquidacion primavera',
    }));
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/HistorialTab.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `HistorialTab.tsx`**

```tsx
import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, ChevronRight, Clock, ExternalLink, Loader2, RefreshCw, XCircle } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import {
  getPublicationBatches,
  getPublicationBatchDetail,
  retryBatchFailed,
  retryPublication,
  type PublicationBatchSummary,
  type PublicationBatchDetail,
} from '../../lib/api';

type Preload = {
  productIds: string[];
  captionTemplate: string;
  hashtags: string[];
  campaignLabel: string | null;
};
type Props = { onRepublish: (p: Preload) => void; onGoToPublish: () => void };

const PLATFORM_SHORT: Record<string, string> = { INSTAGRAM: 'IG', FACEBOOK: 'FB' };
const PLATFORM_NAME: Record<string, string> = { INSTAGRAM: 'Instagram', FACEBOOK: 'Facebook' };

function relativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const mins = Math.round(diffMs / 60000);
  if (mins < 1) return 'recién';
  if (mins < 60) return `hace ${mins} min`;
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return `hace ${hrs} h`;
  const days = Math.round(hrs / 24);
  return `hace ${days} d`;
}

function StatusPill({ status }: { status: string }) {
  if (status === 'PUBLISHED') {
    return <span className="inline-flex items-center gap-1 text-[0.72rem] px-1.5 py-0.5 bg-pe-positive-surface text-pe-positive-ink"><CheckCircle2 size={12} /> Publicado</span>;
  }
  if (status === 'FAILED') {
    return <span className="inline-flex items-center gap-1 text-[0.72rem] px-1.5 py-0.5 bg-pe-danger-surface text-pe-danger-ink"><XCircle size={12} /> Falló</span>;
  }
  if (status === 'SCHEDULED') {
    return <span className="inline-flex items-center gap-1 text-[0.72rem] px-1.5 py-0.5 bg-pe-warning-surface text-pe-warning-ink"><Clock size={12} /> Programado</span>;
  }
  return <span className="inline-flex items-center gap-1 text-[0.72rem] px-1.5 py-0.5 text-pe-muted"><Loader2 size={12} /> {status === 'PUBLISHING' ? 'Publicando' : status}</span>;
}

export default function HistorialTab({ onRepublish, onGoToPublish }: Props) {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie() ?? '';

  const [batches, setBatches] = useState<PublicationBatchSummary[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [details, setDetails] = useState<Map<string, PublicationBatchDetail>>(new Map());
  const [busyBatch, setBusyBatch] = useState<string | null>(null);
  const [openError, setOpenError] = useState<Set<string>>(new Set());

  function load() {
    setLoadError(null);
    setBatches(null);
    getPublicationBatches(effectiveToken)
      .then(setBatches)
      .catch(() => setLoadError('No se pudo cargar el historial.'));
  }
  useEffect(load, [effectiveToken]);

  async function toggle(batchId: string | null) {
    if (batchId === null) return; // "Sin tanda" group is not expandable
    if (expanded === batchId) { setExpanded(null); return; }
    setExpanded(batchId);
    if (!details.has(batchId)) {
      try {
        const d = await getPublicationBatchDetail(batchId, effectiveToken);
        setDetails((prev) => new Map(prev).set(batchId, d));
      } catch {
        setDetails((prev) => prev);
      }
    }
  }

  async function doRetryBatch(batchId: string) {
    setBusyBatch(batchId);
    try {
      const d = await retryBatchFailed(batchId, effectiveToken);
      setDetails((prev) => new Map(prev).set(batchId, d));
      load();
    } finally {
      setBusyBatch(null);
    }
  }

  async function doRetryRow(batchId: string, publicationId: string) {
    setBusyBatch(batchId);
    try {
      await retryPublication(publicationId, effectiveToken);
      const d = await getPublicationBatchDetail(batchId, effectiveToken);
      setDetails((prev) => new Map(prev).set(batchId, d));
    } finally {
      setBusyBatch(null);
    }
  }

  if (loadError) {
    return (
      <div className="text-sm text-pe-danger-ink">
        {loadError} <button type="button" onClick={load} className="underline">Reintentar</button>
      </div>
    );
  }
  if (batches === null) {
    return (
      <ul className="flex flex-col gap-2" aria-busy="true">
        {[0, 1, 2].map((i) => <li key={i} className="h-14 bg-pe-surface border border-pe-border animate-pulse" />)}
      </ul>
    );
  }
  if (batches.length === 0) {
    return (
      <div className="text-sm text-pe-muted flex flex-col items-start gap-2">
        <p>Todavia no publicaste ninguna tanda.</p>
        <button type="button" onClick={onGoToPublish} className="bg-pe-rose text-pe-white px-3 py-1.5 rounded-xs text-sm">
          Ir a Publicar
        </button>
      </div>
    );
  }

  return (
    <ul className="flex flex-col gap-2">
      {batches.map((b) => {
        const key = b.batchId ?? '__none__';
        const isOpen = expanded === b.batchId;
        const detail = b.batchId ? details.get(b.batchId) : undefined;
        return (
          <li key={key} className="border border-pe-border">
            <button
              type="button"
              aria-expanded={b.batchId ? isOpen : undefined}
              disabled={b.batchId === null}
              onClick={() => toggle(b.batchId)}
              className="w-full flex items-center gap-3 p-3 text-left disabled:cursor-default"
            >
              {b.batchId !== null && (
                <ChevronRight
                  size={16}
                  className={['text-pe-muted shrink-0 transition-transform motion-reduce:transition-none', isOpen ? 'rotate-90' : ''].join(' ')}
                />
              )}
              <div className="flex-1 min-w-0">
                <p className={b.campaignLabel ? 'text-sm font-medium truncate' : 'text-sm text-pe-muted'}>
                  {b.campaignLabel ?? 'Sin campaña'}
                </p>
                <p className="text-[0.72rem] text-pe-muted" title={new Date(b.createdAt).toLocaleString('es-CL')}>
                  {relativeTime(b.createdAt)}
                </p>
              </div>
              <div className="flex items-center gap-1.5 shrink-0">
                {b.platforms.map((p) => (
                  <span key={p} className="text-[0.66rem] px-1.5 py-0.5 bg-pe-surface border border-pe-border text-pe-muted">
                    {PLATFORM_SHORT[p] ?? p}
                  </span>
                ))}
              </div>
              <p className="text-[0.78rem] shrink-0 tabular-nums">
                <span className="text-pe-positive-ink">{b.published} publicados</span>
                {b.failed > 0 && <span className="text-pe-danger-ink"> · {b.failed} fallidos</span>}
                {b.scheduled > 0 && <span className="text-pe-warning-ink"> · {b.scheduled} programados</span>}
              </p>
            </button>

            {isOpen && b.batchId && (
              <div className="border-t border-pe-border p-3 flex flex-col gap-3">
                <div className="flex flex-wrap gap-2">
                  {detail && detail.rows.some((r) => r.status === 'FAILED') && (
                    <button
                      type="button"
                      onClick={() => doRetryBatch(b.batchId!)}
                      disabled={busyBatch === b.batchId}
                      className="inline-flex items-center gap-1.5 text-[0.78rem] bg-pe-rose text-pe-white px-2.5 py-1 rounded-xs disabled:opacity-50"
                    >
                      {busyBatch === b.batchId ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
                      Reintentar fallidos
                    </button>
                  )}
                  {detail && detail.captionTemplate && (
                    <button
                      type="button"
                      onClick={() => onRepublish({
                        productIds: detail.productIds,
                        captionTemplate: detail.captionTemplate!,
                        hashtags: detail.hashtags,
                        campaignLabel: detail.campaignLabel,
                      })}
                      className="text-[0.78rem] border border-pe-border px-2.5 py-1 rounded-xs hover:border-pe-rose"
                    >
                      Volver a publicar esta tanda
                    </button>
                  )}
                </div>

                {!detail && <p className="text-xs text-pe-muted">Cargando…</p>}
                {detail && (
                  <ul className="flex flex-col divide-y divide-pe-border">
                    {detail.rows.map((r) => {
                      const errKey = `${b.batchId}:${r.publicationId}`;
                      return (
                        <li key={r.publicationId} className="py-2 flex items-center gap-3">
                          {r.thumbnailUrl
                            ? <img src={r.thumbnailUrl} alt="" className="w-8 h-10 object-cover shrink-0" />
                            : <div className="w-8 h-10 bg-pe-surface shrink-0" />}
                          <span className="flex-1 min-w-0 truncate text-sm">{r.productName}</span>
                          <span className="text-[0.7rem] text-pe-muted shrink-0">{PLATFORM_SHORT[r.platform] ?? r.platform}</span>
                          <StatusPill status={r.status} />
                          <div className="shrink-0 flex items-center gap-2">
                            {r.status === 'FAILED' && (
                              <>
                                <button
                                  type="button"
                                  onClick={() => doRetryRow(b.batchId!, r.publicationId)}
                                  disabled={busyBatch === b.batchId}
                                  className="text-[0.72rem] text-pe-rose hover:underline disabled:opacity-50"
                                >
                                  Reintentar
                                </button>
                                <button
                                  type="button"
                                  onClick={() => setOpenError((prev) => {
                                    const next = new Set(prev);
                                    next.has(errKey) ? next.delete(errKey) : next.add(errKey);
                                    return next;
                                  })}
                                  className="text-[0.72rem] text-pe-muted hover:underline"
                                >
                                  ver detalle
                                </button>
                              </>
                            )}
                            {r.status === 'PUBLISHED' && r.externalPermalink && (
                              <a
                                href={r.externalPermalink}
                                target="_blank"
                                rel="noreferrer"
                                className="inline-flex items-center gap-1 text-[0.72rem] text-pe-muted hover:text-pe-rose"
                              >
                                <ExternalLink size={11} /> Ver en {PLATFORM_NAME[r.platform] ?? r.platform}
                              </a>
                            )}
                          </div>
                          {openError.has(errKey) && (
                            <p className="basis-full text-[0.72rem] text-pe-muted font-mono pl-11">
                              {r.lastErrorCode}: {r.lastErrorMessage}
                            </p>
                          )}
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}
```

Note the `useMemo` import is unused above — remove it (kept the import line minimal: `import { useEffect, useState } from 'react';`).

- [ ] **Step 4: Run to verify it passes**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/HistorialTab.test.tsx`
Expected: PASS. Adjust the error-detail sub-row markup if the test's `basis-full` flex wrap makes `getByText` ambiguous — the test asserts on `/OAuthException 190/` which only appears there.

- [ ] **Step 5: Typecheck + commit**

Run: `cd frontend && npx tsc --noEmit`
Expected: clean (now that `HistorialTab` exists, the shell from Task 9 also typechecks).

```bash
git add frontend/src/islands/admin/HistorialTab.tsx frontend/src/islands/admin/__tests__/HistorialTab.test.tsx
git commit -m "feat(frontend): HistorialTab — grouped publish history with retry and permalinks"
```

Then finish Task 9 Step 7–8 (run both shell + PublicarTab tests, commit the shell).

---

## Task 11: full-suite gate + docs

**Files:**
- Modify: `docs/superpowers/specs/2026-09-05-social-publishing-h2-history-design.md` (mark implemented — optional)
- No code.

- [ ] **Step 1: Backend full suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS. Investigate any failure — do not proceed past red.

- [ ] **Step 2: Frontend full suite + typecheck**

Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: both clean/green.

- [ ] **Step 3: Manual smoke (local Docker, optional but recommended)**

Per `CLAUDE.md`, bring the stack up and click through: publish a small batch, open Historial,
expand it, hit "ver detalle" on a failed row, click "Volver a publicar esta tanda", confirm the
Publicar tab is pre-loaded. (Meta calls will fail without real creds — that's the expected FAILED
path and is exactly what the history should show.)

- [ ] **Step 4: Commit any doc touch-ups, then hand off**

```bash
git add -A
git commit -m "docs: mark H-2 publication history implemented" # only if there are changes
```

The branch is `develop` (this project commits directly to `develop`; master merge + deploy is a
separate explicit step the user requests).

---

## Self-Review

**1. Spec coverage:**
- `publication_batches` table + `batch_id` + `external_permalink` → Task 1. ✅
- No backfill / pre-flight empty check → Global Constraints + Task 11 note. ✅
- `PublicationBatchEntity` (bare UUID, no association) → Task 1. ✅
- `CreatePublicationCommand.batchId`, `PublicationEntity` fields → Task 2. ✅
- Permalink capture (IG extra GET that can't fail the publish; FB from `post_id`) → Task 3. ✅
- `PublicationDto.externalPermalink` → Task 2. ✅
- `PublishProductsBatchUseCase` creates the batch row → Task 4. ✅
- `PublicationBatchSummaryDto` / `PublicationBatchDetailDto` (+ nested `Row`) → Task 5. ✅
- `listBatches` / `getBatch` on `PublicationService`; N+1 avoidance; synthetic null-batch group; `getBatch(null)` unsupported → Task 5. ✅
- `RetryFailedBatchUseCase` (non-transactional, skips raced rows) → Task 6. ✅
- Three endpoints with existing permission checks; "volver a publicar" needs no endpoint → Task 7 + Task 9/10. ✅
- Tab shell with URL sync mirroring `SystemSettingsPanel` → Task 9. ✅
- `PublicarTab` preload via `Promise.all(getProduct)` → Task 9. ✅
- `HistorialTab`: collapsed card, expand fetch+cache, status pill icon+word, contextual row actions, error sub-row, permalink link, retry-failed header action, empty state, skeleton → Task 10. ✅
- `api.ts` four functions → Task 8. ✅
- Testing matrix (unit + IT + RTL) → Tasks 3,4,5,6,7,9,10. ✅
- `notification-service` `validate` tolerance note → Global Constraints + Task 1 (implicit via the IT boot). ✅

**2. Placeholder scan:** No "TBD"/"handle edge cases"/bare "add validation". The one soft spot —
"check the project's `@RestControllerAdvice`" in Task 7 Step 3 — is a concrete conditional
instruction with the fallback action spelled out (add `@ExceptionHandler(NoSuchElementException)`
→ 404). Acceptable. The `findByBatchId...(null)` derived-query caveat in Task 5 names the exact
fallback method to add.

**3. Type consistency:**
- `DispatchResult` 7-arg shape used identically in Task 3 (port), adapters, and all test
  construction sites listed. ✅
- `CreatePublicationCommand` 14-arg shape: Task 2 defines it, Task 2 Step 6 + Task 4 Step 3 pass
  `batchId` last. ✅
- `PublicationBatchDetailDto` field order (`batchId, campaignLabel, captionTemplate, hashtags,
  createdAt, productIds, rows`) identical in Task 5 (def), Task 6 test (construction), Task 8 (TS
  interface), Task 10 (consumption). ✅
- `PublicationBatchSummary` TS fields match `PublicationBatchSummaryDto` Java fields 1:1. ✅
- `HistorialTab` props (`onRepublish`, `onGoToPublish`) match the shell's usage in Task 9. ✅
- `PublicarTab` prop `preload` shape matches the shell's `Preload` type and `onRepublish`
  payload. ✅
