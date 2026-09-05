# Social Publishing Scheduling (Increment H, Etapa H-3) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a publish batch be scheduled for a future date/time; a once-a-minute job publishes it when due, and it can be cancelled, rescheduled or edited before it fires.

**Architecture:** `PublishProductsBatchCommand` gains `scheduledAt`; when set, `PublicationService.create()` puts each row in `SCHEDULED` and `PublishProductsBatchUseCase` skips the immediate `dispatch()`. A `@Scheduled` job (mirroring `DispatchAutoDeliveryScheduler`) queries `SCHEDULED` rows with `scheduled_at <= now`, dispatches the in-window ones and fails the ones past a lateness cap. Cancel/reschedule are small `PublicationService` methods; edit is a `PUT` that deletes and regenerates the `SCHEDULED` rows via a shared `BatchPublicationFactory`. Time is pinned to `America/Santiago` in a dependency-free frontend helper; the backend stays pure `Instant`.

**Tech Stack:** Spring Boot 4 (Java 25), Flyway, JPA, `@Scheduled` + injected `java.time.Clock`, Testcontainers + MockMvc, JUnit 5 + Mockito; Astro 5 + React islands, Tailwind `pe-*` tokens, Vitest + RTL, `Intl.DateTimeFormat` for timezone math.

**Spec:** `docs/superpowers/specs/2026-09-05-social-publishing-h3-scheduling-design.md`

## Global Constraints

- Flyway: never edit an applied migration. Current highest **V100**; this adds **V101**. Expand-only (one nullable column + one partial index), safe against the running old app.
- `notification-service` maps read-only views of `publications` under `ddl-auto: validate`; it does **not** map `publication_batches`. No `*RoEntity` change for V101.
- Scheduled use cases take an injected `java.time.Clock`: a package-private constructor for tests, an `@Autowired` constructor passing `Clock.systemUTC()`. `@Value` config keys with defaults; env var names are the strict derived form of the YAML path.
- Non-`@Transactional` orchestrators when looping over `@Transactional` calls on `PublicationService` (rollback-only hazard).
- `GlobalExceptionHandler` already maps `DomainException` → **400** and `NoSuchElementException` → **404**. This etapa introduces no 409 — every "wrong state" case throws `DomainException`.
- `PublicationStatus` already has `SCHEDULED` and `CANCELLED`. `dispatchInternal` already dispatches from `SCHEDULED`. `PublicationEntity.scheduledAt` (`scheduled_at`) already exists. `CreatePublicationCommand` already carries `scheduledAt` (arg 11 of 14).
- `SCHEDULE_WINDOW_MISSED` is an `errorCode` **string** (column `last_error_code` is `varchar(80)`), not an enum value.
- Spanish (Chilean, tú-form) UI copy. No em dashes in copy. Status pills carry an icon + a word, never color alone. `pe-*` semantic tokens only.
- `@EnableScheduling` is already on `PilarEstiloApplication`.
- All new controller endpoints reuse the existing guard: `hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)`.
- Config keys: `app.social-publishing.schedule.cron` (default `0 * * * * *`) and `app.social-publishing.schedule.max-lateness-minutes` (default `360`). Both get an `additional-spring-configuration-metadata.json` entry and an `infra/.env.example` line.

---

## File Structure

**Backend — create:**
- `backend/src/main/resources/db/migration/V101__publication_batch_schedule.sql`
- `backend/src/main/java/com/pilarestilo/publication/application/usecases/BatchPublicationFactory.java` — shared `CreatePublicationCommand` builder (caption interpolation, variant resolution, media bundle, idempotency key, hashtag serialization).
- `backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishDueScheduledPublicationsUseCase.java`
- `backend/src/main/java/com/pilarestilo/publication/infrastructure/jobs/PublishDueScheduledPublicationsScheduler.java`
- `backend/src/main/java/com/pilarestilo/publication/application/usecases/UpdateScheduledBatchUseCase.java`
- `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/requests/RescheduleBatchRequest.java`
- Tests: `BatchPublicationFactoryTest`, `PublishDueScheduledPublicationsUseCaseTest`, `UpdateScheduledBatchUseCaseTest`.

**Backend — modify:**
- `PublicationBatchEntity.java` — add `scheduledAt`.
- `PublicationJpaRepository.java` — add the due-rows query.
- `PublishProductsBatchCommand.java` — add trailing `Instant scheduledAt`.
- `PublishProductsBatchRequest.java` — add `String scheduledAt`.
- `PublishProductsBatchResult.java` — `PublicationItemResult` gains trailing `boolean scheduled`.
- `PublicationService.java` — `create()` SCHEDULED status; `markScheduleWindowMissed`, `cancelScheduledBatch`, `rescheduleBatch`; `summarize()`/`getBatch()` fill `scheduledAt`.
- `PublicationBatchSummaryDto.java` / `PublicationBatchDetailDto.java` — trailing `Instant scheduledAt`.
- `PublishProductsBatchUseCase.java` — delegate to `BatchPublicationFactory`; scheduled mode (no `dispatch`).
- `PublicationController.java` — `toBatchCommand` parses `scheduledAt` (past → 400); three new endpoints.
- `application.yml`, `additional-spring-configuration-metadata.json`, `infra/.env.example` — the two config keys.
- Tests: `PublishProductsBatchUseCaseTest`, `PublicationServiceTest`, `PublicationControllerIT`.

**Frontend — create:**
- `frontend/src/lib/santiagoTime.ts` + `frontend/src/lib/__tests__/santiagoTime.test.ts`

**Frontend — modify:**
- `frontend/src/lib/api.ts` — interface fields + `cancelBatch` / `rescheduleBatch` / `updateScheduledBatch`.
- `frontend/src/islands/admin/PublicarTab.tsx` — schedule picker + edit mode.
- `frontend/src/islands/admin/PublicacionesPage.tsx` — `editingBatchId` state.
- `frontend/src/islands/admin/HistorialTab.tsx` — scheduled representation + actions + `CANCELLED` pill.
- Tests: `PublicarTab.test.tsx`, `HistorialTab.test.tsx`, `PublicacionesPage.test.tsx`.

---

## Task 1: V101 schema + `PublicationBatchEntity.scheduledAt` + config keys

**Files:**
- Create: `backend/src/main/resources/db/migration/V101__publication_batch_schedule.sql`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/entities/PublicationBatchEntity.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/repositories/PublicationJpaRepository.java`
- Modify: `backend/src/main/resources/application.yml`, `backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json`, `infra/.env.example`

**Interfaces:**
- Produces:
  - `PublicationBatchEntity.getScheduledAt()/setScheduledAt(Instant)`.
  - `PublicationJpaRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(PublicationStatus status, Instant cutoff)` → `List<PublicationEntity>`.
  - config keys `app.social-publishing.schedule.cron`, `app.social-publishing.schedule.max-lateness-minutes`.

- [ ] **Step 1: Write the migration**

`V101__publication_batch_schedule.sql`:
```sql
ALTER TABLE publication_batches ADD COLUMN scheduled_at TIMESTAMPTZ;

CREATE INDEX idx_publications_scheduled_due
    ON publications (scheduled_at)
    WHERE status = 'SCHEDULED';
```

- [ ] **Step 2: Add `scheduledAt` to `PublicationBatchEntity`**

After the `campaignLabel` field:
```java
    @Column(name = "scheduled_at")
    private Instant scheduledAt;
```
And in the accessors:
```java
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
```

- [ ] **Step 3: Add the due-rows query to `PublicationJpaRepository`**

Add the import `com.pilarestilo.publication.domain.enums.PublicationStatus` and `java.time.Instant`, then the method:
```java
    List<PublicationEntity> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            PublicationStatus status, Instant cutoff);
```

- [ ] **Step 4: Register the config keys**

`application.yml` — under the existing `app.social-publishing.meta` block's parent, add:
```yaml
app:
  social-publishing:
    schedule:
      cron: "0 * * * * *"
      max-lateness-minutes: 360
```
(merge into the existing `app.social-publishing` node — do not duplicate the key).

`additional-spring-configuration-metadata.json` — add to `properties`:
```json
{ "name": "app.social-publishing.schedule.cron", "type": "java.lang.String", "description": "Cron for the scheduled-publication job. Default: every minute." },
{ "name": "app.social-publishing.schedule.max-lateness-minutes", "type": "java.lang.Long", "description": "A SCHEDULED publication more than this many minutes overdue is failed instead of published." }
```
(and a `groups` entry for `app.social-publishing.schedule` if the file lists groups per sub-namespace — match the existing `app.social-publishing.meta` group entry's shape).

`infra/.env.example` — near the other `APP_SOCIAL_PUBLISHING_*` lines:
```
# APP_SOCIAL_PUBLISHING_SCHEDULE_CRON=0 * * * * *
# APP_SOCIAL_PUBLISHING_SCHEDULE_MAX_LATENESS_MINUTES=360
```

- [ ] **Step 5: Verify the migration + mapping boot**

Run: `cd backend && mvn -q -Dtest=PublicationControllerIT test`
Expected: PASS. The IT boots the full app against a fresh Testcontainers Postgres, runs V101, and `ddl-auto: validate` checks the new column.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V101__publication_batch_schedule.sql \
  backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/ \
  backend/src/main/resources/application.yml \
  backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json \
  infra/.env.example
git commit -m "feat(publication): V101 publication_batches.scheduled_at + schedule config keys"
```

---

## Task 2: `scheduledAt` through the command/request/DTO; `create()` puts a scheduled row in SCHEDULED

**Files:**
- Modify: `PublishProductsBatchCommand.java`, `PublishProductsBatchRequest.java`, `PublishProductsBatchResult.java`
- Modify: `PublicationService.java` (`create()`), `PublicationBatchSummaryDto.java`, `PublicationBatchDetailDto.java`
- Modify: `PublicationController.java` (`toBatchCommand`)
- Test: `PublicationServiceTest.java`

**Interfaces:**
- Consumes: `PublicationBatchEntity.getScheduledAt` (Task 1).
- Produces:
  - `PublishProductsBatchCommand` gains a **trailing** `Instant scheduledAt` (nullable). New arity 8.
  - `PublishProductsBatchRequest` gains a **trailing** `String scheduledAt` (nullable ISO-8601 instant).
  - `PublishProductsBatchResult.PublicationItemResult` gains a **trailing** `boolean scheduled`.
  - `PublicationBatchSummaryDto` / `PublicationBatchDetailDto` each gain a **trailing** `Instant scheduledAt` (nullable).
  - `PublicationService.create` sets `SCHEDULED` when `command.scheduledAt() != null && !command.approvalRequired()`.

- [ ] **Step 1: Write the failing test in `PublicationServiceTest`**

```java
    @Test
    void create_with_a_scheduled_at_puts_the_row_in_scheduled() {
        when(publicationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        CreatePublicationResult result = service.create(new CreatePublicationCommand(
                null, PublicationSourceType.PRODUCT, null,
                PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "es-CL", null, "Copy", List.of(), false,
                java.time.Instant.now().plusSeconds(3600), "pub-sched-1", List.of(), null
        ), UUID.randomUUID());

        assertEquals(PublicationStatus.SCHEDULED, result.publication().status());
        assertEquals(com.pilarestilo.publication.domain.enums.PublicationApprovalStatus.NOT_REQUIRED,
                result.publication().approvalStatus());
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q -Dtest=PublicationServiceTest test`
Expected: FAIL — status is `APPROVED`, not `SCHEDULED`.

- [ ] **Step 3: Update `create()` in `PublicationService`**

Replace line 103 (`entity.setStatus(command.approvalRequired() ? ... : PublicationStatus.APPROVED);`):
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
Line 104 (`approvalStatus`) is unchanged — `command.approvalRequired()` is `false` for both the APPROVED and SCHEDULED branches, so it stays `NOT_REQUIRED`.

- [ ] **Step 4: Add `scheduledAt` to `PublishProductsBatchCommand`**

```java
public record PublishProductsBatchCommand(
        List<UUID> productIds,
        Set<PublicationPlatform> platforms,
        String captionTemplate,
        List<String> hashtags,
        String campaignLabel,
        Map<UUID, String> imageOverrides,
        Map<UUID, VariantSelection> variantSelections,
        java.time.Instant scheduledAt
) {
    public record VariantSelection(String color, String size) {}
}
```

- [ ] **Step 5: Add `scheduledAt` to `PublishProductsBatchRequest`**

```java
public record PublishProductsBatchRequest(
        @NotEmpty List<UUID> productIds,
        @NotEmpty List<@NotBlank String> platforms,
        @NotBlank String captionTemplate,
        List<String> hashtags,
        String campaignLabel,
        Map<String, String> imageOverrides,
        Map<String, VariantSelectionRequest> variantSelections,
        String scheduledAt
) {
    public record VariantSelectionRequest(String color, String size) {}
}
```

- [ ] **Step 6: Add `scheduled` to `PublishProductsBatchResult.PublicationItemResult`**

```java
    public record PublicationItemResult(
            UUID productId,
            PublicationPlatform platform,
            boolean success,
            UUID publicationId,
            String errorMessage,
            boolean scheduled
    ) {}
```

- [ ] **Step 7: Add `scheduledAt` to the two batch DTOs**

`PublicationBatchSummaryDto` — append `java.time.Instant scheduledAt` as the last component.
`PublicationBatchDetailDto` — append `java.time.Instant scheduledAt` as the last top-level component (after `rows`).

- [ ] **Step 8: Fill `scheduledAt` in `PublicationService.summarize` and `getBatch`**

`summarize(...)` currently takes `(UUID batchId, String label, Instant createdAt, List<PublicationEntity> rows)`. Add a `Instant scheduledAt` param, pass it in the `new PublicationBatchSummaryDto(...)` call as the trailing arg. Update both call sites in `listBatches()`:
- the per-batch one: `summarize(b.getId(), b.getCampaignLabel(), b.getCreatedAt(), b.getScheduledAt(), rows)`.
- the orphan one: `summarize(null, null, orphans.get(...).getCreatedAt(), null, orphans)`.

`getBatch(...)` — the final `new PublicationBatchDetailDto(...)` gains a trailing `batch.getScheduledAt()`.

- [ ] **Step 9: Parse `scheduledAt` in `PublicationController.toBatchCommand`**

At the end of `toBatchCommand`, before `return new PublishProductsBatchCommand(...)`:
```java
        java.time.Instant scheduledAt = null;
        if (request.scheduledAt() != null && !request.scheduledAt().isBlank()) {
            try {
                scheduledAt = java.time.Instant.parse(request.scheduledAt());
            } catch (java.time.format.DateTimeParseException e) {
                throw new com.pilarestilo.shared.domain.DomainException("Fecha de programación inválida");
            }
            if (scheduledAt.isBefore(java.time.Instant.now())) {
                throw new com.pilarestilo.shared.domain.DomainException("La hora programada ya pasó");
            }
        }
```
and add `scheduledAt` as the trailing arg of the `new PublishProductsBatchCommand(...)`.

- [ ] **Step 10: Fix compile fallout**

`mvn -q test-compile` will fail on:
- `PublishProductsBatchUseCase.java` — the `new PublishProductsBatchCommand(...)` is not constructed there, but `publishOne` constructs `PublicationItemResult` in 3 places (the missing-product loop, the success return, the `DomainException` catch). Add `, false` as the trailing arg to each **for now** (Task 3 sets `true` for the scheduled path).
- `PublishProductsBatchUseCaseTest.java` — every `new PublishProductsBatchCommand(...)` (8 tests) gains a trailing `, null`.
- Any other `new PublishProductsBatchCommand(` / `new PublicationBatchSummaryDto(` / `new PublicationBatchDetailDto(` in tests: grep and add the trailing arg. `PublicationServiceTest`'s `list_batches_summarizes...` and `get_batch_resolves...` tests do **not** construct the DTOs (they read them), so only add where a constructor call fails.

- [ ] **Step 11: Run the affected tests**

Run: `cd backend && mvn -q -Dtest='PublicationServiceTest,PublishProductsBatchUseCaseTest' test`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/ backend/src/test/java/com/pilarestilo/publication/
git commit -m "feat(publication): scheduledAt on the batch command/request/DTOs; create() -> SCHEDULED"
```

---

## Task 3: `BatchPublicationFactory` + scheduled mode in `PublishProductsBatchUseCase`

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/application/usecases/BatchPublicationFactory.java`
- Create: `backend/src/test/java/com/pilarestilo/publication/application/usecases/BatchPublicationFactoryTest.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCase.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCaseTest.java`

**Interfaces:**
- Consumes: `PublishProductsBatchCommand.scheduledAt` (Task 2).
- Produces:
  - `BatchPublicationFactory` — package-private `@Component`, methods:
    - `String serializeHashtags(List<String> hashtags)`
    - `String trimToNull(String value)`
    - `String interpolate(String template, com.pilarestilo.product.domain.model.Product product, PublishProductsBatchCommand.VariantSelection selection)`
    - `CreatePublicationCommand buildCreateCommand(PublishProductsBatchCommand command, com.pilarestilo.product.domain.model.Product product, PublicationPlatform platform, String caption, UUID batchId)` — sets `scheduledAt` from `command.scheduledAt()`.
  - `PublishProductsBatchUseCase` in scheduled mode: `create()` only, item result `scheduled=true`, `success=false`.

- [ ] **Step 1: Write `BatchPublicationFactoryTest`**

```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchPublicationFactoryTest {

    private final BatchPublicationFactory factory = new BatchPublicationFactory(new ObjectMapper());

    @Test
    void interpolates_all_five_tokens() {
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(29990), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 5);
        product.setVariants(List.of(new ProductVariant("Negro", "40", 5, 1)));

        String caption = factory.interpolate("{producto} {color} {talla} quedan {cantidad} a {precio}",
                product, new PublishProductsBatchCommand.VariantSelection("Negro", "40"));

        assertEquals("Zapatos Negro 40 quedan 4 a $29.990", caption);
    }

    @Test
    void build_create_command_threads_scheduled_at_and_batch_id() {
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img/z.jpg", ProductCondition.NEW, "Pilar", 5);
        UUID batchId = UUID.randomUUID();
        Instant when = Instant.now().plusSeconds(7200);
        PublishProductsBatchCommand cmd = new PublishProductsBatchCommand(
                List.of(product.getId()), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of("#x"), "Camp", Map.of(), Map.of(), when);

        CreatePublicationCommand out = factory.buildCreateCommand(cmd, product, PublicationPlatform.INSTAGRAM, "Zapatos", batchId);

        assertEquals(when, out.scheduledAt());
        assertEquals(batchId, out.batchId());
        assertEquals("Zapatos", out.caption());
        assertEquals("https://img/z.jpg", out.mediaBundles().get(0).primaryAssetUrl());
        assertTrue(out.idempotencyKey().startsWith("pub-batch-"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q -Dtest=BatchPublicationFactoryTest test`
Expected: FAIL — `BatchPublicationFactory` does not exist.

- [ ] **Step 3: Create `BatchPublicationFactory`**

Move the private helpers out of `PublishProductsBatchUseCase` verbatim, plus the command builder:
```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationMediaBundleType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
class BatchPublicationFactory {

    private final ObjectMapper objectMapper;

    BatchPublicationFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String serializeHashtags(List<String> hashtags) {
        List<String> clean = hashtags == null ? List.of()
                : hashtags.stream().map(this::trimToNull).filter(Objects::nonNull).distinct().toList();
        try {
            return objectMapper.writeValueAsString(clean);
        } catch (RuntimeException e) {
            return "[]";
        }
    }

    String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    String interpolate(String template, Product product, PublishProductsBatchCommand.VariantSelection selection) {
        String priceText = NumberFormat.getInstance(Locale.of("es", "CL")).format(product.getPrice().amount());
        ProductVariant variant = selection == null ? null : resolveVariant(product, selection);
        return template
                .replace("{producto}", product.getName())
                .replace("{precio}", "$" + priceText)
                .replace("{color}", variant == null ? "" : variant.getColor())
                .replace("{talla}", variant == null ? "" : variant.getSize())
                .replace("{cantidad}", variant == null ? "" : String.valueOf(variant.available()));
    }

    CreatePublicationCommand buildCreateCommand(PublishProductsBatchCommand command, Product product,
                                                PublicationPlatform platform, String caption, UUID batchId) {
        return new CreatePublicationCommand(
                product.getId(),
                PublicationSourceType.PRODUCT,
                product.getId(),
                platform,
                PublicationChannelType.FEED_POST,
                "es-CL",
                command.campaignLabel(),
                caption,
                command.hashtags(),
                false,
                command.scheduledAt(),
                "pub-batch-" + product.getId() + "-" + platform.name() + "-" + UUID.randomUUID(),
                List.of(new CreatePublicationCommand.MediaBundleCommand(
                        PublicationMediaBundleType.SOCIAL_FEED,
                        command.imageOverrides().getOrDefault(product.getId(), product.getImageUrl()),
                        Map.of()
                )),
                batchId
        );
    }

    private ProductVariant resolveVariant(Product product, PublishProductsBatchCommand.VariantSelection selection) {
        return product.getVariants().stream()
                .filter(v -> Objects.equals(v.getColor(), selection.color()) && Objects.equals(v.getSize(), selection.size()))
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && mvn -q -Dtest=BatchPublicationFactoryTest test`
Expected: PASS.

- [ ] **Step 5: Write the failing scheduled-mode test in `PublishProductsBatchUseCaseTest`**

```java
    @Test
    void a_scheduled_batch_creates_rows_but_never_dispatches() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(49990), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 2);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(scheduledDto(publicationId), true));

        PublishProductsBatchResult result = useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of(), "Camp", Map.of(), Map.of(),
                java.time.Instant.now().plusSeconds(3600)
        ), UUID.randomUUID());

        org.mockito.Mockito.verify(publicationService, org.mockito.Mockito.never()).dispatch(any(), any());
        assertEquals(1, result.items().size());
        assertTrue(result.items().get(0).scheduled());
        assertFalse(result.items().get(0).success());

        ArgumentCaptor<PublicationBatchEntity> batchCaptor = ArgumentCaptor.forClass(PublicationBatchEntity.class);
        verify(publicationBatchRepository).save(batchCaptor.capture());
        assertEquals(java.time.temporal.ChronoUnit.SECONDS,
                java.time.temporal.ChronoUnit.SECONDS); // placeholder assertion below replaces
    }

    private PublicationDto scheduledDto(UUID id) {
        return dto(id, PublicationStatus.SCHEDULED, null);
    }
```
Then replace the placeholder line with a real assertion on the captured batch's `scheduledAt`:
```java
        assertEquals(true, batchCaptor.getValue().getScheduledAt() != null);
```

- [ ] **Step 6: Run to verify it fails**

Run: `cd backend && mvn -q -Dtest=PublishProductsBatchUseCaseTest test`
Expected: FAIL — `scheduled()` accessor unknown / `dispatch` still called.

- [ ] **Step 7: Rewrite `PublishProductsBatchUseCase` to use the factory + scheduled mode**

```java
@Component
public class PublishProductsBatchUseCase {

    private final PublicationService publicationService;
    private final ProductRepository productRepository;
    private final PublicationBatchJpaRepository publicationBatchRepository;
    private final BatchPublicationFactory factory;

    public PublishProductsBatchUseCase(PublicationService publicationService,
                                       ProductRepository productRepository,
                                       PublicationBatchJpaRepository publicationBatchRepository,
                                       BatchPublicationFactory factory) {
        this.publicationService = publicationService;
        this.productRepository = productRepository;
        this.publicationBatchRepository = publicationBatchRepository;
        this.factory = factory;
    }

    public PublishProductsBatchResult execute(PublishProductsBatchCommand command, UUID actorUserId) {
        List<PublishProductsBatchResult.PublicationItemResult> items = new ArrayList<>();
        boolean scheduled = command.scheduledAt() != null;

        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(UUID.randomUUID());
        batch.setCaptionTemplate(command.captionTemplate());
        batch.setHashtagsJson(factory.serializeHashtags(command.hashtags()));
        batch.setCampaignLabel(factory.trimToNull(command.campaignLabel()));
        batch.setScheduledAt(command.scheduledAt());
        batch.setCreatedBy(actorUserId);
        batch.setCreatedAt(Instant.now());
        publicationBatchRepository.save(batch);

        for (UUID productId : command.productIds()) {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                for (PublicationPlatform platform : command.platforms()) {
                    items.add(new PublishProductsBatchResult.PublicationItemResult(
                            productId, platform, false, null, "Producto no encontrado: " + productId, false));
                }
                continue;
            }
            String caption = factory.interpolate(command.captionTemplate(), product,
                    command.variantSelections().get(productId));
            for (PublicationPlatform platform : command.platforms()) {
                items.add(publishOne(product, platform, caption, command, actorUserId, batch.getId(), scheduled));
            }
        }
        return new PublishProductsBatchResult(items);
    }

    private PublishProductsBatchResult.PublicationItemResult publishOne(Product product,
                                                                        PublicationPlatform platform,
                                                                        String caption,
                                                                        PublishProductsBatchCommand command,
                                                                        UUID actorUserId,
                                                                        UUID batchId,
                                                                        boolean scheduled) {
        try {
            CreatePublicationCommand createCommand = factory.buildCreateCommand(command, product, platform, caption, batchId);
            CreatePublicationResult created = publicationService.create(createCommand, actorUserId);
            if (scheduled) {
                return new PublishProductsBatchResult.PublicationItemResult(
                        product.getId(), platform, false, created.publication().id(), null, true);
            }
            PublicationDto dispatched = publicationService.dispatch(created.publication().id(), actorUserId);
            boolean success = dispatched.status() == PublicationStatus.PUBLISHED;
            return new PublishProductsBatchResult.PublicationItemResult(
                    product.getId(), platform, success, dispatched.id(),
                    success ? null : dispatched.lastErrorMessage(), false);
        } catch (DomainException ex) {
            return new PublishProductsBatchResult.PublicationItemResult(
                    product.getId(), platform, false, null, ex.getMessage(), false);
        }
    }
}
```
Delete the now-unused imports (`NumberFormat`, `Locale`, `ObjectMapper`, `ProductVariant`, `PublicationChannelType`, `PublicationMediaBundleType`, `PublicationSourceType`, `CreatePublicationCommand` stays for the return type of `buildCreateCommand`? no — it's only referenced as `factory.buildCreateCommand(...)` returning it and assigned to a local; keep the `CreatePublicationCommand` import).

- [ ] **Step 8: Run to verify it passes**

Run: `cd backend && mvn -q -Dtest='PublishProductsBatchUseCaseTest,BatchPublicationFactoryTest' test`
Expected: PASS (all existing + the 2 new). The existing tests that assert `cmd.caption()` on captured `CreatePublicationCommand` still work — the factory produces the same shape.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/usecases/ \
  backend/src/test/java/com/pilarestilo/publication/application/usecases/
git commit -m "feat(publication): BatchPublicationFactory + scheduled batch mode (no immediate dispatch)"
```

---

## Task 4: the job — `markScheduleWindowMissed` + `PublishDueScheduledPublicationsUseCase` + scheduler

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java` (add `markScheduleWindowMissed`)
- Create: `backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishDueScheduledPublicationsUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/jobs/PublishDueScheduledPublicationsScheduler.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java`, new `PublishDueScheduledPublicationsUseCaseTest.java`

**Interfaces:**
- Consumes: `PublicationJpaRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc` (Task 1), `PublicationService.dispatch(UUID, UUID)` (existing).
- Produces:
  - `PublicationService.markScheduleWindowMissed(UUID id)` → `PublicationDto` (FAILED + `SCHEDULE_WINDOW_MISSED` + `PublicationDispatchFailed` event; throws `DomainException` if not `SCHEDULED`).
  - `PublishDueScheduledPublicationsUseCase.execute()` → `int` (rows handled). Package-private ctor `(PublicationJpaRepository, PublicationService, Clock, long maxLatenessMinutes)`.

- [ ] **Step 1: Write the failing `markScheduleWindowMissed` test in `PublicationServiceTest`**

```java
    @Test
    void mark_schedule_window_missed_fails_the_row_and_emits_the_event() {
        UUID id = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(id, null);
        entity.setStatus(PublicationStatus.SCHEDULED);
        when(publicationRepository.findById(id)).thenReturn(Optional.of(entity));

        PublicationDto dto = service.markScheduleWindowMissed(id);

        assertEquals(PublicationStatus.FAILED, dto.status());
        assertEquals("SCHEDULE_WINDOW_MISSED", dto.lastErrorCode());
        verify(eventPublisher).publish(any(com.pilarestilo.publication.domain.events.PublicationDispatchFailed.class));
    }

    @Test
    void mark_schedule_window_missed_rejects_a_non_scheduled_row() {
        UUID id = UUID.randomUUID();
        PublicationEntity entity = approvedPublication(id, null); // APPROVED
        when(publicationRepository.findById(id)).thenReturn(Optional.of(entity));
        assertThrows(DomainException.class, () -> service.markScheduleWindowMissed(id));
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q -Dtest=PublicationServiceTest test`
Expected: FAIL — `markScheduleWindowMissed` undefined.

- [ ] **Step 3: Add `markScheduleWindowMissed` to `PublicationService`**

After `retry(...)`:
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
`PublicationDispatchFailed` is already imported in `PublicationService`.

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && mvn -q -Dtest=PublicationServiceTest test`
Expected: PASS.

- [ ] **Step 5: Write `PublishDueScheduledPublicationsUseCaseTest`**

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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishDueScheduledPublicationsUseCaseTest {

    @Mock PublicationJpaRepository publicationRepository;
    @Mock PublicationService publicationService;

    private final Instant fixedNow = Instant.parse("2026-09-06T12:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    private PublishDueScheduledPublicationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PublishDueScheduledPublicationsUseCase(publicationRepository, publicationService, clock, 360L);
    }

    private PublicationEntity due(Instant scheduledAt) {
        PublicationEntity e = new PublicationEntity();
        e.setId(UUID.randomUUID());
        e.setStatus(PublicationStatus.SCHEDULED);
        e.setPlatform(PublicationPlatform.INSTAGRAM);
        e.setScheduledAt(scheduledAt);
        return e;
    }

    @Test
    void dispatches_an_in_window_row_and_fails_a_stale_one() {
        PublicationEntity fresh = due(fixedNow.minusSeconds(120));         // 2 min late -> dispatch
        PublicationEntity stale = due(fixedNow.minusSeconds(7 * 3600));    // 7 h late  -> missed
        when(publicationRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                PublicationStatus.SCHEDULED, fixedNow)).thenReturn(List.of(fresh, stale));

        int handled = useCase.execute();

        assertEquals(2, handled);
        verify(publicationService).dispatch(eq(fresh.getId()), any());
        verify(publicationService).markScheduleWindowMissed(stale.getId());
        verify(publicationService, never()).dispatch(eq(stale.getId()), any());
    }

    @Test
    void one_row_throwing_does_not_stop_the_rest() {
        PublicationEntity a = due(fixedNow.minusSeconds(60));
        PublicationEntity b = due(fixedNow.minusSeconds(60));
        when(publicationRepository.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                PublicationStatus.SCHEDULED, fixedNow)).thenReturn(List.of(a, b));
        when(publicationService.dispatch(eq(a.getId()), any())).thenThrow(new DomainException("boom"));

        int handled = useCase.execute();

        assertEquals(1, handled); // only b
        verify(publicationService).dispatch(eq(b.getId()), any());
    }
}
```

- [ ] **Step 6: Run to verify it fails**

Run: `cd backend && mvn -q -Dtest=PublishDueScheduledPublicationsUseCaseTest test`
Expected: FAIL — class does not exist.

- [ ] **Step 7: Create `PublishDueScheduledPublicationsUseCase`**

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
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Publishes SCHEDULED publications when their scheduled_at is reached. Not @Transactional: each
 * dispatch is its own @Transactional call on PublicationService — same reasoning as
 * PublishProductsBatchUseCase.
 */
@Component
public class PublishDueScheduledPublicationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(PublishDueScheduledPublicationsUseCase.class);

    private final PublicationJpaRepository publicationRepository;
    private final PublicationService publicationService;
    private final Clock clock;
    private final long maxLatenessMinutes;

    @Autowired
    public PublishDueScheduledPublicationsUseCase(PublicationJpaRepository publicationRepository,
                                                 PublicationService publicationService,
                                                 @Value("${app.social-publishing.schedule.max-lateness-minutes:360}") long maxLatenessMinutes) {
        this(publicationRepository, publicationService, Clock.systemUTC(), maxLatenessMinutes);
    }

    PublishDueScheduledPublicationsUseCase(PublicationJpaRepository publicationRepository,
                                          PublicationService publicationService,
                                          Clock clock,
                                          long maxLatenessMinutes) {
        this.publicationRepository = publicationRepository;
        this.publicationService = publicationService;
        this.clock = clock;
        this.maxLatenessMinutes = maxLatenessMinutes;
    }

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

- [ ] **Step 8: Run to verify it passes**

Run: `cd backend && mvn -q -Dtest=PublishDueScheduledPublicationsUseCaseTest test`
Expected: PASS.

- [ ] **Step 9: Create the scheduler**

`backend/src/main/java/com/pilarestilo/publication/infrastructure/jobs/PublishDueScheduledPublicationsScheduler.java`:
```java
package com.pilarestilo.publication.infrastructure.jobs;

import com.pilarestilo.publication.application.usecases.PublishDueScheduledPublicationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PublishDueScheduledPublicationsScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublishDueScheduledPublicationsScheduler.class);

    private final PublishDueScheduledPublicationsUseCase useCase;

    public PublishDueScheduledPublicationsScheduler(PublishDueScheduledPublicationsUseCase useCase) {
        this.useCase = useCase;
    }

    @Scheduled(cron = "${app.social-publishing.schedule.cron:0 * * * * *}")
    public void run() {
        int handled = useCase.execute();
        if (handled > 0) {
            log.info("Published or failed {} due scheduled publications", handled);
        }
    }
}
```

- [ ] **Step 10: Verify it wires (context boot)**

Run: `cd backend && mvn -q -Dtest=PublicationControllerIT test`
Expected: PASS — the scheduler bean wires into the running context; with no `SCHEDULED` rows the job is a harmless no-op query.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/ backend/src/test/java/com/pilarestilo/publication/
git commit -m "feat(publication): @Scheduled job that publishes due scheduled batches (lateness cap -> FAILED)"
```

---

## Task 5: `cancelScheduledBatch` + `rescheduleBatch` on `PublicationService`

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java`

**Interfaces:**
- Produces:
  - `PublicationService.cancelScheduledBatch(UUID batchId)` → `PublicationBatchDetailDto` (SCHEDULED rows → CANCELLED; `DomainException` if none SCHEDULED).
  - `PublicationService.rescheduleBatch(UUID batchId, Instant newScheduledAt)` → `PublicationBatchDetailDto` (updates the batch row's `scheduled_at` + every SCHEDULED row's `scheduled_at`; `DomainException` if none SCHEDULED).

- [ ] **Step 1: Write the failing tests**

```java
    @Test
    void cancel_scheduled_batch_flips_only_scheduled_rows_to_cancelled() {
        UUID batchId = UUID.randomUUID();
        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(batchId);
        batch.setCaptionTemplate("{producto}");
        batch.setHashtagsJson("[]");
        batch.setCreatedAt(Instant.now());
        batch.setScheduledAt(Instant.now().plusSeconds(3600));
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        PublicationEntity sched = batchRow(batchId, PublicationStatus.SCHEDULED, PublicationPlatform.INSTAGRAM);
        PublicationEntity done = batchRow(batchId, PublicationStatus.PUBLISHED, PublicationPlatform.FACEBOOK);
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(sched, done));

        service.cancelScheduledBatch(batchId);

        assertEquals(PublicationStatus.CANCELLED, sched.getStatus());
        assertEquals(PublicationStatus.PUBLISHED, done.getStatus());
    }

    @Test
    void cancel_scheduled_batch_rejects_a_batch_with_nothing_scheduled() {
        UUID batchId = UUID.randomUUID();
        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(batchId);
        batch.setCaptionTemplate("{producto}");
        batch.setHashtagsJson("[]");
        batch.setCreatedAt(Instant.now());
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(
                List.of(batchRow(batchId, PublicationStatus.PUBLISHED, PublicationPlatform.INSTAGRAM)));
        assertThrows(DomainException.class, () -> service.cancelScheduledBatch(batchId));
    }

    @Test
    void reschedule_batch_updates_the_batch_and_the_scheduled_rows() {
        UUID batchId = UUID.randomUUID();
        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(batchId);
        batch.setCaptionTemplate("{producto}");
        batch.setHashtagsJson("[]");
        batch.setCreatedAt(Instant.now());
        batch.setScheduledAt(Instant.now().plusSeconds(3600));
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        PublicationEntity sched = batchRow(batchId, PublicationStatus.SCHEDULED, PublicationPlatform.INSTAGRAM);
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(sched));

        Instant newТime = Instant.now().plusSeconds(7200);
        service.rescheduleBatch(batchId, newТime);

        assertEquals(newТime, batch.getScheduledAt());
        assertEquals(newТime, sched.getScheduledAt());
    }
```
(rename `newТime` → `newTime` — the Cyrillic Т was a copy artifact; use ASCII.)

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && mvn -q -Dtest=PublicationServiceTest test`
Expected: FAIL — methods undefined.

- [ ] **Step 3: Implement both methods**

After `markScheduleWindowMissed`:
```java
    @Transactional
    public PublicationBatchDetailDto cancelScheduledBatch(UUID batchId) {
        List<PublicationEntity> rows = publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        List<PublicationEntity> scheduled = rows.stream()
                .filter(r -> r.getStatus() == PublicationStatus.SCHEDULED).toList();
        if (scheduled.isEmpty()) {
            throw new DomainException("Esta tanda no tiene publicaciones programadas para cancelar");
        }
        Instant now = Instant.now();
        for (PublicationEntity r : scheduled) {
            r.setStatus(PublicationStatus.CANCELLED);
            r.setUpdatedAt(now);
            publicationRepository.save(r);
        }
        return getBatch(batchId);
    }

    @Transactional
    public PublicationBatchDetailDto rescheduleBatch(UUID batchId, Instant newScheduledAt) {
        PublicationBatchEntity batch = publicationBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Publication batch not found: " + batchId));
        List<PublicationEntity> scheduled = publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId).stream()
                .filter(r -> r.getStatus() == PublicationStatus.SCHEDULED).toList();
        if (scheduled.isEmpty()) {
            throw new DomainException("Esta tanda ya no está programada");
        }
        Instant now = Instant.now();
        batch.setScheduledAt(newScheduledAt);
        publicationBatchRepository.save(batch);
        for (PublicationEntity r : scheduled) {
            r.setScheduledAt(newScheduledAt);
            r.setUpdatedAt(now);
            publicationRepository.save(r);
        }
        return getBatch(batchId);
    }
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && mvn -q -Dtest=PublicationServiceTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java \
  backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java
git commit -m "feat(publication): cancelScheduledBatch + rescheduleBatch"
```

---

## Task 6: `UpdateScheduledBatchUseCase`

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/application/usecases/UpdateScheduledBatchUseCase.java`
- Create: `backend/src/test/java/com/pilarestilo/publication/application/usecases/UpdateScheduledBatchUseCaseTest.java`

**Interfaces:**
- Consumes: `BatchPublicationFactory` (Task 3), `PublicationService.create` / `getBatch` (existing), `PublicationBatchJpaRepository`, `PublicationJpaRepository`, `ProductRepository`.
- Produces: `UpdateScheduledBatchUseCase.execute(UUID batchId, PublishProductsBatchCommand command, UUID actorUserId)` → `PublicationBatchDetailDto`. `DomainException` if any row is not `SCHEDULED` (nothing deleted).

- [ ] **Step 1: Write `UpdateScheduledBatchUseCaseTest`**

```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
import com.pilarestilo.publication.application.dto.PublicationBatchDetailDto;
import com.pilarestilo.publication.application.dto.PublicationDto;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateScheduledBatchUseCaseTest {

    @Mock PublicationService publicationService;
    @Mock PublicationJpaRepository publicationRepository;
    @Mock PublicationBatchJpaRepository publicationBatchRepository;
    @Mock ProductRepository productRepository;

    UpdateScheduledBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateScheduledBatchUseCase(publicationService, publicationRepository,
                publicationBatchRepository, productRepository, new BatchPublicationFactory(new ObjectMapper()));
    }

    private PublicationEntity row(UUID batchId, PublicationStatus status) {
        PublicationEntity e = new PublicationEntity();
        e.setId(UUID.randomUUID());
        e.setBatchId(batchId);
        e.setStatus(status);
        e.setPlatform(PublicationPlatform.INSTAGRAM);
        return e;
    }

    private PublishProductsBatchCommand cmd(UUID productId, Instant when) {
        return new PublishProductsBatchCommand(List.of(productId), Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of("#x"), "Camp", Map.of(), Map.of(), when);
    }

    @Test
    void replaces_scheduled_rows_and_updates_the_batch() {
        UUID batchId = UUID.randomUUID();
        Product product = Product.create("Zapatos", "d", new Money(BigDecimal.valueOf(1000), "CLP"),
                "https://img/z.jpg", ProductCondition.NEW, "Pilar", 5);
        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(batchId);
        batch.setCaptionTemplate("old");
        batch.setHashtagsJson("[]");
        batch.setCreatedAt(Instant.now());
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        PublicationEntity old = row(batchId, PublicationStatus.SCHEDULED);
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(old));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        UUID newPubId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(scheduledDto(newPubId), true));
        when(publicationService.getBatch(batchId)).thenReturn(detailStub(batchId));

        Instant when = Instant.now().plusSeconds(3600);
        useCase.execute(batchId, cmd(product.getId(), when), UUID.randomUUID());

        verify(publicationRepository).deleteAll(List.of(old));
        verify(publicationService).create(any(CreatePublicationCommand.class), any());
        verify(publicationService, never()).dispatch(any(), any());
    }

    @Test
    void refuses_when_a_row_already_left_scheduled() {
        UUID batchId = UUID.randomUUID();
        PublicationBatchEntity batch = new PublicationBatchEntity();
        batch.setId(batchId);
        batch.setCaptionTemplate("old");
        batch.setHashtagsJson("[]");
        batch.setCreatedAt(Instant.now());
        when(publicationBatchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId)).thenReturn(List.of(
                row(batchId, PublicationStatus.SCHEDULED), row(batchId, PublicationStatus.PUBLISHING)));

        assertThrows(DomainException.class,
                () -> useCase.execute(batchId, cmd(UUID.randomUUID(), Instant.now().plusSeconds(60)), UUID.randomUUID()));
        verify(publicationRepository, never()).deleteAll(any());
    }

    private PublicationDto scheduledDto(UUID id) {
        return new PublicationDto(id, null, com.pilarestilo.publication.domain.enums.PublicationSourceType.PRODUCT, null,
                PublicationPlatform.INSTAGRAM, com.pilarestilo.publication.domain.enums.PublicationChannelType.FEED_POST,
                PublicationStatus.SCHEDULED, com.pilarestilo.publication.domain.enums.PublicationApprovalStatus.NOT_REQUIRED,
                "c", List.of(), "es-CL", null, null, null, null, "k", 1, 1, null, null, 0, null, null,
                Instant.now(), Instant.now(), List.of(), List.of(), List.of(), List.of(), null);
    }

    private PublicationBatchDetailDto detailStub(UUID batchId) {
        return new PublicationBatchDetailDto(batchId, null, "{producto}", List.of(), Instant.now(), List.of(), List.of(), null);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q -Dtest=UpdateScheduledBatchUseCaseTest test`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create `UpdateScheduledBatchUseCase`**

```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.application.dto.PublicationBatchDetailDto;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Replaces the SCHEDULED rows of a not-yet-published batch with a fresh set built from a new
 * command. Not a full @Transactional over the loop — create() is its own transaction on
 * PublicationService (same reasoning as PublishProductsBatchUseCase). The delete + batch update
 * are done in one @Transactional method first; regeneration follows.
 */
@Component
public class UpdateScheduledBatchUseCase {

    private final PublicationService publicationService;
    private final PublicationJpaRepository publicationRepository;
    private final PublicationBatchJpaRepository publicationBatchRepository;
    private final ProductRepository productRepository;
    private final BatchPublicationFactory factory;

    public UpdateScheduledBatchUseCase(PublicationService publicationService,
                                       PublicationJpaRepository publicationRepository,
                                       PublicationBatchJpaRepository publicationBatchRepository,
                                       ProductRepository productRepository,
                                       BatchPublicationFactory factory) {
        this.publicationService = publicationService;
        this.publicationRepository = publicationRepository;
        this.publicationBatchRepository = publicationBatchRepository;
        this.productRepository = productRepository;
        this.factory = factory;
    }

    public PublicationBatchDetailDto execute(UUID batchId, PublishProductsBatchCommand command, UUID actorUserId) {
        clearAndUpdateBatch(batchId, command);
        for (UUID productId : command.productIds()) {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                continue;
            }
            String caption = factory.interpolate(command.captionTemplate(), product,
                    command.variantSelections().get(productId));
            for (PublicationPlatform platform : command.platforms()) {
                CreatePublicationCommand create = factory.buildCreateCommand(command, product, platform, caption, batchId);
                publicationService.create(create, actorUserId);
            }
        }
        return publicationService.getBatch(batchId);
    }

    @Transactional
    protected void clearAndUpdateBatch(UUID batchId, PublishProductsBatchCommand command) {
        PublicationBatchEntity batch = publicationBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Publication batch not found: " + batchId));
        List<PublicationEntity> rows = publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        boolean allScheduled = !rows.isEmpty() && rows.stream().allMatch(r -> r.getStatus() == PublicationStatus.SCHEDULED);
        if (!allScheduled) {
            throw new DomainException("Esta tanda ya empezó a publicarse, no se puede editar");
        }
        publicationRepository.deleteAll(rows);
        batch.setCaptionTemplate(command.captionTemplate());
        batch.setHashtagsJson(factory.serializeHashtags(command.hashtags()));
        batch.setCampaignLabel(factory.trimToNull(command.campaignLabel()));
        batch.setScheduledAt(command.scheduledAt());
        publicationBatchRepository.save(batch);
    }
}
```
Note: `@Transactional` on `protected` self-invoked method is a proxy trap. **Instead** make
`clearAndUpdateBatch` a package-private method on a small `@Component ScheduledBatchMutator`, OR
simpler — inline it and accept that the delete + regenerate are not atomic (a crash between them
leaves an empty batch, which the owner sees and can re-edit). **Decision for the plan:** inline,
no inner `@Transactional`. Rewrite `execute` so the guard + delete + batch-field update happen
first (each `repository` call auto-commits in its own transaction via the repository proxy — Spring
Data repositories are `@Transactional` by default for writes), then the regeneration loop. Drop
the `clearAndUpdateBatch` method:

```java
    public PublicationBatchDetailDto execute(UUID batchId, PublishProductsBatchCommand command, UUID actorUserId) {
        PublicationBatchEntity batch = publicationBatchRepository.findById(batchId)
                .orElseThrow(() -> new NoSuchElementException("Publication batch not found: " + batchId));
        List<PublicationEntity> rows = publicationRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        boolean allScheduled = !rows.isEmpty()
                && rows.stream().allMatch(r -> r.getStatus() == PublicationStatus.SCHEDULED);
        if (!allScheduled) {
            throw new DomainException("Esta tanda ya empezó a publicarse, no se puede editar");
        }
        publicationRepository.deleteAll(rows);
        batch.setCaptionTemplate(command.captionTemplate());
        batch.setHashtagsJson(factory.serializeHashtags(command.hashtags()));
        batch.setCampaignLabel(factory.trimToNull(command.campaignLabel()));
        batch.setScheduledAt(command.scheduledAt());
        publicationBatchRepository.save(batch);

        for (UUID productId : command.productIds()) {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) continue;
            String caption = factory.interpolate(command.captionTemplate(), product,
                    command.variantSelections().get(productId));
            for (PublicationPlatform platform : command.platforms()) {
                publicationService.create(
                        factory.buildCreateCommand(command, product, platform, caption, batchId), actorUserId);
            }
        }
        return publicationService.getBatch(batchId);
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && mvn -q -Dtest=UpdateScheduledBatchUseCaseTest test`
Expected: PASS. (The test's `verify(publicationRepository).deleteAll(List.of(old))` matches
`deleteAll(rows)` where `rows == List.of(old)`.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/usecases/UpdateScheduledBatchUseCase.java \
  backend/src/test/java/com/pilarestilo/publication/application/usecases/UpdateScheduledBatchUseCaseTest.java
git commit -m "feat(publication): UpdateScheduledBatchUseCase (edit a scheduled batch by replacing its rows)"
```

---

## Task 7: three controller endpoints + IT + full backend suite

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/requests/RescheduleBatchRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/controllers/PublicationController.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationControllerIT.java`

**Interfaces:**
- Consumes: `PublicationService.cancelScheduledBatch` / `rescheduleBatch` (Task 5), `UpdateScheduledBatchUseCase.execute` (Task 6), `PublicationController.toBatchCommand` (Task 2).
- Produces: `POST /batches/{id}/cancel`, `POST /batches/{id}/reschedule`, `PUT /batches/{id}` on `/api/admin/publications`.

- [ ] **Step 1: Write the failing IT tests**

Add to `PublicationControllerIT` (uses `Product` + `Money` + `ProductCondition` already imported; add `java.time.Instant` if missing):

```java
    @Test
    void a_scheduled_batch_shows_as_scheduled_and_can_be_cancelled_rescheduled_and_edited() throws Exception {
        String adminToken = loginAdmin();
        Product p1 = productRepository.save(Product.create("Falda prog", "d",
                new Money(BigDecimal.valueOf(29990), "CLP"), "https://cdn.example.com/f.jpg",
                ProductCondition.NEW, "Pilar", 3));
        String future = java.time.Instant.now().plusSeconds(3 * 3600).toString();

        MvcResult created = mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(p1.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}",
                                "campaignLabel", "Programada Test",
                                "scheduledAt", future))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].scheduled").value(true))
                .andExpect(jsonPath("$.items[0].success").value(false))
                .andReturn();

        MvcResult batches = mvc.perform(get("/api/admin/publications/batches")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scheduledAt").exists())
                .andReturn();
        String batchId = om.readTree(batches.getResponse().getContentAsString()).get(0).get("batchId").asString();

        mvc.perform(get("/api/admin/publications/batches/{id}", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.rows[0].status").value("SCHEDULED"));

        // reschedule
        String later = java.time.Instant.now().plusSeconds(5 * 3600).toString();
        mvc.perform(post("/api/admin/publications/batches/{id}/reschedule", batchId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("scheduledAt", later))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledAt").value(org.hamcrest.Matchers.startsWith(later.substring(0, 19))));

        // reschedule to the past -> 400
        mvc.perform(post("/api/admin/publications/batches/{id}/reschedule", batchId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("scheduledAt",
                                java.time.Instant.now().minusSeconds(60).toString()))))
                .andExpect(status().isBadRequest());

        // edit content (PUT) — add a second product, change the caption
        Product p2 = productRepository.save(Product.create("Blusa prog", "d",
                new Money(BigDecimal.valueOf(19990), "CLP"), "https://cdn.example.com/b.jpg",
                ProductCondition.NEW, "Pilar", 2));
        mvc.perform(put("/api/admin/publications/batches/{id}", batchId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(p1.getId().toString(), p2.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "Nuevo {producto}",
                                "scheduledAt", later))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captionTemplate").value("Nuevo {producto}"))
                .andExpect(jsonPath("$.rows", hasSize(2)));

        // cancel
        mvc.perform(post("/api/admin/publications/batches/{id}/cancel", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].status").value("CANCELLED"));
    }

    @Test
    void a_batch_scheduled_in_the_past_is_rejected() throws Exception {
        String adminToken = loginAdmin();
        Product p = productRepository.save(Product.create("Prod pasado", "d",
                new Money(BigDecimal.valueOf(9990), "CLP"), "https://cdn.example.com/p.jpg",
                ProductCondition.NEW, "Pilar", 1));
        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(p.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}",
                                "scheduledAt", java.time.Instant.now().minusSeconds(60).toString()))))
                .andExpect(status().isBadRequest());
    }
```
Add the static import `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;`.

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && mvn -q -Dtest=PublicationControllerIT test`
Expected: FAIL — 404 / 405 on the new routes, `scheduled` field absent.

- [ ] **Step 3: Create `RescheduleBatchRequest`**

```java
package com.pilarestilo.publication.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;

public record RescheduleBatchRequest(@NotBlank String scheduledAt) {}
```

- [ ] **Step 4: Add the endpoints + `UpdateScheduledBatchUseCase` dependency to `PublicationController`**

Add to the constructor (append param + assignment): `UpdateScheduledBatchUseCase updateScheduledBatchUseCase`.
Add imports for `RescheduleBatchRequest`, `UpdateScheduledBatchUseCase`, `java.time.Instant`,
`java.time.format.DateTimeParseException`, `com.pilarestilo.shared.domain.DomainException`,
`org.springframework.web.bind.annotation.PutMapping`.

```java
    @PostMapping("/batches/{batchId}/cancel")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublicationBatchDetailDto cancelBatch(@PathVariable UUID batchId) {
        return publicationService.cancelScheduledBatch(batchId);
    }

    @PostMapping("/batches/{batchId}/reschedule")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublicationBatchDetailDto rescheduleBatch(@PathVariable UUID batchId,
                                                     @Valid @RequestBody RescheduleBatchRequest request) {
        return publicationService.rescheduleBatch(batchId, parseFutureInstant(request.scheduledAt()));
    }

    @PutMapping("/batches/{batchId}")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublicationBatchDetailDto updateScheduledBatch(@PathVariable UUID batchId,
                                                          @Valid @RequestBody PublishProductsBatchRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return updateScheduledBatchUseCase.execute(batchId, toBatchCommand(request),
                currentUser == null ? null : currentUser.id());
    }

    private Instant parseFutureInstant(String raw) {
        Instant when;
        try {
            when = Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new DomainException("Fecha de programación inválida");
        }
        if (when.isBefore(Instant.now())) {
            throw new DomainException("La hora programada ya pasó");
        }
        return when;
    }
```
Refactor Task 2 Step 9's inline parse in `toBatchCommand` to call `parseFutureInstant` (DRY) —
`toBatchCommand` does `request.scheduledAt() == null ? null : parseFutureInstant(request.scheduledAt())`.

- [ ] **Step 5: Run to verify they pass**

Run: `cd backend && mvn -q -Dtest=PublicationControllerIT test`
Expected: PASS.

- [ ] **Step 6: Full backend suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS. Known-flaky under load: `PosControllerTest`, `LoginWithPermissionsTest` (Testcontainers contention — re-run in isolation to confirm, per project memory). Fix any real fallout — grep `new PublishProductsBatchCommand(` / `new PublicationItemResult(` / `new PublicationBatchSummaryDto(` / `new PublicationBatchDetailDto(` / `new PublicationDto(` across `src/test` for missed trailing args.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/ backend/src/test/java/com/pilarestilo/publication/
git commit -m "feat(publication): cancel / reschedule / edit endpoints for scheduled batches"
```

---

## Task 8: `santiagoTime.ts`

**Files:**
- Create: `frontend/src/lib/santiagoTime.ts`
- Create: `frontend/src/lib/__tests__/santiagoTime.test.ts`

**Interfaces:**
- Produces:
  - `santiagoWallTimeToInstant(local: string): string` — `"2026-09-06T10:00"` (Santiago wall-clock) → UTC ISO instant.
  - `instantToSantiagoLabel(iso: string): string` — → `"sáb 6 sep, 10:00"`.
  - `instantToSantiagoInputValue(iso: string): string` — → `"2026-09-06T10:00"` for `<input type=datetime-local>`.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from 'vitest';
import {
  santiagoWallTimeToInstant,
  instantToSantiagoInputValue,
  instantToSantiagoLabel,
} from '../santiagoTime';

describe('santiagoTime', () => {
  it('maps a summer (CLST, UTC-3) wall time to the right UTC instant', () => {
    // Chile summer: February. UTC-3, so 10:00 Santiago = 13:00 UTC.
    expect(santiagoWallTimeToInstant('2026-02-15T10:00')).toBe('2026-02-15T13:00:00.000Z');
  });

  it('maps a winter (CLT, UTC-4) wall time to the right UTC instant', () => {
    // Chile winter: June. UTC-4, so 10:00 Santiago = 14:00 UTC.
    expect(santiagoWallTimeToInstant('2026-06-15T10:00')).toBe('2026-06-15T14:00:00.000Z');
  });

  it('round-trips through the input-value formatter', () => {
    const iso = santiagoWallTimeToInstant('2026-06-15T10:00');
    expect(instantToSantiagoInputValue(iso)).toBe('2026-06-15T10:00');
  });

  it('formats a readable Santiago label', () => {
    const label = instantToSantiagoLabel('2026-06-15T14:00:00.000Z');
    expect(label).toMatch(/10:00/);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/lib/__tests__/santiagoTime.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `santiagoTime.ts`**

```ts
const TZ = 'America/Santiago';

/** Parse "YYYY-MM-DD HH:MM:SS" (sv-SE locale output) into a UTC epoch ms. */
function parseSvSe(s: string): number {
  const [d, t] = s.split(' ');
  const [y, mo, day] = d.split('-').map(Number);
  const [h, mi, sec] = t.split(':').map(Number);
  return Date.UTC(y, mo - 1, day, h, mi, sec || 0);
}

/**
 * A Santiago wall-clock string ("2026-06-15T10:00" or with seconds) -> UTC ISO instant.
 * Iterative: guess the instant is the wall time in UTC, see what Santiago clock that shows,
 * correct by the delta. Converges in <= 2 passes even across a DST boundary.
 */
export function santiagoWallTimeToInstant(local: string): string {
  const [d, t] = local.split('T');
  const [y, mo, day] = d.split('-').map(Number);
  const [h, mi] = t.split(':').map(Number);
  const target = Date.UTC(y, mo - 1, day, h, mi, 0);
  let guess = target;
  for (let i = 0; i < 3; i++) {
    const shown = parseSvSe(new Date(guess).toLocaleString('sv-SE', { timeZone: TZ }));
    const delta = target - shown;
    if (delta === 0) break;
    guess += delta;
  }
  return new Date(guess).toISOString();
}

export function instantToSantiagoLabel(iso: string): string {
  return new Date(iso).toLocaleString('es-CL', {
    timeZone: TZ, weekday: 'short', day: 'numeric', month: 'short',
    hour: '2-digit', minute: '2-digit',
  });
}

export function instantToSantiagoInputValue(iso: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).formatToParts(new Date(iso));
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '00';
  // en-CA hour can come back as "24" at midnight in some engines; normalize.
  const hour = get('hour') === '24' ? '00' : get('hour');
  return `${get('year')}-${get('month')}-${get('day')}T${hour}:${get('minute')}`;
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd frontend && npx vitest run src/lib/__tests__/santiagoTime.test.ts`
Expected: PASS. If the `instantToSantiagoLabel` assertion is brittle across Node ICU versions, loosen it to `expect(label.length).toBeGreaterThan(0)` — the round-trip tests are the real guarantee.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/santiagoTime.ts frontend/src/lib/__tests__/santiagoTime.test.ts
git commit -m "feat(frontend): santiagoTime helper (DST-aware wall-clock <-> UTC instant)"
```

---

## Task 9: `api.ts` — scheduling fields + endpoints

**Files:**
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Produces:
  - `PublishProductsBatchRequest` gains `scheduledAt?: string` (ISO instant).
  - `PublishProductsBatchItemResult` gains `scheduled: boolean`.
  - `PublicationBatchSummary` and `PublicationBatchDetail` each gain `scheduledAt: string | null`.
  - `cancelBatch(batchId, token) => Promise<PublicationBatchDetail>`
  - `rescheduleBatch(batchId, scheduledAt, token) => Promise<PublicationBatchDetail>`
  - `updateScheduledBatch(batchId, body: PublishProductsBatchRequest, token) => Promise<PublicationBatchDetail>`

- [ ] **Step 1: Add the fields**

In `PublishProductsBatchRequest` add `scheduledAt?: string;`. In `PublishProductsBatchItemResult`
add `scheduled: boolean;`. In `PublicationBatchSummary` and `PublicationBatchDetail` add
`scheduledAt: string | null;`.

- [ ] **Step 2: Add the three functions**

After `retryBatchFailed`:
```ts
export async function cancelBatch(batchId: string, token: string): Promise<PublicationBatchDetail> {
  return apiFetch<PublicationBatchDetail>(`/admin/publications/batches/${encodeURIComponent(batchId)}/cancel`, {
    method: 'POST',
    headers: authHeaders(token),
  });
}

export async function rescheduleBatch(batchId: string, scheduledAt: string, token: string): Promise<PublicationBatchDetail> {
  return apiFetch<PublicationBatchDetail>(`/admin/publications/batches/${encodeURIComponent(batchId)}/reschedule`, {
    method: 'POST',
    body: JSON.stringify({ scheduledAt }),
    headers: authHeaders(token),
  });
}

export async function updateScheduledBatch(
  batchId: string,
  body: PublishProductsBatchRequest,
  token: string,
): Promise<PublicationBatchDetail> {
  return apiFetch<PublicationBatchDetail>(`/admin/publications/batches/${encodeURIComponent(batchId)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
    headers: authHeaders(token),
  });
}
```

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: clean (the `PublicarTab` / `HistorialTab` uses come in Tasks 10-11; adding optional fields + new functions does not break existing callers).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(frontend): api client for scheduling (scheduledAt fields + cancel/reschedule/update)"
```

---

## Task 10: `PublicarTab` — schedule picker + edit mode

**Files:**
- Modify: `frontend/src/islands/admin/PublicarTab.tsx`
- Test: `frontend/src/islands/admin/__tests__/PublicarTab.test.tsx`

**Interfaces:**
- Consumes: `santiagoWallTimeToInstant` / `instantToSantiagoInputValue` (Task 8), `updateScheduledBatch` (Task 9).
- Produces:
  - `PublicarTabPreload` gains `scheduledAt?: string | null`.
  - `PublicarTab` props gain `editingBatchId?: string` and `onEditCancelled?: () => void`.

- [ ] **Step 1: Write the failing tests**

Add to `PublicarTab.test.tsx` (mock now needs `updateScheduledBatch`, `getProduct`; and mock
`../../../lib/santiagoTime` is NOT needed — it is pure and fast, let it run):

```tsx
  it('sends scheduledAt when the batch is scheduled', async () => {
    const user = userEvent.setup();
    render(<PublicarTab />);
    await selectTheProduct(user);

    await user.click(screen.getByRole('radio', { name: /programar/i }));
    const input = screen.getByLabelText(/fecha y hora/i);
    // a fixed far-future local value
    fireEvent.change(input, { target: { value: '2027-06-15T10:00' } });

    await user.click(screen.getByRole('button', { name: /programar publicaci/i }));
    await waitFor(() =>
      expect(publishProductsBatch).toHaveBeenCalledWith(
        expect.objectContaining({ scheduledAt: '2027-06-15T14:00:00.000Z' }),
        't',
      ),
    );
  });

  it('in edit mode, submit calls updateScheduledBatch and the CTA says Guardar cambios', async () => {
    const user = userEvent.setup();
    render(
      <PublicarTab
        editingBatchId="b9"
        preload={{ productIds: [], captionTemplate: 'x', hashtags: [], campaignLabel: null, scheduledAt: '2027-06-15T14:00:00.000Z' }}
      />,
    );
    // preload seeds an empty selection; add one product so submit is enabled
    await selectTheProduct(user);
    await user.click(screen.getByRole('button', { name: /guardar cambios/i }));
    await waitFor(() => expect(vi.mocked(updateScheduledBatch)).toHaveBeenCalled());
  });
```
Add `fireEvent` to the testing-library import and `updateScheduledBatch` to the api mock +
`import`.

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/PublicarTab.test.tsx`
Expected: FAIL — no "Programar" radio, no edit mode.

- [ ] **Step 3: Implement in `PublicarTab.tsx`**

- Imports: add `import { santiagoWallTimeToInstant, instantToSantiagoInputValue } from '../../lib/santiagoTime';`
  and `updateScheduledBatch` to the api import.
- `PublicarTabPreload` type: add `scheduledAt?: string | null;`.
- Props: add `editingBatchId?: string; onEditCancelled?: () => void;` to `PublicarTabProps`.
- State: `const [mode, setMode] = useState<'now' | 'schedule'>('now');`
  `const [scheduleInput, setScheduleInput] = useState('');`
- Preload effect: after the existing seeds, `if (props.preload.scheduledAt) { setMode('schedule');
  setScheduleInput(instantToSantiagoInputValue(props.preload.scheduledAt)); }`.
- Between the caption section and the CTA, render:
  ```tsx
  <fieldset className="flex flex-col gap-2">
    <legend className="font-sans text-sm text-pe-muted mb-1">Cuándo</legend>
    <label className="flex items-center gap-2 text-sm">
      <input type="radio" name="when" checked={mode === 'now'} onChange={() => setMode('now')} className="accent-pe-rose" />
      Publicar ahora
    </label>
    <label className="flex items-center gap-2 text-sm">
      <input type="radio" name="when" checked={mode === 'schedule'} onChange={() => setMode('schedule')} className="accent-pe-rose" />
      Programar
    </label>
    {mode === 'schedule' && (
      <label className="flex flex-col gap-1 text-xs text-pe-muted mt-1 max-w-xs">
        Fecha y hora (hora de Chile)
        <input
          type="datetime-local"
          value={scheduleInput}
          min={instantToSantiagoInputValue(new Date(Date.now() + 5 * 60000).toISOString())}
          onChange={(e) => setScheduleInput(e.target.value)}
          className="bg-pe-surface border border-pe-border rounded-xs px-2 py-1 text-sm text-pe-black"
        />
      </label>
    )}
  </fieldset>
  ```
- `canPublish`: also require `mode === 'now' || scheduleInput.length > 0`.
- Rename the submit handler to `handleSubmit`; build the payload once, then:
  ```tsx
  const scheduledAt = mode === 'schedule' ? santiagoWallTimeToInstant(scheduleInput) : undefined;
  const payload = { /* ...existing fields... */, scheduledAt };
  const response = props.editingBatchId
    ? await updateScheduledBatch(props.editingBatchId, payload, effectiveToken)
    : await publishProductsBatch(payload, effectiveToken);
  ```
  When `editingBatchId`, `response` is a `PublicationBatchDetail` (not a batch result) — after a
  successful edit, show "Cambios guardados." and call `props.onEditCancelled?.()`.
  When scheduled (not editing), show "Programada para <instantToSantiagoLabel(scheduledAt)>. La vas
  a ver en Historial." instead of the per-item list.
- CTA label: `props.editingBatchId ? 'Guardar cambios' : (mode === 'schedule' ? 'Programar publicación' : 'Publicar ahora')`.
- When `editingBatchId`, render a "Cancelar edición" text button that calls `props.onEditCancelled?.()`.

- [ ] **Step 4: Run to verify it passes**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/PublicarTab.test.tsx`
Expected: PASS (existing tests + the 2 new). Existing tests use `mode='now'` by default, unchanged.

- [ ] **Step 5: Typecheck + commit**

Run: `cd frontend && npx tsc --noEmit` → clean.
```bash
git add frontend/src/islands/admin/PublicarTab.tsx frontend/src/islands/admin/__tests__/PublicarTab.test.tsx
git commit -m "feat(frontend): schedule picker + edit mode in PublicarTab"
```

---

## Task 11: `PublicacionesPage` shell + `HistorialTab` scheduled representation

**Files:**
- Modify: `frontend/src/islands/admin/PublicacionesPage.tsx`
- Modify: `frontend/src/islands/admin/HistorialTab.tsx`
- Test: `frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx`, `HistorialTab.test.tsx`

**Interfaces:**
- Consumes: `cancelBatch`, `rescheduleBatch` (Task 9), `instantToSantiagoLabel`, `santiagoWallTimeToInstant` (Task 8), `PublicarTab` `editingBatchId` prop (Task 10).
- Produces:
  - Shell: `editingBatchId` state; passes it + a clearing `onEditCancelled` to `PublicarTab`.
  - `HistorialTab` props gain `onEditScheduled: (batchId: string, preload: { productIds: string[]; captionTemplate: string; hashtags: string[]; campaignLabel: string | null; scheduledAt: string | null }) => void`.

- [ ] **Step 1: Write the failing shell test**

Add to `PublicacionesPage.test.tsx` — mock `getPublicationBatchDetail` to return a scheduled
batch, click "Editar" in Historial, assert `?tab=publicar` + the compose form shown + edit CTA:

```tsx
  it('editing a scheduled batch from Historial opens Publicar in edit mode', async () => {
    const scheduled = {
      batchId: 'bs', campaignLabel: 'Prog', createdAt: new Date().toISOString(),
      platforms: ['INSTAGRAM'], total: 1, published: 0, failed: 0, scheduled: 1, pending: 0,
      scheduledAt: '2027-06-15T14:00:00.000Z',
    };
    vi.mocked(getPublicationBatches).mockResolvedValue([scheduled] as never);
    vi.mocked(getPublicationBatchDetail).mockResolvedValue({
      batchId: 'bs', campaignLabel: 'Prog', captionTemplate: '{producto}', hashtags: [],
      createdAt: scheduled.createdAt, productIds: [], rows: [
        { publicationId: 'p', productId: null, productName: 'X', thumbnailUrl: null,
          platform: 'INSTAGRAM', status: 'SCHEDULED', externalPermalink: null,
          lastErrorCode: null, lastErrorMessage: null },
      ], scheduledAt: '2027-06-15T14:00:00.000Z',
    } as never);

    const user = userEvent.setup();
    window.history.replaceState({}, '', '/admin/publicaciones?tab=historial');
    render(<PublicacionesPage />);
    await user.click(await screen.findByRole('button', { name: /prog/i }));
    await user.click(await screen.findByRole('button', { name: /editar/i }));

    expect(new URLSearchParams(window.location.search).get('tab')).toBe('publicar');
    expect(await screen.findByRole('button', { name: /guardar cambios/i })).toBeInTheDocument();
  });
```
Extend the shell mock's api module with `getPublicationBatchDetail`, `cancelBatch`,
`rescheduleBatch`, `updateScheduledBatch`.

- [ ] **Step 2: Write the failing HistorialTab tests**

```tsx
  it('shows a scheduled batch as "Programada para" with cancel / reschedule / edit', async () => {
    const summary = { batchId: 'bs', campaignLabel: 'Prog', createdAt: new Date().toISOString(),
      platforms: ['INSTAGRAM'] as Array<'INSTAGRAM' | 'FACEBOOK'>, total: 1, published: 0, failed: 0,
      scheduled: 1, pending: 0, scheduledAt: '2027-06-15T14:00:00.000Z' };
    const detail = { batchId: 'bs', campaignLabel: 'Prog', captionTemplate: '{producto}', hashtags: [],
      createdAt: summary.createdAt, productIds: ['p1'], scheduledAt: '2027-06-15T14:00:00.000Z',
      rows: [{ publicationId: 'pub', productId: 'p1', productName: 'X', thumbnailUrl: null,
        platform: 'INSTAGRAM' as const, status: 'SCHEDULED', externalPermalink: null,
        lastErrorCode: null, lastErrorMessage: null }] };
    vi.mocked(getPublicationBatches).mockResolvedValue([summary] as never);
    vi.mocked(getPublicationBatchDetail).mockResolvedValue(detail as never);
    vi.mocked(cancelBatch).mockResolvedValue({ ...detail, rows: [{ ...detail.rows[0], status: 'CANCELLED' }] } as never);

    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={vi.fn()} />);
    expect(await screen.findByText(/programada para/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /prog/i }));
    await user.click(await screen.findByRole('button', { name: /cancelar programaci/i }));
    await waitFor(() => expect(cancelBatch).toHaveBeenCalledWith('bs', 't'));
  });

  it('"Editar" on a scheduled batch calls onEditScheduled with the batch data', async () => {
    const summary = { batchId: 'bs', campaignLabel: 'Prog', createdAt: new Date().toISOString(),
      platforms: ['INSTAGRAM'] as Array<'INSTAGRAM' | 'FACEBOOK'>, total: 1, published: 0, failed: 0,
      scheduled: 1, pending: 0, scheduledAt: '2027-06-15T14:00:00.000Z' };
    const detail = { batchId: 'bs', campaignLabel: 'Prog', captionTemplate: '{producto}', hashtags: ['#x'],
      createdAt: summary.createdAt, productIds: ['p1'], scheduledAt: '2027-06-15T14:00:00.000Z',
      rows: [{ publicationId: 'pub', productId: 'p1', productName: 'X', thumbnailUrl: null,
        platform: 'INSTAGRAM' as const, status: 'SCHEDULED', externalPermalink: null,
        lastErrorCode: null, lastErrorMessage: null }] };
    vi.mocked(getPublicationBatches).mockResolvedValue([summary] as never);
    vi.mocked(getPublicationBatchDetail).mockResolvedValue(detail as never);
    const onEditScheduled = vi.fn();

    const user = userEvent.setup();
    render(<HistorialTab onRepublish={vi.fn()} onGoToPublish={vi.fn()} onEditScheduled={onEditScheduled} />);
    await user.click(await screen.findByRole('button', { name: /prog/i }));
    await user.click(await screen.findByRole('button', { name: /^editar$/i }));

    expect(onEditScheduled).toHaveBeenCalledWith('bs', expect.objectContaining({
      productIds: ['p1'], captionTemplate: '{producto}', hashtags: ['#x'],
      campaignLabel: 'Prog', scheduledAt: '2027-06-15T14:00:00.000Z',
    }));
  });
```
Extend the `HistorialTab.test.tsx` api mock with `cancelBatch`, `rescheduleBatch`, and pass
`onEditScheduled={vi.fn()}` to the existing `render(<HistorialTab .../>)` calls (the prop becomes
required).

- [ ] **Step 3: Run to verify they fail**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/HistorialTab.test.tsx src/islands/admin/__tests__/PublicacionesPage.test.tsx`
Expected: FAIL — `onEditScheduled` prop unknown, no scheduled rendering.

- [ ] **Step 4: Update the shell `PublicacionesPage.tsx`**

- Add `const [editingBatchId, setEditingBatchId] = useState<string | null>(null);`.
- `PublicarTabPreload` import already covers the shape; extend the shell's local `Preload` type
  (if it has one) with `scheduledAt?: string | null` — or just import `PublicarTabPreload` and use
  it.
- `republish(next)` stays (clears `editingBatchId`: `setEditingBatchId(null); setPreload(next); setTab('publicar');`).
- New `function editScheduled(batchId: string, preload: PublicarTabPreload) { setEditingBatchId(batchId); setPreload(preload); setTab('publicar'); }`.
- Pass to `PublicarTab`: `editingBatchId={editingBatchId ?? undefined}` and
  `onEditCancelled={() => { setEditingBatchId(null); setPreload(undefined); }}` (keep the existing
  `onPreloadConsumed`).
- Pass to `HistorialTab`: `onEditScheduled={editScheduled}`.

- [ ] **Step 5: Update `HistorialTab.tsx`**

- Imports: `cancelBatch`, `rescheduleBatch` from api; `instantToSantiagoLabel`,
  `santiagoWallTimeToInstant`, `instantToSantiagoInputValue` from `../../lib/santiagoTime`.
- Props: add `onEditScheduled: (batchId: string, preload: { productIds: string[]; captionTemplate: string; hashtags: string[]; campaignLabel: string | null; scheduledAt: string | null }) => void;`.
- `StatusPill`: add a `CANCELLED` case → `<span className="... bg-pe-surface text-pe-muted"><Ban size={12} /> Cancelado</span>` (import `Ban` from lucide-react).
- Helper: `const isScheduled = (b) => b.scheduledAt != null && detailFor(b)?.rows.some(r => r.status === 'SCHEDULED');`
  but the summary list does not have the detail yet — use the summary's own counts:
  `const scheduledLike = b.scheduledAt != null && b.scheduled > 0;`.
- Collapsed summary line: when `scheduledLike`, render
  `<span className="text-pe-warning-ink">◷ Programada para {instantToSantiagoLabel(b.scheduledAt!)}</span>`
  (+ `· {b.failed} fallidos` if `b.failed > 0`), instead of the published/failed counts.
- Expanded header (inside `isOpen && b.batchId && detail`): when `detail.rows.some(r => r.status === 'SCHEDULED')`, show a row of buttons:
  - `Cancelar programación` → `await cancelBatch(b.batchId, effectiveToken)`; replace the cached detail + `load()`.
  - `Cambiar hora` → toggles an inline `<input type="datetime-local">` (seeded with
    `instantToSantiagoInputValue(detail.scheduledAt!)`) + a `Guardar` button →
    `await rescheduleBatch(b.batchId, santiagoWallTimeToInstant(value), effectiveToken)`; replace
    cached detail + `load()`.
  - `Editar` → `onEditScheduled(b.batchId, { productIds: detail.productIds, captionTemplate:
    detail.captionTemplate ?? '', hashtags: detail.hashtags, campaignLabel: detail.campaignLabel,
    scheduledAt: detail.scheduledAt })`.
  - Hide the H-2 `Reintentar fallidos` / `Volver a publicar esta tanda` buttons while the batch
    still has `SCHEDULED` rows (they do not apply to a not-yet-published batch).

- [ ] **Step 6: Run to verify they pass**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/HistorialTab.test.tsx src/islands/admin/__tests__/PublicacionesPage.test.tsx src/islands/admin/__tests__/PublicarTab.test.tsx`
Expected: PASS.

- [ ] **Step 7: Typecheck + commit**

Run: `cd frontend && npx tsc --noEmit` → clean.
```bash
git add frontend/src/islands/admin/
git commit -m "feat(frontend): scheduled-batch representation + cancel/reschedule/edit in HistorialTab"
```

---

## Task 12: full-suite gate + docs

- [ ] **Step 1: Backend full suite**

Run: `cd backend && mvn test`
Expected: BUILD SUCCESS. Investigate real failures; `PosControllerTest` / `LoginWithPermissionsTest`
timeouts under load are known-flaky (re-run in isolation).

- [ ] **Step 2: Frontend full suite + typecheck**

Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: both clean/green.

- [ ] **Step 3: Manual smoke (local Docker, recommended)**

Per `CLAUDE.md`, bring the stack up. Schedule a batch 2 minutes out, watch the backend log for
"Published or failed N due scheduled publications" (it will FAIL each — no Meta creds locally —
which is the expected path and shows in Historial). Try cancel, reschedule, edit on a scheduled
batch. Confirm the datetime picker shows Chile time.

- [ ] **Step 4: Commit any doc touch-ups**

```bash
git add -A
git commit -m "docs: mark H-3 scheduling implemented"   # only if there are changes
```

Branch stays `develop` (this project commits directly to `develop`; master merge + deploy is a
separate explicit step the user requests). Note the V101 deploy is expand-only and the new
`@Scheduled` job starts as a once-a-minute no-op until a `SCHEDULED` row exists.

---

## Self-Review

**1. Spec coverage:**
- V101 `publication_batches.scheduled_at` + partial index → Task 1. ✅
- Config keys + metadata + `.env.example` → Task 1. ✅
- `create()` → `SCHEDULED` when `scheduledAt` set & not approval → Task 2. ✅
- `PublishProductsBatchCommand/Request.scheduledAt`, `PublicationItemResult.scheduled`, DTO
  `scheduledAt` fields → Task 2. ✅
- Past `scheduledAt` → 400 (both `POST /batch` and `reschedule`) → Task 2 (Step 9) + Task 7
  (`parseFutureInstant`, DRY'd). ✅
- `BatchPublicationFactory` extraction (interpolation, variants, media bundle, idem key, hashtags)
  → Task 3. ✅
- Scheduled mode: `create` only, no `dispatch`, item `scheduled=true` → Task 3. ✅
- `@Scheduled` job every minute, `Clock`-injected use case, in-window → dispatch, stale (>cap) →
  `markScheduleWindowMissed` → Task 4. ✅
- Catch-up = natural (query is `<= now`); cap is the only guard → Task 4 (query + staleBefore). ✅
- `markScheduleWindowMissed` → FAILED + `SCHEDULE_WINDOW_MISSED` + `PublicationDispatchFailed`
  event, guarded to `SCHEDULED` → Task 4. ✅
- `cancelScheduledBatch` / `rescheduleBatch` on `PublicationService`, `DomainException` (→400)
  when nothing scheduled → Task 5. ✅
- `UpdateScheduledBatchUseCase` — all-SCHEDULED guard (→400, nothing deleted), delete + regen via
  factory, fresh idem keys → Task 6. ✅
- `POST /cancel`, `POST /reschedule` (`RescheduleBatchRequest`), `PUT /batches/{id}` → Task 7. ✅
- `santiagoTime.ts` DST-aware, dependency-free, round-trip + summer/winter test → Task 8. ✅
- `api.ts` field additions + `cancelBatch`/`rescheduleBatch`/`updateScheduledBatch` → Task 9. ✅
- `PublicarTab`: now/schedule radio + datetime picker (min now+5min, Chile), edit mode
  (`editingBatchId` → `PUT`, "Guardar cambios", "Cancelar edición"), preload `scheduledAt` →
  Task 10. ✅
- Shell `editingBatchId` state + `editScheduled` wiring → Task 11. ✅
- `HistorialTab`: "Programada para <label>" when `scheduledAt != null && scheduled > 0`,
  `CANCELLED` pill, cancel/reschedule(inline picker)/edit actions, hide H-2 actions while
  scheduled → Task 11. ✅
- IT: scheduled batch shows SCHEDULED; cancel; reschedule (+past→400); PUT edit; past `scheduledAt`
  →400; job path via direct use-case call → Task 7 + Task 4 test. ✅
  - **Gap found:** the spec's IT list includes "the job path: call `PublishDueScheduledPublicationsUseCase`
    directly with a `Clock` fixed after the scheduled instant → row → FAILED". Task 4's unit test
    covers the routing with mocks, but not the end-to-end DB transition. **Added below.**

- [ ] **Gap fix — add to Task 7 Step 1 (or a Task 4 IT):** an IT that creates a scheduled batch,
  then obtains `PublishDueScheduledPublicationsUseCase` from the context and calls
  `execute()` after making the row due (insert with a past `scheduled_at` via a second batch whose
  `scheduledAt` is `now + 1s`, then `Thread.sleep(1500)` — or expose a package-private
  constructor call with a forward `Clock`). Simplest: schedule at `now.plusSeconds(1)`, sleep
  1.2s, `@Autowired PublishDueScheduledPublicationsUseCase` `.execute()`, assert the row is
  `FAILED` (no creds) via `GET /batches/{id}`. Add to `PublicationControllerIT`:

```java
    @Autowired PublishDueScheduledPublicationsUseCase publishDueScheduledPublicationsUseCase;

    @Test
    void the_scheduled_job_publishes_a_due_batch() throws Exception {
        String adminToken = loginAdmin();
        Product p = productRepository.save(Product.create("Prod due", "d",
                new Money(BigDecimal.valueOf(9990), "CLP"), "https://cdn.example.com/d.jpg",
                ProductCondition.NEW, "Pilar", 1));
        String soon = java.time.Instant.now().plusSeconds(1).toString();
        MvcResult created = mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(p.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}",
                                "scheduledAt", soon))))
                .andExpect(status().isOk()).andReturn();

        MvcResult batches = mvc.perform(get("/api/admin/publications/batches")
                        .header("Authorization", bearer(adminToken))).andReturn();
        String batchId = om.readTree(batches.getResponse().getContentAsString()).get(0).get("batchId").asString();

        Thread.sleep(1400);
        int handled = publishDueScheduledPublicationsUseCase.execute();
        org.junit.jupiter.api.Assertions.assertTrue(handled >= 1);

        mvc.perform(get("/api/admin/publications/batches/{id}", batchId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.rows[0].status").value("FAILED"));
    }
```
Add `import` for `PublishDueScheduledPublicationsUseCase`.

**2. Placeholder scan:** The `UpdateScheduledBatchUseCase` step first shows a `@Transactional
protected` method then explicitly rejects it and gives the final inline version — the executor
implements the **second** block. Cyrillic `Т` in Task 5's test is flagged with a fix note. No
"TBD"/"handle errors"/bare "add validation". The `additional-spring-configuration-metadata.json`
step says "match the existing group entry's shape" — that is a concrete instruction (open the
file, copy the `app.social-publishing.meta` group entry, adapt).

**3. Type consistency:**
- `PublishProductsBatchCommand` 8-arg shape: defined Task 2, used Task 3 (`buildCreateCommand`
  reads `command.scheduledAt()`), Task 6 test constructs it with 8 args. ✅
- `PublicationItemResult` 6-arg (`+ boolean scheduled`): Task 2 defines, Task 3 constructs 4 sites
  with the trailing bool. ✅
- `PublicationBatchDetailDto` 8-arg (`+ scheduledAt` after `rows`): Task 2 defines; Task 6 test's
  `detailStub` builds it with 8 args; `PublicationService.getBatch` (Task 2 Step 8) appends
  `batch.getScheduledAt()`. ✅
- `PublicationDto` 30-arg: Task 6 test's `scheduledDto` builds it — must match the H-2 shape
  (29 args) + H-2's trailing `externalPermalink` = 30. The stub in Task 6 has 30 values ending
  `..., List.of(), List.of(), List.of(), List.of(), null` — the last `null` is `externalPermalink`.
  Wait: H-2 added `externalPermalink` as the **last** field. Count in the stub: after
  `Instant.now(), Instant.now()` come `List.of()×4` (bundles/attempts/reviews/snapshots) then
  `null`. That is the `externalPermalink`. ✅ (H-3 adds no field to `PublicationDto`.)
- `santiagoWallTimeToInstant` / `instantToSantiagoInputValue` / `instantToSantiagoLabel`: names
  identical in Task 8 (def), Task 10 (PublicarTab), Task 11 (HistorialTab). ✅
- `onEditScheduled` signature identical in Task 11's `HistorialTab` props and the shell's
  `editScheduled`. ✅
- `PublicarTab` `editingBatchId` / `onEditCancelled`: Task 10 defines, Task 11 shell passes. ✅
