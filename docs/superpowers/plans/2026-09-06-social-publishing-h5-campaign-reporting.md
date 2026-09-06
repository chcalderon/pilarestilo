# Campaign Reporting + Meta Engagement Metrics — Implementation Plan (H-5)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the admin a per-campaign rollup — grouped by `publication_batches.campaign_label` — of what went live on each platform, with links, plus engagement metrics (impressions / reach / likes / comments / shares / saved) pulled from the Meta Graph API per published post.

**Architecture:** The metrics subsystem mirrors publishing: a `PublicationMetricsFetcher` port with a `MetaMetricsFetcher` that routes on platform to `InstagramMetricsFetcher` / `FacebookMetricsFetcher` (same shape as `MetaDirectPublicationDispatcher` + its two publisher adapters). A `RefreshMetricsUseCase` selects PUBLISHED publications (by campaign label, or by recency for a daily `@Scheduled` job), fetches each, and upserts one `publication_metrics` row per publication. A read-only `CampaignReportService` groups batches + publications + metrics in memory. A third admin tab, `CampanasTab`, renders it. Every Meta failure degrades to a stored `fetch_error` string and a "no disponible" cell — it never fails a run or a page load.

**Tech Stack:** Spring Boot 4 (Java 25), JPA/Hibernate, Flyway, `RestClient`, Jackson 3; Testcontainers + MockMvc ITs, JUnit 5 + Mockito, `MockRestServiceServer`; Astro 5 SSR + React islands, Vitest + happy-dom + React Testing Library.

**Spec:** `docs/superpowers/specs/2026-09-06-social-publishing-h5-campaign-reporting-design.md`

## Global Constraints

- Hexagonal: one use-case class per action; domain objects carry no framework annotations; JPA entities separate from domain models.
- Flyway only; never edit an applied migration. Current highest is **V102** → this adds **V103**.
- Jackson 3: `tools.jackson.databind.ObjectMapper` / `JsonNode`, never `com.fasterxml`.
- Every Meta Graph call reads `.body(String.class)` then `objectMapper.readTree(raw)` (Graph serves JSON as `Content-Type: text/javascript`).
- Meta metrics may need scopes the token lacks. **Degrade gracefully:** any fetch failure → `PublicationMetricsFetcher.Result.failed(msg)` → the message lands in `publication_metrics.fetch_error` → the UI shows "no disponible". Never throw out of a refresh run or a report read.
- Permissions: reuse `PermissionRegistry.PUBLICATIONS_READ` (reads) and `PUBLICATIONS_UPDATE` (the refresh POST). No new permission.
- `notification-service` maps none of the publication tables — `publication_metrics` needs no `*RoEntity`. `ReadOnlyMappingIT` runs V103.
- Single production instance → the daily `@Scheduled` refresh needs no lock.
- Backend test: `cd backend && mvn test -Dtest=<Class>`. Frontend: `cd frontend && npx vitest run <path>` and `cd frontend && ./node_modules/.bin/tsc --noEmit` (NOT `npx tsc`).
- Before writing any `CampanasTab` markup: invoke `ui-ux-pro-max` and `impeccable` (session rule).
- Caveman mode is chat-only; code, comments, commits, spec prose stay normal.

---

## Task 1: V103 migration + `PublicationMetricsEntity` + repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V103__publication_metrics.sql`
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/entities/PublicationMetricsEntity.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/repositories/PublicationMetricsJpaRepository.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/persistence/repositories/PublicationMetricsJpaRepositoryIT.java` (create)

**Interfaces:**
- Produces:
  - `PublicationMetricsEntity` — `@Id UUID publicationId`; `Long` fields `impressions, reach, likes, comments, shares, saved`; `Instant fetchedAt` (not null); `String fetchError`.
  - `PublicationMetricsJpaRepository extends JpaRepository<PublicationMetricsEntity, UUID>` with `List<PublicationMetricsEntity> findByPublicationIdIn(Collection<UUID> ids)`.

- [ ] **Step 1: Write the failing test**

Create `PublicationMetricsJpaRepositoryIT.java`:

```java
package com.pilarestilo.publication.infrastructure.persistence.repositories;

import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class PublicationMetricsJpaRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("pilarestilo_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired PublicationMetricsJpaRepository repo;

    @Test
    void saves_and_finds_by_publication_id_in() {
        UUID pubId = UUID.randomUUID();
        PublicationMetricsEntity e = new PublicationMetricsEntity();
        e.setPublicationId(pubId);
        e.setLikes(42L);
        e.setImpressions(null);
        e.setFetchedAt(Instant.now());
        repo.save(e);

        List<PublicationMetricsEntity> found = repo.findByPublicationIdIn(List.of(pubId, UUID.randomUUID()));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getLikes()).isEqualTo(42L);
        assertThat(found.get(0).getImpressions()).isNull();
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=PublicationMetricsJpaRepositoryIT`
Expected: FAIL — `PublicationMetricsEntity` / `PublicationMetricsJpaRepository` do not exist; and Flyway `validate` fails on a missing `publication_metrics` table once the entity exists.

- [ ] **Step 3: Create the migration**

`backend/src/main/resources/db/migration/V103__publication_metrics.sql`:

```sql
-- Engagement metrics for a published social post, pulled from the Meta Graph API.
-- One row per publication, upserted on each refresh. NULL metrics mean the platform does not
-- report that number for this post (or a fetch has not populated it yet). fetch_error holds the
-- last failure message (bad token scope, deleted post) so the UI can say "no disponible".
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

- [ ] **Step 4: Create the entity**

`PublicationMetricsEntity.java`:

```java
package com.pilarestilo.publication.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

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

    public UUID getPublicationId() { return publicationId; }
    public void setPublicationId(UUID publicationId) { this.publicationId = publicationId; }
    public Long getImpressions() { return impressions; }
    public void setImpressions(Long impressions) { this.impressions = impressions; }
    public Long getReach() { return reach; }
    public void setReach(Long reach) { this.reach = reach; }
    public Long getLikes() { return likes; }
    public void setLikes(Long likes) { this.likes = likes; }
    public Long getComments() { return comments; }
    public void setComments(Long comments) { this.comments = comments; }
    public Long getShares() { return shares; }
    public void setShares(Long shares) { this.shares = shares; }
    public Long getSaved() { return saved; }
    public void setSaved(Long saved) { this.saved = saved; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
    public String getFetchError() { return fetchError; }
    public void setFetchError(String fetchError) { this.fetchError = fetchError; }
}
```

- [ ] **Step 5: Create the repository**

`PublicationMetricsJpaRepository.java`:

```java
package com.pilarestilo.publication.infrastructure.persistence.repositories;

import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PublicationMetricsJpaRepository extends JpaRepository<PublicationMetricsEntity, UUID> {
    List<PublicationMetricsEntity> findByPublicationIdIn(Collection<UUID> ids);
}
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=PublicationMetricsJpaRepositoryIT`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V103__publication_metrics.sql \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/entities/PublicationMetricsEntity.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/repositories/PublicationMetricsJpaRepository.java \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/persistence/repositories/PublicationMetricsJpaRepositoryIT.java
git commit -m "feat(publication): V103 publication_metrics table + entity"
```

---

## Task 2: `PostMetrics` value object + `PublicationMetricsFetcher` port

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/domain/model/PostMetrics.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/application/ports/PublicationMetricsFetcher.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/domain/model/PostMetricsTest.java` (create)

**Interfaces:**
- Produces:
  - `record PostMetrics(Long impressions, Long reach, Long likes, Long comments, Long shares, Long saved)` with a static `PostMetrics empty()`.
  - `interface PublicationMetricsFetcher { Result fetch(PublicationPlatform platform, String externalPostId); }` where `record Result(java.util.Optional<PostMetrics> metrics, String error)` has static `ok(PostMetrics)` and `failed(String)`.

- [ ] **Step 1: Write the failing test**

Create `PostMetricsTest.java`:

```java
package com.pilarestilo.publication.domain.model;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostMetricsTest {

    @Test
    void empty_has_all_null_fields() {
        PostMetrics m = PostMetrics.empty();
        assertEquals(null, m.likes());
        assertEquals(null, m.impressions());
    }

    @Test
    void result_ok_carries_the_metrics_and_no_error() {
        var r = PublicationMetricsFetcher.Result.ok(new PostMetrics(10L, 8L, 5L, 1L, 0L, 2L));
        assertTrue(r.metrics().isPresent());
        assertEquals(5L, r.metrics().get().likes());
        assertEquals(null, r.error());
    }

    @Test
    void result_failed_carries_the_message_and_no_metrics() {
        var r = PublicationMetricsFetcher.Result.failed("403 permission denied");
        assertFalse(r.metrics().isPresent());
        assertEquals("403 permission denied", r.error());
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=PostMetricsTest`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Create `PostMetrics`**

`PostMetrics.java`:

```java
package com.pilarestilo.publication.domain.model;

public record PostMetrics(
        Long impressions, Long reach, Long likes, Long comments, Long shares, Long saved) {

    public static PostMetrics empty() {
        return new PostMetrics(null, null, null, null, null, null);
    }
}
```

- [ ] **Step 4: Create the port**

`PublicationMetricsFetcher.java`:

```java
package com.pilarestilo.publication.application.ports;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.model.PostMetrics;

import java.util.Optional;

public interface PublicationMetricsFetcher {

    /** Empty result when the fetch failed for any reason (bad scope, deleted post, network). */
    Result fetch(PublicationPlatform platform, String externalPostId);

    record Result(Optional<PostMetrics> metrics, String error) {
        public static Result ok(PostMetrics m) { return new Result(Optional.of(m), null); }
        public static Result failed(String error) { return new Result(Optional.empty(), error); }
    }
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=PostMetricsTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/domain/model/PostMetrics.java \
        backend/src/main/java/com/pilarestilo/publication/application/ports/PublicationMetricsFetcher.java \
        backend/src/test/java/com/pilarestilo/publication/domain/model/PostMetricsTest.java
git commit -m "feat(publication): PostMetrics value object + PublicationMetricsFetcher port"
```

---

## Task 3: `InstagramMetricsFetcher`

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/InstagramMetricsFetcher.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/InstagramMetricsFetcherTest.java` (create)

**Interfaces:**
- Consumes: `PublicationMetricsFetcher.Result` / `PostMetrics` (Task 2); `MetaPublishingConfigResolver` (existing).
- Produces: `class InstagramMetricsFetcher` (package-private `@Component`) with `PublicationMetricsFetcher.Result fetch(String externalPostId)`.

- [ ] **Step 1: Write the failing test**

Create `InstagramMetricsFetcherTest.java`:

```java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InstagramMetricsFetcherTest {

    private MetaPublishingConfigResolver config() {
        MetaPublishingConfigResolver r = mock(MetaPublishingConfigResolver.class);
        when(r.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "ig-user", "token-ig", "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null));
        return r;
    }

    @Test
    void reads_like_and_comment_counts_and_insight_metrics() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("m-1?fields=like_count%2Ccomments_count")))
                .andRespond(withSuccess("{\"like_count\":45,\"comments_count\":3}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("m-1/insights")))
                .andRespond(withSuccess(
                        "{\"data\":[{\"name\":\"reach\",\"values\":[{\"value\":900}]},"
                        + "{\"name\":\"saved\",\"values\":[{\"value\":12}]}]}",
                        MediaType.APPLICATION_JSON));

        var result = new InstagramMetricsFetcher(b, config(), new tools.jackson.databind.ObjectMapper()).fetch("m-1");

        assertTrue(result.metrics().isPresent());
        assertEquals(45L, result.metrics().get().likes());
        assertEquals(3L, result.metrics().get().comments());
        assertEquals(900L, result.metrics().get().reach());
        assertEquals(12L, result.metrics().get().saved());
        assertEquals(null, result.metrics().get().impressions());
        server.verify();
    }

    @Test
    void returns_failed_on_a_graph_error() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("m-1?fields=like_count")))
                .andRespond(withServerError());

        var result = new InstagramMetricsFetcher(b, config(), new tools.jackson.databind.ObjectMapper()).fetch("m-1");

        assertFalse(result.metrics().isPresent());
    }

    @Test
    void returns_failed_when_credentials_are_missing() {
        MetaPublishingConfigResolver r = mock(MetaPublishingConfigResolver.class);
        when(r.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0", null, null,
                "https://graph.facebook.com/v23.0", null));

        var result = new InstagramMetricsFetcher(RestClient.builder(), r, new tools.jackson.databind.ObjectMapper()).fetch("m-1");
        assertFalse(result.metrics().isPresent());
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=InstagramMetricsFetcherTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement**

`InstagramMetricsFetcher.java`:

```java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.model.PostMetrics;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class InstagramMetricsFetcher {

    // Feed image/carousel metrics available on Graph v23.0. If Graph rejects a name, drop it
    // here — mapMetric already leaves absent metrics null. "impressions" is derived from "views"
    // when present (impressions was deprecated for media created after 2024-07).
    private static final String INSIGHT_METRICS = "reach,saved,shares,total_interactions,views";

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;
    private final ObjectMapper objectMapper;

    InstagramMetricsFetcher(RestClient.Builder restClientBuilder,
                            MetaPublishingConfigResolver configResolver,
                            ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
        this.objectMapper = objectMapper;
    }

    PublicationMetricsFetcher.Result fetch(String externalPostId) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.instagramUserId() == null || config.instagramAccessToken() == null) {
            return PublicationMetricsFetcher.Result.failed("Instagram credentials are not configured");
        }
        RestClient client = restClientBuilder.baseUrl(config.instagramBaseUrl()).build();
        String token = config.instagramAccessToken();
        try {
            JsonNode fields = getJson(client,
                    "/{mediaId}?fields=like_count,comments_count&access_token={token}", externalPostId, token);
            Long likes = asLong(fields.get("like_count"));
            Long comments = asLong(fields.get("comments_count"));

            JsonNode insights = getJson(client,
                    "/{mediaId}/insights?metric={metrics}&access_token={token}",
                    externalPostId, INSIGHT_METRICS, token);
            Long reach = null, saved = null, shares = null, impressions = null;
            if (insights.hasNonNull("data")) {
                for (JsonNode metric : insights.get("data")) {
                    String name = metric.hasNonNull("name") ? metric.get("name").asString() : "";
                    Long value = firstValue(metric);
                    switch (name) {
                        case "reach" -> reach = value;
                        case "saved" -> saved = value;
                        case "shares" -> shares = value;
                        case "views" -> impressions = value;
                        default -> { /* total_interactions and anything new: ignored */ }
                    }
                }
            }
            return PublicationMetricsFetcher.Result.ok(
                    new PostMetrics(impressions, reach, likes, comments, shares, saved));
        } catch (RuntimeException ex) {
            return PublicationMetricsFetcher.Result.failed(ex.getMessage());
        }
    }

    private Long firstValue(JsonNode metric) {
        JsonNode values = metric.get("values");
        if (values == null || !values.isArray() || values.isEmpty()) {
            return null;
        }
        return asLong(values.get(0).get("value"));
    }

    private Long asLong(JsonNode node) {
        return node == null || node.isNull() ? null : node.asLong();
    }

    private JsonNode getJson(RestClient client, String uri, Object... uriVars) {
        String raw = client.get().uri(uri, uriVars).retrieve().body(String.class);
        return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=InstagramMetricsFetcherTest`
Expected: PASS. If the first-request matcher fails on the URL-encoded comma, adjust the `containsString` to `m-1?fields=like_count` (RestClient percent-encodes `,` to `%2C`).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/InstagramMetricsFetcher.java \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/InstagramMetricsFetcherTest.java
git commit -m "feat(publication): InstagramMetricsFetcher — like/comment counts + media insights"
```

---

## Task 4: `FacebookMetricsFetcher`

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/FacebookMetricsFetcher.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/FacebookMetricsFetcherTest.java` (create)

**Interfaces:**
- Consumes: `PublicationMetricsFetcher.Result` / `PostMetrics` (Task 2); `MetaPublishingConfigResolver`.
- Produces: `class FacebookMetricsFetcher` (package-private `@Component`) with `PublicationMetricsFetcher.Result fetch(String externalPostId)`.

- [ ] **Step 1: Write the failing test**

Create `FacebookMetricsFetcherTest.java`:

```java
package com.pilarestilo.publication.infrastructure.meta;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacebookMetricsFetcherTest {

    private MetaPublishingConfigResolver config() {
        MetaPublishingConfigResolver r = mock(MetaPublishingConfigResolver.class);
        when(r.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                "page-1", "token-fb", "https://graph.facebook.com/v23.0", null));
        return r;
    }

    @Test
    void reads_like_comment_share_counts_and_insights() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("p_1?fields=likes.summary")))
                .andRespond(withSuccess(
                        "{\"likes\":{\"summary\":{\"total_count\":30}},"
                        + "\"comments\":{\"summary\":{\"total_count\":4}},"
                        + "\"shares\":{\"count\":2}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("p_1/insights")))
                .andRespond(withSuccess(
                        "{\"data\":[{\"name\":\"post_impressions\",\"values\":[{\"value\":1200}]},"
                        + "{\"name\":\"post_impressions_unique\",\"values\":[{\"value\":800}]}]}",
                        MediaType.APPLICATION_JSON));

        var result = new FacebookMetricsFetcher(b, config(), new tools.jackson.databind.ObjectMapper()).fetch("p_1");

        assertTrue(result.metrics().isPresent());
        assertEquals(30L, result.metrics().get().likes());
        assertEquals(4L, result.metrics().get().comments());
        assertEquals(2L, result.metrics().get().shares());
        assertEquals(1200L, result.metrics().get().impressions());
        assertEquals(800L, result.metrics().get().reach());
        server.verify();
    }

    @Test
    void keeps_the_summary_counts_when_the_insights_call_is_forbidden() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();

        server.expect(requestTo(org.hamcrest.Matchers.containsString("p_1?fields=likes.summary")))
                .andRespond(withSuccess("{\"likes\":{\"summary\":{\"total_count\":30}},"
                        + "\"comments\":{\"summary\":{\"total_count\":4}}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("p_1/insights")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

        var result = new FacebookMetricsFetcher(b, config(), new tools.jackson.databind.ObjectMapper()).fetch("p_1");

        assertTrue(result.metrics().isPresent());
        assertEquals(30L, result.metrics().get().likes());
        assertEquals(null, result.metrics().get().impressions());
    }

    @Test
    void returns_failed_when_the_summary_call_fails() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("p_1?fields=likes.summary")))
                .andRespond(withServerError());

        var result = new FacebookMetricsFetcher(b, config(), new tools.jackson.databind.ObjectMapper()).fetch("p_1");
        assertFalse(result.metrics().isPresent());
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=FacebookMetricsFetcherTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement**

`FacebookMetricsFetcher.java`:

```java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.model.PostMetrics;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class FacebookMetricsFetcher {

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;
    private final ObjectMapper objectMapper;

    FacebookMetricsFetcher(RestClient.Builder restClientBuilder,
                           MetaPublishingConfigResolver configResolver,
                           ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
        this.objectMapper = objectMapper;
    }

    PublicationMetricsFetcher.Result fetch(String externalPostId) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.facebookPageId() == null || config.facebookPageAccessToken() == null) {
            return PublicationMetricsFetcher.Result.failed("Facebook credentials are not configured");
        }
        RestClient client = restClientBuilder.baseUrl(config.facebookBaseUrl()).build();
        String token = config.facebookPageAccessToken();

        Long likes, comments, shares;
        try {
            JsonNode summary = getJson(client,
                    "/{postId}?fields=likes.summary(true),comments.summary(true),shares&access_token={token}",
                    externalPostId, token);
            likes = summaryCount(summary.get("likes"));
            comments = summaryCount(summary.get("comments"));
            shares = summary.hasNonNull("shares") && summary.get("shares").hasNonNull("count")
                    ? summary.get("shares").get("count").asLong() : null;
        } catch (RuntimeException ex) {
            return PublicationMetricsFetcher.Result.failed(ex.getMessage());
        }

        Long impressions = null, reach = null;
        try {
            JsonNode insights = getJson(client,
                    "/{postId}/insights?metric=post_impressions,post_impressions_unique&access_token={token}",
                    externalPostId, token);
            if (insights.hasNonNull("data")) {
                for (JsonNode metric : insights.get("data")) {
                    String name = metric.hasNonNull("name") ? metric.get("name").asString() : "";
                    Long value = firstValue(metric);
                    if ("post_impressions".equals(name)) impressions = value;
                    else if ("post_impressions_unique".equals(name)) reach = value;
                }
            }
        } catch (RuntimeException insightsError) {
            // read_insights may not be granted — keep the like/comment/share counts.
        }

        return PublicationMetricsFetcher.Result.ok(
                new PostMetrics(impressions, reach, likes, comments, shares, null));
    }

    private Long summaryCount(JsonNode node) {
        if (node == null || !node.hasNonNull("summary") || !node.get("summary").hasNonNull("total_count")) {
            return null;
        }
        return node.get("summary").get("total_count").asLong();
    }

    private Long firstValue(JsonNode metric) {
        JsonNode values = metric.get("values");
        if (values == null || !values.isArray() || values.isEmpty() || values.get(0).get("value") == null) {
            return null;
        }
        return values.get(0).get("value").asLong();
    }

    private JsonNode getJson(RestClient client, String uri, Object... uriVars) {
        String raw = client.get().uri(uri, uriVars).retrieve().body(String.class);
        return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=FacebookMetricsFetcherTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/FacebookMetricsFetcher.java \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/FacebookMetricsFetcherTest.java
git commit -m "feat(publication): FacebookMetricsFetcher — like/comment/share counts + post insights"
```

---

## Task 5: `MetaMetricsFetcher` router

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/MetaMetricsFetcher.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/MetaMetricsFetcherTest.java` (create)

**Interfaces:**
- Consumes: `InstagramMetricsFetcher.fetch(String)` (Task 3), `FacebookMetricsFetcher.fetch(String)` (Task 4).
- Produces: `class MetaMetricsFetcher implements PublicationMetricsFetcher` (`@Component`) — `fetch(platform, id)` switches on platform and delegates.

- [ ] **Step 1: Write the failing test**

Create `MetaMetricsFetcherTest.java`:

```java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.model.PostMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaMetricsFetcherTest {

    @Mock InstagramMetricsFetcher instagram;
    @Mock FacebookMetricsFetcher facebook;

    @Test
    void routes_instagram_to_the_instagram_fetcher() {
        when(instagram.fetch("m-1")).thenReturn(
                PublicationMetricsFetcher.Result.ok(new PostMetrics(1L, 1L, 1L, 1L, 1L, 1L)));
        var out = new MetaMetricsFetcher(instagram, facebook).fetch(PublicationPlatform.INSTAGRAM, "m-1");
        assertEquals(1L, out.metrics().get().likes());
        verify(instagram).fetch("m-1");
        verifyNoInteractions(facebook);
    }

    @Test
    void routes_facebook_to_the_facebook_fetcher() {
        when(facebook.fetch("p_1")).thenReturn(PublicationMetricsFetcher.Result.failed("nope"));
        var out = new MetaMetricsFetcher(instagram, facebook).fetch(PublicationPlatform.FACEBOOK, "p_1");
        assertEquals("nope", out.error());
        verify(facebook).fetch("p_1");
        verifyNoInteractions(instagram);
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=MetaMetricsFetcherTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement**

`MetaMetricsFetcher.java`:

```java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import org.springframework.stereotype.Component;

@Component
public class MetaMetricsFetcher implements PublicationMetricsFetcher {

    private final InstagramMetricsFetcher instagram;
    private final FacebookMetricsFetcher facebook;

    public MetaMetricsFetcher(InstagramMetricsFetcher instagram, FacebookMetricsFetcher facebook) {
        this.instagram = instagram;
        this.facebook = facebook;
    }

    @Override
    public Result fetch(PublicationPlatform platform, String externalPostId) {
        return switch (platform) {
            case INSTAGRAM -> instagram.fetch(externalPostId);
            case FACEBOOK -> facebook.fetch(externalPostId);
        };
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=MetaMetricsFetcherTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/MetaMetricsFetcher.java \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/MetaMetricsFetcherTest.java
git commit -m "feat(publication): MetaMetricsFetcher routes metrics fetch by platform"
```

---

## Task 6: `MetricsUpsertService`

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/application/MetricsUpsertService.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/application/MetricsUpsertServiceTest.java` (create)

**Interfaces:**
- Consumes: `PublicationMetricsFetcher.Result` (Task 2), `PublicationMetricsJpaRepository` (Task 1).
- Produces: `MetricsUpsertService.upsert(UUID publicationId, PublicationMetricsFetcher.Result result, Instant now)` — inserts or updates one `publication_metrics` row; on a failed result keeps existing metric values and sets `fetchError`.

- [ ] **Step 1: Write the failing test**

Create `MetricsUpsertServiceTest.java`:

```java
package com.pilarestilo.publication.application;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.model.PostMetrics;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationMetricsJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsUpsertServiceTest {

    @Mock PublicationMetricsJpaRepository repo;

    @Test
    void inserts_a_new_row_on_a_successful_fetch() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        Instant now = Instant.parse("2026-09-06T12:00:00Z");

        new MetricsUpsertService(repo).upsert(id,
                PublicationMetricsFetcher.Result.ok(new PostMetrics(100L, 80L, 45L, 3L, 1L, 12L)), now);

        ArgumentCaptor<PublicationMetricsEntity> captor = ArgumentCaptor.forClass(PublicationMetricsEntity.class);
        verify(repo).save(captor.capture());
        assertEquals(45L, captor.getValue().getLikes());
        assertEquals(now, captor.getValue().getFetchedAt());
        assertNull(captor.getValue().getFetchError());
    }

    @Test
    void a_failed_fetch_keeps_old_values_and_sets_the_error() {
        UUID id = UUID.randomUUID();
        PublicationMetricsEntity existing = new PublicationMetricsEntity();
        existing.setPublicationId(id);
        existing.setLikes(40L);
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        new MetricsUpsertService(repo).upsert(id,
                PublicationMetricsFetcher.Result.failed("403 forbidden"), Instant.now());

        ArgumentCaptor<PublicationMetricsEntity> captor = ArgumentCaptor.forClass(PublicationMetricsEntity.class);
        verify(repo).save(captor.capture());
        assertEquals(40L, captor.getValue().getLikes());
        assertEquals("403 forbidden", captor.getValue().getFetchError());
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=MetricsUpsertServiceTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement**

`MetricsUpsertService.java`:

```java
package com.pilarestilo.publication.application;

import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationMetricsJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Upserts one publication_metrics row. @Transactional per call: RefreshMetricsUseCase loops over
 * this without an outer transaction so one bad write does not roll back the others.
 */
@Service
public class MetricsUpsertService {

    private final PublicationMetricsJpaRepository repository;

    public MetricsUpsertService(PublicationMetricsJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsert(UUID publicationId, PublicationMetricsFetcher.Result result, Instant now) {
        PublicationMetricsEntity e = repository.findById(publicationId).orElseGet(() -> {
            PublicationMetricsEntity n = new PublicationMetricsEntity();
            n.setPublicationId(publicationId);
            return n;
        });
        e.setFetchedAt(now);
        if (result.metrics().isPresent()) {
            var m = result.metrics().get();
            e.setImpressions(m.impressions());
            e.setReach(m.reach());
            e.setLikes(m.likes());
            e.setComments(m.comments());
            e.setShares(m.shares());
            e.setSaved(m.saved());
            e.setFetchError(null);
        } else {
            e.setFetchError(result.error());
        }
        repository.save(e);
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=MetricsUpsertServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/MetricsUpsertService.java \
        backend/src/test/java/com/pilarestilo/publication/application/MetricsUpsertServiceTest.java
git commit -m "feat(publication): MetricsUpsertService — per-call transactional metrics upsert"
```

---

## Task 7: `RefreshMetricsUseCase` + the two repository queries

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/application/usecases/MetricsRefreshScope.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/application/usecases/RefreshMetricsUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/repositories/PublicationJpaRepository.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/application/usecases/RefreshMetricsUseCaseTest.java` (create)

**Interfaces:**
- Consumes: `PublicationMetricsFetcher` (Task 2), `MetricsUpsertService.upsert(...)` (Task 6), `PublicationJpaRepository`.
- Produces:
  - `sealed interface MetricsRefreshScope permits Campaign, RecentDays` with `record Campaign(String label)` and `record RecentDays(int days)`.
  - `RefreshMetricsUseCase.execute(MetricsRefreshScope scope): MetricsRefreshResult` where `record MetricsRefreshResult(int refreshed, int failed)`.
  - `PublicationJpaRepository.findPublishedWithPostIdByCampaignLabel(String label)` and `findPublishedWithPostIdSince(Instant since)`.

- [ ] **Step 1: Write the failing test**

Create `RefreshMetricsUseCaseTest.java`:

```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.MetricsUpsertService;
import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.model.PostMetrics;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshMetricsUseCaseTest {

    @Mock PublicationJpaRepository publicationRepository;
    @Mock PublicationMetricsFetcher fetcher;
    @Mock MetricsUpsertService upsertService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);

    private PublicationEntity published(String postId, PublicationPlatform platform) {
        PublicationEntity p = new PublicationEntity();
        p.setId(UUID.randomUUID());
        p.setExternalPostId(postId);
        p.setPlatform(platform);
        return p;
    }

    @Test
    void campaign_scope_selects_by_label_and_counts_results() {
        var a = published("m-1", PublicationPlatform.INSTAGRAM);
        var b = published("p_1", PublicationPlatform.FACEBOOK);
        when(publicationRepository.findPublishedWithPostIdByCampaignLabel("Verano")).thenReturn(List.of(a, b));
        when(fetcher.fetch(PublicationPlatform.INSTAGRAM, "m-1"))
                .thenReturn(PublicationMetricsFetcher.Result.ok(PostMetrics.empty()));
        when(fetcher.fetch(PublicationPlatform.FACEBOOK, "p_1"))
                .thenReturn(PublicationMetricsFetcher.Result.failed("boom"));

        var result = new RefreshMetricsUseCase(publicationRepository, fetcher, upsertService, clock)
                .execute(new MetricsRefreshScope.Campaign("Verano"));

        assertEquals(1, result.refreshed());
        assertEquals(1, result.failed());
        verify(upsertService).upsert(eq(a.getId()), any(), eq(Instant.parse("2026-09-06T12:00:00Z")));
        verify(upsertService).upsert(eq(b.getId()), any(), any());
    }

    @Test
    void recent_days_scope_selects_by_published_at() {
        when(publicationRepository.findPublishedWithPostIdSince(Instant.parse("2026-08-07T12:00:00Z")))
                .thenReturn(List.of());
        var result = new RefreshMetricsUseCase(publicationRepository, fetcher, upsertService, clock)
                .execute(new MetricsRefreshScope.RecentDays(30));
        assertEquals(0, result.refreshed());
        assertEquals(0, result.failed());
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=RefreshMetricsUseCaseTest`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Add the repository queries**

In `PublicationJpaRepository.java`, add (after the existing finders):

```java
    @org.springframework.data.jpa.repository.Query("""
            select p from PublicationEntity p
            join com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity b
                 on b.id = p.batchId
            where p.status = com.pilarestilo.publication.domain.enums.PublicationStatus.PUBLISHED
              and p.externalPostId is not null
              and b.campaignLabel = :label
            order by p.createdAt asc
            """)
    List<PublicationEntity> findPublishedWithPostIdByCampaignLabel(String label);

    @org.springframework.data.jpa.repository.Query("""
            select p from PublicationEntity p
            where p.status = com.pilarestilo.publication.domain.enums.PublicationStatus.PUBLISHED
              and p.externalPostId is not null
              and p.publishedAt >= :since
            order by p.publishedAt asc
            """)
    List<PublicationEntity> findPublishedWithPostIdSince(java.time.Instant since);
```

Confirm `PublicationBatchEntity` has a `campaignLabel` field and `PublicationEntity` has `batchId` (both added in H-2). If the cross-entity `join ... on` JPQL is rejected by Hibernate, fall back to a native query with the same `WHERE`.

- [ ] **Step 4: Create `MetricsRefreshScope`**

`MetricsRefreshScope.java`:

```java
package com.pilarestilo.publication.application.usecases;

public sealed interface MetricsRefreshScope
        permits MetricsRefreshScope.Campaign, MetricsRefreshScope.RecentDays {

    record Campaign(String label) implements MetricsRefreshScope {}

    record RecentDays(int days) implements MetricsRefreshScope {}
}
```

- [ ] **Step 5: Create `RefreshMetricsUseCase`**

`RefreshMetricsUseCase.java`:

```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.publication.application.MetricsUpsertService;
import com.pilarestilo.publication.application.ports.PublicationMetricsFetcher;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Refreshes publication_metrics for a set of PUBLISHED posts. Not @Transactional over the loop:
 * each upsert is its own @Transactional call on MetricsUpsertService (same reasoning as
 * PublishProductsBatchUseCase).
 */
@Component
public class RefreshMetricsUseCase {

    private final PublicationJpaRepository publicationRepository;
    private final PublicationMetricsFetcher fetcher;
    private final MetricsUpsertService upsertService;
    private final Clock clock;

    @Autowired
    public RefreshMetricsUseCase(PublicationJpaRepository publicationRepository,
                                 PublicationMetricsFetcher fetcher,
                                 MetricsUpsertService upsertService) {
        this(publicationRepository, fetcher, upsertService, Clock.systemUTC());
    }

    RefreshMetricsUseCase(PublicationJpaRepository publicationRepository,
                          PublicationMetricsFetcher fetcher,
                          MetricsUpsertService upsertService,
                          Clock clock) {
        this.publicationRepository = publicationRepository;
        this.fetcher = fetcher;
        this.upsertService = upsertService;
        this.clock = clock;
    }

    public MetricsRefreshResult execute(MetricsRefreshScope scope) {
        List<PublicationEntity> targets = switch (scope) {
            case MetricsRefreshScope.Campaign c ->
                    publicationRepository.findPublishedWithPostIdByCampaignLabel(c.label());
            case MetricsRefreshScope.RecentDays r ->
                    publicationRepository.findPublishedWithPostIdSince(
                            Instant.now(clock).minus(Duration.ofDays(r.days())));
        };
        int refreshed = 0;
        int failed = 0;
        for (PublicationEntity p : targets) {
            PublicationMetricsFetcher.Result result = fetcher.fetch(p.getPlatform(), p.getExternalPostId());
            upsertService.upsert(p.getId(), result, Instant.now(clock));
            if (result.metrics().isPresent()) {
                refreshed++;
            } else {
                failed++;
            }
        }
        return new MetricsRefreshResult(refreshed, failed);
    }

    public record MetricsRefreshResult(int refreshed, int failed) {}
}
```

- [ ] **Step 6: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=RefreshMetricsUseCaseTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/usecases/MetricsRefreshScope.java \
        backend/src/main/java/com/pilarestilo/publication/application/usecases/RefreshMetricsUseCase.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/persistence/repositories/PublicationJpaRepository.java \
        backend/src/test/java/com/pilarestilo/publication/application/usecases/RefreshMetricsUseCaseTest.java
git commit -m "feat(publication): RefreshMetricsUseCase (campaign / recent scope) + repo queries"
```

---

## Task 8: `RefreshRecentMetricsScheduler` + config keys

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/jobs/RefreshRecentMetricsScheduler.java`
- Modify: `backend/src/main/resources/application.yml` (`app.social-publishing` block)
- Modify: `backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- Modify: `infra/.env.example`
- Modify: `infra/docker-compose.yml` (backend `environment:`)
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/jobs/RefreshRecentMetricsSchedulerTest.java` (create)

**Interfaces:**
- Consumes: `RefreshMetricsUseCase.execute(new MetricsRefreshScope.RecentDays(int))` (Task 7).
- Produces: a `@Scheduled` `@Component` that runs the recent-metrics refresh.

- [ ] **Step 1: Write the failing test**

Create `RefreshRecentMetricsSchedulerTest.java`:

```java
package com.pilarestilo.publication.infrastructure.jobs;

import com.pilarestilo.publication.application.usecases.MetricsRefreshScope;
import com.pilarestilo.publication.application.usecases.RefreshMetricsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshRecentMetricsSchedulerTest {

    @Mock RefreshMetricsUseCase useCase;

    @Test
    void runs_the_use_case_with_a_recent_days_scope() {
        when(useCase.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RefreshMetricsUseCase.MetricsRefreshResult(2, 1));

        new RefreshRecentMetricsScheduler(useCase, 30).run();

        ArgumentCaptor<MetricsRefreshScope> captor = ArgumentCaptor.forClass(MetricsRefreshScope.class);
        verify(useCase).execute(captor.capture());
        MetricsRefreshScope.RecentDays scope = (MetricsRefreshScope.RecentDays) captor.getValue();
        assertEquals(30, scope.days());
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=RefreshRecentMetricsSchedulerTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the scheduler**

`RefreshRecentMetricsScheduler.java`:

```java
package com.pilarestilo.publication.infrastructure.jobs;

import com.pilarestilo.publication.application.usecases.MetricsRefreshScope;
import com.pilarestilo.publication.application.usecases.RefreshMetricsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshRecentMetricsScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshRecentMetricsScheduler.class);

    private final RefreshMetricsUseCase useCase;
    private final int maxAgeDays;

    public RefreshRecentMetricsScheduler(RefreshMetricsUseCase useCase,
                                         @Value("${app.social-publishing.metrics.max-age-days:30}") int maxAgeDays) {
        this.useCase = useCase;
        this.maxAgeDays = maxAgeDays;
    }

    @Scheduled(cron = "${app.social-publishing.metrics.refresh-cron:0 0 6 * * *}")
    public void run() {
        RefreshMetricsUseCase.MetricsRefreshResult result =
                useCase.execute(new MetricsRefreshScope.RecentDays(maxAgeDays));
        if (result.refreshed() + result.failed() > 0) {
            log.info("Refreshed metrics for {} posts ({} failed)", result.refreshed(), result.failed());
        }
    }
}
```

- [ ] **Step 4: Register the config keys**

`application.yml` — under `app.social-publishing`, after the `schedule:` block:

```yaml
    metrics:
      refresh-cron: ${APP_SOCIAL_PUBLISHING_METRICS_REFRESH_CRON:0 0 6 * * *}
      max-age-days: ${APP_SOCIAL_PUBLISHING_METRICS_MAX_AGE_DAYS:30}
```

`additional-spring-configuration-metadata.json` — add two entries next to the
`app.social-publishing.schedule.*` ones:

```json
    {
      "name": "app.social-publishing.metrics.refresh-cron",
      "type": "java.lang.String",
      "description": "Cron for the daily engagement-metrics refresh job. Default: 06:00."
    },
    {
      "name": "app.social-publishing.metrics.max-age-days",
      "type": "java.lang.Integer",
      "description": "The scheduled refresh only touches posts published within this many days.",
      "defaultValue": 30
    },
```

`infra/.env.example` — after the `APP_SOCIAL_PUBLISHING_SCHEDULE_*` lines:

```
# Daily engagement-metrics refresh (Increment H, Etapa H-5). Defaults shown.
# APP_SOCIAL_PUBLISHING_METRICS_REFRESH_CRON=0 0 6 * * *
# APP_SOCIAL_PUBLISHING_METRICS_MAX_AGE_DAYS=30
```

`infra/docker-compose.yml` — in the backend service `environment:` block, after
`APP_SOCIAL_PUBLISHING_SCHEDULE_MAX_LATENESS_MINUTES`:

```yaml
      APP_SOCIAL_PUBLISHING_METRICS_REFRESH_CRON: ${APP_SOCIAL_PUBLISHING_METRICS_REFRESH_CRON:-0 0 6 * * *}
      APP_SOCIAL_PUBLISHING_METRICS_MAX_AGE_DAYS: ${APP_SOCIAL_PUBLISHING_METRICS_MAX_AGE_DAYS:-30}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=RefreshRecentMetricsSchedulerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/infrastructure/jobs/RefreshRecentMetricsScheduler.java \
        backend/src/main/resources/application.yml \
        backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json \
        infra/.env.example infra/docker-compose.yml \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/jobs/RefreshRecentMetricsSchedulerTest.java
git commit -m "feat(publication): daily engagement-metrics refresh scheduler + config"
```

---

## Task 9: `CampaignReportService` + DTOs

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/application/dto/CampaignSummaryDto.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/application/dto/CampaignDetailDto.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/application/CampaignReportService.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/application/CampaignReportServiceTest.java` (create)

**Interfaces:**
- Consumes: `PublicationBatchJpaRepository`, `PublicationJpaRepository.findByBatchIdInOrderByCreatedAtAsc`, `PublicationMetricsJpaRepository.findByPublicationIdIn` (Task 1), `ProductRepository.findAllByIds`.
- Produces:
  - `record CampaignSummaryDto(String label, Instant firstPostAt, Instant lastPostAt, int batchCount, int totalPosts, int published, int failed, int scheduled, Set<PublicationPlatform> platforms, MetricsTotals totals, int postsWithError)`
  - `record MetricsTotals(long impressions, long reach, long likes, long comments, long shares, long saved)` (nested in `CampaignSummaryDto`)
  - `record CampaignDetailDto(String label, Instant firstPostAt, Instant lastPostAt, List<PostRow> posts)` with nested `record PostRow(UUID publicationId, UUID productId, String productName, String thumbnailUrl, PublicationPlatform platform, PublicationStatus status, String externalPermalink, PostMetrics metrics, String fetchError, Instant fetchedAt)`
  - `CampaignReportService.listCampaigns(): List<CampaignSummaryDto>`, `getCampaign(String label): CampaignDetailDto`

- [ ] **Step 1: Write the failing test**

Create `CampaignReportServiceTest.java`:

```java
package com.pilarestilo.publication.application;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.dto.CampaignSummaryDto;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationMetricsJpaRepository;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignReportServiceTest {

    @Mock PublicationBatchJpaRepository batchRepo;
    @Mock PublicationJpaRepository publicationRepo;
    @Mock PublicationMetricsJpaRepository metricsRepo;
    @Mock ProductRepository productRepo;

    private PublicationBatchEntity batch(UUID id, String label) {
        PublicationBatchEntity b = new PublicationBatchEntity();
        b.setId(id);
        b.setCampaignLabel(label);
        b.setCreatedAt(Instant.parse("2026-09-01T00:00:00Z"));
        return b;
    }

    private PublicationEntity pub(UUID batchId, UUID productId, PublicationPlatform platform, PublicationStatus status) {
        PublicationEntity p = new PublicationEntity();
        p.setId(UUID.randomUUID());
        p.setBatchId(batchId);
        p.setProductId(productId);
        p.setPlatform(platform);
        p.setStatus(status);
        p.setCreatedAt(Instant.parse("2026-09-02T00:00:00Z"));
        return p;
    }

    @Test
    void groups_batches_by_label_and_sums_metrics_treating_null_as_zero() {
        UUID b1 = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        var pub1 = pub(b1, productId, PublicationPlatform.INSTAGRAM, PublicationStatus.PUBLISHED);
        var pub2 = pub(b1, productId, PublicationPlatform.FACEBOOK, PublicationStatus.FAILED);
        when(batchRepo.findAll()).thenReturn(List.of(batch(b1, "Verano")));
        when(publicationRepo.findByBatchIdInOrderByCreatedAtAsc(any())).thenReturn(List.of(pub1, pub2));

        PublicationMetricsEntity m = new PublicationMetricsEntity();
        m.setPublicationId(pub1.getId());
        m.setLikes(10L);
        m.setImpressions(null);
        m.setFetchError(null);
        m.setFetchedAt(Instant.now());
        when(metricsRepo.findByPublicationIdIn(any())).thenReturn(List.of(m));
        when(productRepo.findAllByIds(any())).thenReturn(List.of());

        List<CampaignSummaryDto> out = new CampaignReportService(batchRepo, publicationRepo, metricsRepo, productRepo)
                .listCampaigns();

        assertEquals(1, out.size());
        assertEquals("Verano", out.get(0).label());
        assertEquals(2, out.get(0).totalPosts());
        assertEquals(1, out.get(0).published());
        assertEquals(1, out.get(0).failed());
        assertEquals(10L, out.get(0).totals().likes());
        assertEquals(0L, out.get(0).totals().impressions());
    }

    @Test
    void unknown_label_yields_an_empty_detail() {
        when(batchRepo.findAll()).thenReturn(List.of());
        var detail = new CampaignReportService(batchRepo, publicationRepo, metricsRepo, productRepo)
                .getCampaign("nope");
        assertEquals("nope", detail.label());
        assertEquals(0, detail.posts().size());
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=CampaignReportServiceTest`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Create the DTOs**

`CampaignSummaryDto.java`:

```java
package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.time.Instant;
import java.util.Set;

public record CampaignSummaryDto(
        String label,
        Instant firstPostAt,
        Instant lastPostAt,
        int batchCount,
        int totalPosts,
        int published,
        int failed,
        int scheduled,
        Set<PublicationPlatform> platforms,
        MetricsTotals totals,
        int postsWithError
) {
    public record MetricsTotals(long impressions, long reach, long likes, long comments,
                                long shares, long saved) {}
}
```

`CampaignDetailDto.java`:

```java
package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.domain.model.PostMetrics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CampaignDetailDto(
        String label,
        Instant firstPostAt,
        Instant lastPostAt,
        List<PostRow> posts
) {
    public record PostRow(
            UUID publicationId,
            UUID productId,
            String productName,
            String thumbnailUrl,
            PublicationPlatform platform,
            PublicationStatus status,
            String externalPermalink,
            PostMetrics metrics,
            String fetchError,
            Instant fetchedAt
    ) {}
}
```

- [ ] **Step 4: Create the service**

`CampaignReportService.java`:

```java
package com.pilarestilo.publication.application;

import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.dto.CampaignDetailDto;
import com.pilarestilo.publication.application.dto.CampaignSummaryDto;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.publication.domain.model.PostMetrics;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationBatchEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationEntity;
import com.pilarestilo.publication.infrastructure.persistence.entities.PublicationMetricsEntity;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationBatchJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationJpaRepository;
import com.pilarestilo.publication.infrastructure.persistence.repositories.PublicationMetricsJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CampaignReportService {

    private final PublicationBatchJpaRepository batchRepository;
    private final PublicationJpaRepository publicationRepository;
    private final PublicationMetricsJpaRepository metricsRepository;
    private final ProductRepository productRepository;

    public CampaignReportService(PublicationBatchJpaRepository batchRepository,
                                 PublicationJpaRepository publicationRepository,
                                 PublicationMetricsJpaRepository metricsRepository,
                                 ProductRepository productRepository) {
        this.batchRepository = batchRepository;
        this.publicationRepository = publicationRepository;
        this.metricsRepository = metricsRepository;
        this.productRepository = productRepository;
    }

    public List<CampaignSummaryDto> listCampaigns() {
        Map<String, List<PublicationEntity>> byLabel = groupPublicationsByLabel();
        Map<UUID, PublicationMetricsEntity> metrics = loadMetrics(byLabel);
        Map<String, Integer> batchCounts = batchCountsByLabel();

        List<CampaignSummaryDto> out = new ArrayList<>();
        for (Map.Entry<String, List<PublicationEntity>> e : byLabel.entrySet()) {
            List<PublicationEntity> rows = e.getValue();
            EnumSet<PublicationPlatform> platforms = EnumSet.noneOf(PublicationPlatform.class);
            int published = 0, failed = 0, scheduled = 0, postsWithError = 0;
            long tImp = 0, tReach = 0, tLikes = 0, tComments = 0, tShares = 0, tSaved = 0;
            Instant first = null, last = null;
            for (PublicationEntity r : rows) {
                platforms.add(r.getPlatform());
                switch (r.getStatus()) {
                    case PUBLISHED -> published++;
                    case FAILED -> failed++;
                    case SCHEDULED -> scheduled++;
                    default -> { /* counted only in totalPosts */ }
                }
                if (first == null || r.getCreatedAt().isBefore(first)) first = r.getCreatedAt();
                if (last == null || r.getCreatedAt().isAfter(last)) last = r.getCreatedAt();
                PublicationMetricsEntity m = metrics.get(r.getId());
                if (m != null) {
                    if (m.getFetchError() != null) postsWithError++;
                    tImp += nz(m.getImpressions()); tReach += nz(m.getReach()); tLikes += nz(m.getLikes());
                    tComments += nz(m.getComments()); tShares += nz(m.getShares()); tSaved += nz(m.getSaved());
                }
            }
            out.add(new CampaignSummaryDto(
                    e.getKey(), first, last, batchCounts.getOrDefault(e.getKey(), 0), rows.size(),
                    published, failed, scheduled, platforms,
                    new CampaignSummaryDto.MetricsTotals(tImp, tReach, tLikes, tComments, tShares, tSaved),
                    postsWithError));
        }
        out.sort(Comparator.comparing(CampaignSummaryDto::lastPostAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    public CampaignDetailDto getCampaign(String label) {
        List<PublicationEntity> rows = groupPublicationsByLabel().getOrDefault(label, List.of());
        if (rows.isEmpty()) {
            return new CampaignDetailDto(label, null, null, List.of());
        }
        Map<UUID, PublicationMetricsEntity> metrics = metricsRepository
                .findByPublicationIdIn(rows.stream().map(PublicationEntity::getId).toList()).stream()
                .collect(Collectors.toMap(PublicationMetricsEntity::getPublicationId, m -> m));
        Map<UUID, Product> products = productRepository.findAllByIds(rows.stream()
                        .map(PublicationEntity::getProductId).filter(java.util.Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<CampaignDetailDto.PostRow> postRows = new ArrayList<>();
        Instant first = null, last = null;
        for (PublicationEntity r : rows) {
            if (first == null || r.getCreatedAt().isBefore(first)) first = r.getCreatedAt();
            if (last == null || r.getCreatedAt().isAfter(last)) last = r.getCreatedAt();
            Product p = r.getProductId() == null ? null : products.get(r.getProductId());
            PublicationMetricsEntity m = metrics.get(r.getId());
            postRows.add(new CampaignDetailDto.PostRow(
                    r.getId(), r.getProductId(),
                    p != null ? p.getName() : "(producto eliminado)",
                    p != null ? p.getImageUrl() : null,
                    r.getPlatform(), r.getStatus(), r.getExternalPermalink(),
                    m == null ? null : toPostMetrics(m),
                    m == null ? null : m.getFetchError(),
                    m == null ? null : m.getFetchedAt()));
        }
        return new CampaignDetailDto(label, first, last, postRows);
    }

    private Map<String, List<PublicationEntity>> groupPublicationsByLabel() {
        List<PublicationBatchEntity> batches = batchRepository.findAll().stream()
                .filter(b -> b.getCampaignLabel() != null && !b.getCampaignLabel().isBlank())
                .toList();
        Map<UUID, String> labelByBatch = batches.stream()
                .collect(Collectors.toMap(PublicationBatchEntity::getId, PublicationBatchEntity::getCampaignLabel));
        List<PublicationEntity> pubs = labelByBatch.isEmpty() ? List.of()
                : publicationRepository.findByBatchIdInOrderByCreatedAtAsc(labelByBatch.keySet());
        Map<String, List<PublicationEntity>> byLabel = new LinkedHashMap<>();
        for (PublicationEntity p : pubs) {
            String label = labelByBatch.get(p.getBatchId());
            if (label != null) {
                byLabel.computeIfAbsent(label, k -> new ArrayList<>()).add(p);
            }
        }
        return byLabel;
    }

    private Map<String, Integer> batchCountsByLabel() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PublicationBatchEntity b : batchRepository.findAll()) {
            if (b.getCampaignLabel() != null && !b.getCampaignLabel().isBlank()) {
                counts.merge(b.getCampaignLabel(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private Map<UUID, PublicationMetricsEntity> loadMetrics(Map<String, List<PublicationEntity>> byLabel) {
        Set<UUID> ids = byLabel.values().stream().flatMap(List::stream)
                .map(PublicationEntity::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return metricsRepository.findByPublicationIdIn(ids).stream()
                .collect(Collectors.toMap(PublicationMetricsEntity::getPublicationId, m -> m));
    }

    private static long nz(Long v) { return v == null ? 0L : v; }

    private static PostMetrics toPostMetrics(PublicationMetricsEntity m) {
        return new PostMetrics(m.getImpressions(), m.getReach(), m.getLikes(),
                m.getComments(), m.getShares(), m.getSaved());
    }
}
```

- [ ] **Step 5: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=CampaignReportServiceTest`
Expected: PASS. (`PublicationBatchJpaRepository` extends `JpaRepository`, so `findAll()` exists.)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/dto/CampaignSummaryDto.java \
        backend/src/main/java/com/pilarestilo/publication/application/dto/CampaignDetailDto.java \
        backend/src/main/java/com/pilarestilo/publication/application/CampaignReportService.java \
        backend/src/test/java/com/pilarestilo/publication/application/CampaignReportServiceTest.java
git commit -m "feat(publication): CampaignReportService — campaign rollup with metric totals"
```

---

## Task 10: `PublicationController` campaign endpoints

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/controllers/PublicationController.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationControllerIT.java`

**Interfaces:**
- Consumes: `CampaignReportService.listCampaigns()` / `getCampaign(String)` (Task 9); `RefreshMetricsUseCase.execute(new MetricsRefreshScope.Campaign(String))` (Task 7).
- Produces: `GET /api/admin/publications/campaigns`, `GET /api/admin/publications/campaigns/detail?label=`, `POST /api/admin/publications/campaigns/refresh-metrics?label=`.

- [ ] **Step 1: Write the failing test**

Add to `PublicationControllerIT.java`:

```java
    @Test
    void campaigns_list_groups_a_published_batch_by_its_label() throws Exception {
        String adminToken = loginAdmin();
        Product product = productRepository.save(Product.create("Falda campaña", "d",
                new Money(BigDecimal.valueOf(29990), "CLP"), "https://cdn.example.com/f.jpg",
                ProductCondition.NEW, "Pilar", 3));

        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(product.getId().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}",
                                "campaignLabel", "Campaña de prueba"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/publications/campaigns").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.label == 'Campaña de prueba')]").exists());

        mvc.perform(get("/api/admin/publications/campaigns/detail")
                        .param("label", "Campaña de prueba")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Campaña de prueba"))
                .andExpect(jsonPath("$.posts", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void refresh_metrics_requires_update_permission() throws Exception {
        String sellerToken = loginAs("seller-metrics@pilarestilo.com", "SELLER");   // match the IT's helper for a non-admin

        mvc.perform(post("/api/admin/publications/campaigns/refresh-metrics")
                        .param("label", "x")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isForbidden());
    }
```

Match `loginAs(...)` to whatever helper the IT already uses for a non-admin (the batch tests have
one — reuse it verbatim; if there is none, use `@WithMockUser(roles = "SELLER")` on a MockMvc-only
variant like the other permission tests in the file).

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd backend && mvn test -Dtest=PublicationControllerIT`
Expected: FAIL — the endpoints do not exist (404).

- [ ] **Step 3: Wire the dependencies**

In `PublicationController`, add two constructor params and fields:

```java
    private final CampaignReportService campaignReportService;
    private final RefreshMetricsUseCase refreshMetricsUseCase;
```

Add them to the constructor parameter list and assignments (keep the existing five).

- [ ] **Step 4: Add the endpoints**

After the `/batches/...` mappings:

```java
    @GetMapping("/campaigns")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_READ)")
    public List<CampaignSummaryDto> campaigns() {
        return campaignReportService.listCampaigns();
    }

    @GetMapping("/campaigns/detail")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_READ)")
    public CampaignDetailDto campaignDetail(@RequestParam String label) {
        return campaignReportService.getCampaign(label);
    }

    @PostMapping("/campaigns/refresh-metrics")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public RefreshMetricsUseCase.MetricsRefreshResult refreshCampaignMetrics(@RequestParam String label) {
        return refreshMetricsUseCase.execute(new MetricsRefreshScope.Campaign(label));
    }
```

Add imports: `CampaignSummaryDto`, `CampaignDetailDto`, `CampaignReportService`,
`RefreshMetricsUseCase`, `MetricsRefreshScope`.

- [ ] **Step 5: Run the test, verify it passes**

Run: `cd backend && mvn test -Dtest=PublicationControllerIT`
Expected: PASS.

- [ ] **Step 6: Run the whole publication package + backend regression**

Run: `cd backend && mvn test -Dtest='com.pilarestilo.publication.**'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/infrastructure/web/controllers/PublicationController.java \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationControllerIT.java
git commit -m "feat(publication): GET /campaigns, /campaigns/detail, POST /campaigns/refresh-metrics"
```

---

## Task 11: Frontend `api.ts` — campaign types + functions

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Test: `frontend/src/lib/__tests__/api.campaigns.test.ts` (create)

**Interfaces:**
- Produces: `CampaignSummary`, `CampaignDetail`, `CampaignPostRow`, `PostMetricsDto`, `MetricsTotals` interfaces; `getCampaigns()`, `getCampaignDetail(label)`, `refreshCampaignMetrics(label)`.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/lib/__tests__/api.campaigns.test.ts`:

```ts
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { getCampaignDetail, refreshCampaignMetrics } from '../api';

const fetchMock = vi.fn();

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockResolvedValue({ ok: true, status: 200, json: async () => ({}) });
});
afterEach(() => vi.unstubAllGlobals());

describe('campaign api', () => {
  it('URL-encodes the label in getCampaignDetail', async () => {
    await getCampaignDetail('Liquidación primavera', 'tok');
    const url = fetchMock.mock.calls[0][0] as string;
    expect(url).toContain('/campaigns/detail?label=Liquidaci%C3%B3n%20primavera');
  });

  it('URL-encodes the label in refreshCampaignMetrics and POSTs', async () => {
    await refreshCampaignMetrics('Verano & Sol', 'tok');
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain('label=Verano%20%26%20Sol');
    expect((init as RequestInit).method).toBe('POST');
  });
});
```

(If `api.ts` uses a shared `apiFetch` wrapper rather than raw `fetch`, mock that instead — check
how `publishProductsBatch` is tested in the repo and follow the same seam.)

- [ ] **Step 2: Run the test, verify it fails**

Run: `cd frontend && npx vitest run src/lib/__tests__/api.campaigns.test.ts`
Expected: FAIL — functions are not exported.

- [ ] **Step 3: Implement**

In `api.ts`, near the other publication types:

```ts
export interface PostMetricsDto {
  impressions: number | null;
  reach: number | null;
  likes: number | null;
  comments: number | null;
  shares: number | null;
  saved: number | null;
}

export interface MetricsTotals {
  impressions: number;
  reach: number;
  likes: number;
  comments: number;
  shares: number;
  saved: number;
}

export interface CampaignSummary {
  label: string;
  firstPostAt: string;
  lastPostAt: string;
  batchCount: number;
  totalPosts: number;
  published: number;
  failed: number;
  scheduled: number;
  platforms: Array<'INSTAGRAM' | 'FACEBOOK'>;
  totals: MetricsTotals;
  postsWithError: number;
}

export interface CampaignPostRow {
  publicationId: string;
  productId: string | null;
  productName: string;
  thumbnailUrl: string | null;
  platform: 'INSTAGRAM' | 'FACEBOOK';
  status: string;
  externalPermalink: string | null;
  metrics: PostMetricsDto | null;
  fetchError: string | null;
  fetchedAt: string | null;
}

export interface CampaignDetail {
  label: string;
  firstPostAt: string | null;
  lastPostAt: string | null;
  posts: CampaignPostRow[];
}

export async function getCampaigns(token?: string): Promise<CampaignSummary[]> {
  return apiFetch<CampaignSummary[]>('/admin/publications/campaigns', { headers: authHeaders(token) });
}

export async function getCampaignDetail(label: string, token?: string): Promise<CampaignDetail> {
  return apiFetch<CampaignDetail>(
    `/admin/publications/campaigns/detail?label=${encodeURIComponent(label)}`,
    { headers: authHeaders(token) },
  );
}

export async function refreshCampaignMetrics(
  label: string,
  token?: string,
): Promise<{ refreshed: number; failed: number }> {
  return apiFetch<{ refreshed: number; failed: number }>(
    `/admin/publications/campaigns/refresh-metrics?label=${encodeURIComponent(label)}`,
    { method: 'POST', headers: authHeaders(token) },
  );
}
```

Match `apiFetch` / `authHeaders` signatures to the file's existing helpers (the same ones
`publishProductsBatch` and `getPublicationBatches` use).

- [ ] **Step 4: Run the test + typecheck**

Run: `cd frontend && npx vitest run src/lib/__tests__/api.campaigns.test.ts && ./node_modules/.bin/tsc --noEmit`
Expected: PASS, tsc clean.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/__tests__/api.campaigns.test.ts
git commit -m "feat(publication): campaign report + refresh-metrics api client"
```

---

## Task 12: `PublicacionesPage` third tab + `CampanasTab`

**Files:**
- Modify: `frontend/src/islands/admin/PublicacionesPage.tsx` (`Tab` type ~line 5, `parseTab` ~line 7, the tab list ~line 51, the render switch ~line 68)
- Create: `frontend/src/islands/admin/CampanasTab.tsx`
- Test: `frontend/src/islands/admin/__tests__/CampanasTab.test.tsx` (create)
- Test: `frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx`

**Interfaces:**
- Consumes: `getCampaigns`, `getCampaignDetail`, `refreshCampaignMetrics` (Task 11).

**Design gate:** invoke `ui-ux-pro-max` and `impeccable` before writing `CampanasTab.tsx` markup.
Follow the `HistorialTab` visual language (expandable rows, `StatusPill`, `PLATFORM_SHORT`/`PLATFORM_NAME`).

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/islands/admin/__tests__/CampanasTab.test.tsx`:

```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import CampanasTab from '../CampanasTab';
import { getCampaigns, getCampaignDetail, refreshCampaignMetrics } from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  getCampaigns: vi.fn(),
  getCampaignDetail: vi.fn(),
  refreshCampaignMetrics: vi.fn(),
}));
vi.mock('../../../lib/authStore', () => ({
  useAuthStore: () => ({ token: 't' }),
  readAuthTokenCookie: () => 't',
}));

const summary = {
  label: 'Verano', firstPostAt: '2026-09-01T00:00:00Z', lastPostAt: '2026-09-03T00:00:00Z',
  batchCount: 2, totalPosts: 4, published: 3, failed: 1, scheduled: 0,
  platforms: ['INSTAGRAM', 'FACEBOOK'] as const,
  totals: { impressions: 1234, reach: 900, likes: 87, comments: 5, shares: 2, saved: 10 },
  postsWithError: 1,
};

const detail = {
  label: 'Verano', firstPostAt: '2026-09-01T00:00:00Z', lastPostAt: '2026-09-03T00:00:00Z',
  posts: [
    {
      publicationId: 'p1', productId: 'x', productName: 'Vestido', thumbnailUrl: null,
      platform: 'INSTAGRAM' as const, status: 'PUBLISHED', externalPermalink: 'https://instagram.com/p/A/',
      metrics: { impressions: 500, reach: 400, likes: 40, comments: 2, shares: 1, saved: 6 },
      fetchError: null, fetchedAt: '2026-09-04T06:00:00Z',
    },
    {
      publicationId: 'p2', productId: 'x', productName: 'Vestido', thumbnailUrl: null,
      platform: 'FACEBOOK' as const, status: 'PUBLISHED', externalPermalink: null,
      metrics: null, fetchError: '403 forbidden', fetchedAt: '2026-09-04T06:00:00Z',
    },
  ],
};

beforeEach(() => {
  vi.mocked(getCampaigns).mockResolvedValue([summary] as never);
  vi.mocked(getCampaignDetail).mockResolvedValue(detail as never);
  vi.mocked(refreshCampaignMetrics).mockResolvedValue({ refreshed: 1, failed: 1 });
});

describe('CampanasTab', () => {
  it('lists campaigns with headline metrics', async () => {
    render(<CampanasTab />);
    expect(await screen.findByText('Verano')).toBeInTheDocument();
    expect(screen.getByText(/1[.,]2\s?mil|1234|1,2 K/i)).toBeInTheDocument(); // compact impressions
  });

  it('expands to per-post rows on click', async () => {
    const user = userEvent.setup();
    render(<CampanasTab />);
    await user.click(await screen.findByRole('button', { name: /verano/i }));
    expect(await screen.findByText('No disponible')).toBeInTheDocument(); // p2 has fetchError
    expect(screen.getByRole('link', { name: /ver en instagram/i })).toBeInTheDocument();
  });

  it('refreshes metrics and re-fetches', async () => {
    const user = userEvent.setup();
    render(<CampanasTab />);
    await screen.findByText('Verano');
    await user.click(screen.getByRole('button', { name: /actualizar métricas/i }));
    expect(refreshCampaignMetrics).toHaveBeenCalledWith('Verano', 't');
    // getCampaigns called twice: initial + after refresh
    expect(vi.mocked(getCampaigns).mock.calls.length).toBeGreaterThanOrEqual(2);
  });

  it('shows an empty state when there are no campaigns', async () => {
    vi.mocked(getCampaigns).mockResolvedValue([]);
    render(<CampanasTab />);
    expect(await screen.findByText(/aún no hay campañas/i)).toBeInTheDocument();
  });
});
```

Add to `PublicacionesPage.test.tsx`:

```tsx
  it('opens the Campañas tab and writes ?tab=campanas', async () => {
    const user = userEvent.setup();
    render(<PublicacionesPage />);
    await user.click(screen.getByRole('tab', { name: /campañas/i }));
    expect(new URL(window.location.href).searchParams.get('tab')).toBe('campanas');
  });
```

- [ ] **Step 2: Run the tests, verify they fail**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/CampanasTab.test.tsx src/islands/admin/__tests__/PublicacionesPage.test.tsx`
Expected: FAIL — `CampanasTab` module missing; no Campañas tab.

- [ ] **Step 3: `PublicacionesPage` — add the tab**

- `type Tab = 'publicar' | 'historial' | 'campanas';`
- `parseTab`: `const v = raw?.toLowerCase(); return v === 'historial' ? 'historial' : v === 'campanas' ? 'campanas' : 'publicar';`
- tab list: `(['publicar', 'historial', 'campanas'] as const)`, label map
  `id === 'publicar' ? 'Publicar' : id === 'historial' ? 'Historial' : 'Campañas'`.
- render: `{tab === 'campanas' && <CampanasTab />}`.
- `import CampanasTab from './CampanasTab';`

- [ ] **Step 4: Create `CampanasTab.tsx`** (after the `ui-ux-pro-max` / `impeccable` pass)

Structure (fill the visual detail from the design pass; this is the skeleton the tests pin):

```tsx
import { useEffect, useState } from 'react';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import {
  getCampaigns, getCampaignDetail, refreshCampaignMetrics,
  type CampaignSummary, type CampaignDetail,
} from '../../lib/api';

const NF = new Intl.NumberFormat('es-CL', { notation: 'compact', maximumFractionDigits: 1 });
const PLATFORM_NAME: Record<string, string> = { INSTAGRAM: 'Instagram', FACEBOOK: 'Facebook' };

export default function CampanasTab() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie() ?? '';
  const [campaigns, setCampaigns] = useState<CampaignSummary[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [detail, setDetail] = useState<CampaignDetail | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  function load() {
    void getCampaigns(effectiveToken).then(setCampaigns).catch(() => setCampaigns([]));
  }
  useEffect(load, [effectiveToken]);

  async function toggle(label: string) {
    if (open === label) { setOpen(null); return; }
    setOpen(label);
    setDetail(null);
    setDetail(await getCampaignDetail(label, effectiveToken));
  }

  async function refresh(label: string) {
    setBusy(label);
    try {
      const r = await refreshCampaignMetrics(label, effectiveToken);
      setNotice(`Métricas actualizadas (${r.refreshed}, ${r.failed} con error).`);
      load();
      if (open === label) setDetail(await getCampaignDetail(label, effectiveToken));
    } finally {
      setBusy(null);
    }
  }

  if (campaigns && campaigns.length === 0) {
    return <p className="text-sm text-pe-muted">Aún no hay campañas. Poné una etiqueta de campaña al publicar.</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      {notice && <p className="text-xs text-pe-muted" role="status">{notice}</p>}
      {(campaigns ?? []).map((c) => (
        <div key={c.label} className="border border-pe-border rounded-xs">
          <div className="flex items-center justify-between gap-3 p-3">
            <button type="button" onClick={() => toggle(c.label)} className="flex-1 text-left">
              <span className="font-sans text-sm">{c.label}</span>
              <span className="block text-xs text-pe-muted">
                {c.totalPosts} posts · {c.published} publicados · {c.failed} fallidos ·
                {' '}Impresiones {NF.format(c.totals.impressions)} · Reach {NF.format(c.totals.reach)} ·
                {' '}Likes {NF.format(c.totals.likes)} · Comentarios {NF.format(c.totals.comments)}
              </span>
            </button>
            <button
              type="button"
              onClick={() => refresh(c.label)}
              disabled={busy === c.label}
              className="text-[0.78rem] border border-pe-border px-2.5 py-1 rounded-xs hover:border-pe-rose disabled:opacity-50"
            >
              {busy === c.label ? 'Actualizando…' : 'Actualizar métricas'}
            </button>
          </div>
          {open === c.label && detail && (
            <ul className="border-t border-pe-border divide-y divide-pe-border">
              {detail.posts.map((p) => (
                <li key={p.publicationId} className="p-3 flex flex-wrap items-center gap-3 text-sm">
                  <span className="flex-1 min-w-0 truncate">{p.productName}</span>
                  <span className="text-[0.7rem] text-pe-muted">{PLATFORM_NAME[p.platform] ?? p.platform}</span>
                  <span className="text-[0.72rem]">
                    {p.metrics
                      ? `Impresiones ${NF.format(p.metrics.impressions ?? 0)} · Likes ${NF.format(p.metrics.likes ?? 0)} · Comentarios ${NF.format(p.metrics.comments ?? 0)}`
                      : p.fetchError
                        ? <span title={p.fetchError}>No disponible</span>
                        : 'Sin métricas aún'}
                  </span>
                  {p.externalPermalink && (
                    <a href={p.externalPermalink} target="_blank" rel="noreferrer"
                       className="text-[0.72rem] text-pe-rose hover:underline">
                      Ver en {PLATFORM_NAME[p.platform] ?? p.platform}
                    </a>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}
    </div>
  );
}
```

- [ ] **Step 5: Run the tests, verify they pass**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/CampanasTab.test.tsx src/islands/admin/__tests__/PublicacionesPage.test.tsx`
Expected: PASS. Adjust the compact-number assertion in the test to whatever `Intl.NumberFormat('es-CL', { notation: 'compact' })` actually renders in happy-dom (log it once and pin the exact string).

- [ ] **Step 6: Full frontend + backend regression**

Run: `cd frontend && npx vitest run && ./node_modules/.bin/tsc --noEmit`
Run: `cd backend && mvn test`
Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/islands/admin/PublicacionesPage.tsx \
        frontend/src/islands/admin/CampanasTab.tsx \
        frontend/src/islands/admin/__tests__/CampanasTab.test.tsx \
        frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx
git commit -m "feat(admin): Campañas tab — campaign rollup with engagement metrics"
```

---

## Self-Review

**Spec coverage:**

| Spec section | Task |
|---|---|
| §1 V103 migration | Task 1 |
| §2 `PublicationMetricsEntity` + repo | Task 1 |
| §3 `PostMetrics` value object | Task 2 |
| §4 `PublicationMetricsFetcher` port + `Result` | Task 2 |
| §5 `MetaMetricsFetcher` + IG/FB sub-fetchers (incl. FB insights-403 fallback, missing-creds → failed) | Tasks 3, 4, 5 |
| §6 `MetricsUpsertService` (per-call `@Transactional`, keep-old-on-failure) | Task 6 |
| §7 `RefreshMetricsUseCase` + `MetricsRefreshScope` + repo queries + injected `Clock` | Task 7 |
| §8 `RefreshRecentMetricsScheduler` + config (yml, metadata, .env.example, compose) | Task 8 |
| §9 `CampaignReportService` + DTOs (`MetricsTotals` null-as-zero, unknown label → empty) | Task 9 |
| §10 three controller endpoints (label as query param, `PUBLICATIONS_READ`/`UPDATE`) | Task 10 |
| §11 `api.ts` types + functions (encodeURIComponent) | Task 11 |
| §12 `PublicacionesPage` third tab + `CampanasTab` (empty state, "Sin métricas aún" vs "No disponible", design gate) | Task 12 |
| §Error handling — degrade to `fetch_error`, never throw out of a run/read | Tasks 4, 6, 7, 9 |
| §Testing | every task |

No gaps.

**Placeholder scan:** Task 3's IG `INSIGHT_METRICS` string is a concrete choice with an inline
instruction for what to do if Graph rejects a name (drop it — the mapper already tolerates
absent metrics); this is an API-version reality, not a plan gap. Task 10 Step 1 and Task 11 Step 1
defer to the IT's / api.ts's existing test seam ("match the helper the file uses") rather than
reproducing ~40 lines of harness — the concrete assertions are literal. Task 12 Step 4's
`CampanasTab.tsx` is a working skeleton the tests pin, with the visual polish gated behind the
`ui-ux-pro-max`/`impeccable` pass named in Step 4 — deliberate, since the design pass genuinely
changes the markup. No `TBD` / "add error handling" / bare "write tests".

**Type consistency:**
- `PublicationMetricsFetcher.Result` (`Optional<PostMetrics> metrics`, `String error`, `ok`/`failed`) — Task 2; used identically in Tasks 3–7, 9.
- `PostMetrics(Long impressions, reach, likes, comments, shares, saved)` — Task 2; constructed in Tasks 3, 4, 9; consumed in Task 12 as `PostMetricsDto` (all `number | null`).
- `MetricsRefreshScope.Campaign(String label)` / `RecentDays(int days)` — Task 7; used in Tasks 8, 10.
- `RefreshMetricsUseCase.MetricsRefreshResult(int refreshed, int failed)` — Task 7; used in Tasks 8, 10; frontend `{ refreshed, failed }` — Task 11.
- `PublicationJpaRepository.findPublishedWithPostIdByCampaignLabel(String)` / `findPublishedWithPostIdSince(Instant)` — Task 7; called in Tasks 7 (use case).
- `PublicationMetricsJpaRepository.findByPublicationIdIn(Collection<UUID>)` — Task 1; used in Tasks 6 (via `findById`), 9.
- `CampaignSummaryDto` / `CampaignSummaryDto.MetricsTotals` / `CampaignDetailDto` / `CampaignDetailDto.PostRow` — Task 9; serialized by Task 10; mirrored in `api.ts` (Task 11) as `CampaignSummary` / `MetricsTotals` / `CampaignDetail` / `CampaignPostRow`; consumed in Task 12.
- `MetricsUpsertService.upsert(UUID, PublicationMetricsFetcher.Result, Instant)` — Task 6; called in Task 7.

Consistent throughout.
