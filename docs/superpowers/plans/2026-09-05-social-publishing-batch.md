# Social Publishing Batch (Increment H, Etapa 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin pick 1+ products, write one caption template, and publish them to Instagram and/or Facebook in one batch action — replacing the dead n8n dispatch path with direct Meta Graph API calls.

**Architecture:** Widen the existing `publication` module's dispatch port to carry a real synchronous result (fixing a latent transaction-rollback bug in the process), add two concrete Meta adapters selected by an exhaustive `switch` (no n8n-style webhook, no lookup map), add a non-transactional batch orchestrator that reuses the existing `create()`/`dispatch()` methods per product×platform, and add one admin screen backed by one new endpoint.

**Tech Stack:** Spring Boot 4 (Java 25, hexagonal monolith), `RestClient` for outbound HTTP, Astro 5 + React admin islands, Vitest/RTL, JUnit 5 + Mockito + Testcontainers Postgres.

**Spec:** `docs/superpowers/specs/2026-09-05-social-publishing-batch-design.md`

## Global Constraints

- Java 25 idioms already used in this module: records for DTOs/commands, exhaustive `switch` expressions for enum dispatch, unnamed catch variables (`catch (Exception _)`), plain mutable classes (no records) for JPA entities.
- Jackson 3: import `tools.jackson.databind.ObjectMapper` / `tools.jackson.core.JacksonException`, never `com.fasterxml.jackson.*`.
- `@Value` constructor injection for config, never `@ConfigurationProperties` (reserved for datasources/Kafka in this codebase).
- Secrets resolve from `system_settings` (encrypted via `SystemSettingsCryptoService`) first, env var fallback second — same pattern as `SocialPublishingN8nConfigResolver` and `MercadoPagoPaymentGatewayAdapter`.
- Env var names are the derived form of the YAML path: `app.social-publishing.meta.instagram.access-token` → `APP_SOCIAL_PUBLISHING_META_INSTAGRAM_ACCESS_TOKEN`.
- Every new `app.*` key needs an entry in `additional-spring-configuration-metadata.json` and a line in `infra/.env.example` — skipping this is exactly how the n8n path went unnoticed as dead for months.
- No dead code: deleting the n8n dispatch mechanism means deleting all of it (dispatcher, config resolver, webhook controller, DTOs, security rule, YAML block, metadata entries, tests) in the same task that replaces it — not leaving it "just in case."
- Next Flyway migration is **V99**.
- RBAC: reuse existing `PermissionRegistry.PUBLICATIONS_UPDATE` / `PUBLICATIONS_READ` — no new permission, no RBAC migration.
- Test conventions already established in this module: unit tests use `@ExtendWith(MockitoExtension.class)` and mock collaborators directly (no port needed if there isn't one — mocking a concrete `@Service` class is fine here); IT tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc` + Testcontainers `PostgreSQLContainer("postgres:16")`.

---

## Task 1: Meta credential fields on `system_settings`

**Files:**
- Create: `backend/src/main/resources/db/migration/V99__social_publishing_meta_credentials.sql`
- Modify: `backend/src/main/java/com/pilarestilo/systemsettings/domain/model/SystemSettings.java`
- Modify: `backend/src/main/java/com/pilarestilo/systemsettings/infrastructure/persistence/entities/SystemSettingsEntity.java`
- Modify: `backend/src/main/java/com/pilarestilo/systemsettings/application/dto/SystemSettingsDto.java`
- Modify: `backend/src/main/java/com/pilarestilo/systemsettings/infrastructure/web/requests/UpdateSystemSettingsRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/systemsettings/application/commands/UpdateSystemSettingsCommand.java`
- Modify: `backend/src/main/java/com/pilarestilo/systemsettings/application/usecases/UpdateSystemSettingsUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/systemsettings/infrastructure/web/controllers/SystemSettingsController.java`
- Modify: `backend/src/main/java/com/pilarestilo/systemsettings/application/mappers/SystemSettingsMapper.java`
- Modify: `backend/src/main/java/com/pilarestilo/systemsettings/infrastructure/persistence/repositories/SystemSettingsRepositoryAdapter.java`
- Test: `backend/src/test/java/com/pilarestilo/systemsettings/application/usecases/UpdateSystemSettingsUseCaseTest.java`

**Interfaces:**
- Produces: `SystemSettings.getMetaInstagramUserId()`, `.getMetaInstagramAccessTokenEncrypted()`, `.getMetaFacebookPageId()`, `.getMetaFacebookPageAccessTokenEncrypted()` — Task 2's `MetaPublishingConfigResolver` calls these.

This mirrors the existing n8n fields exactly (same 9-file vertical: migration → entity → repository adapter → domain model → DTO → request → command → use-case → controller/mapper), just for 2 platforms instead of 1 webhook. There is exactly **one** call site for `UpdateSystemSettingsCommand`'s constructor outside the record itself (the controller) and exactly **one** in the test file — confirmed by grep (case-insensitive — `N8n`, not `n8n`, since these are camelCase getter/field names) before writing this task, so there is no risk of missing a call site.

- [ ] **Step 1: Write the migration**

```sql
-- V99__social_publishing_meta_credentials.sql
-- Instagram/Facebook posting credentials for Increment H. Ids are not secret (plain columns,
-- mirrors n8n_webhook_url); access tokens are (encrypted columns, mirrors n8n_api_key_encrypted).
ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS meta_instagram_user_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS meta_instagram_access_token_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS meta_facebook_page_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS meta_facebook_page_access_token_encrypted TEXT;
```

- [ ] **Step 2: Run the existing migration-chain test to confirm V99 applies cleanly**

Run: `cd backend && mvn test -Dtest=ShippingZoneSeedRepairIT` (this IT boots the full V1→latest migration chain against a real Testcontainers Postgres — same check used for V98)
Expected: PASS

- [ ] **Step 3: Add the 4 fields to the domain model**

In `SystemSettings.java`, add to the field declarations, immediately after `private String n8nTokenHeaderName;`:

```java
    private String metaInstagramUserId;
    private String metaInstagramAccessTokenEncrypted;
    private String metaFacebookPageId;
    private String metaFacebookPageAccessTokenEncrypted;
```

In the `restore(...)` static factory's parameter list, immediately after the `String n8nApiKeyEncrypted,` parameter, add:

```java
            String metaInstagramUserId,
            String metaInstagramAccessTokenEncrypted,
            String metaFacebookPageId,
            String metaFacebookPageAccessTokenEncrypted,
```

In that same `restore(...)` factory's body, immediately after `settings.n8nApiKeyEncrypted = normalizeNullable(n8nApiKeyEncrypted);`, add:

```java
        settings.metaInstagramUserId = normalizeNullable(metaInstagramUserId);
        settings.metaInstagramAccessTokenEncrypted = normalizeNullable(metaInstagramAccessTokenEncrypted);
        settings.metaFacebookPageId = normalizeNullable(metaFacebookPageId);
        settings.metaFacebookPageAccessTokenEncrypted = normalizeNullable(metaFacebookPageAccessTokenEncrypted);
```

In the `update(...)` instance method's parameter list, immediately after `String n8nApiKeyEncrypted,`, add the same 4 parameters as above. In that method's body, immediately after `this.n8nApiKeyEncrypted = normalizeNullable(n8nApiKeyEncrypted);`, add the same 4 assignment lines as above but with `this.` instead of `settings.`.

In the getters block, immediately after `public String getN8nTokenHeaderName() { return n8nTokenHeaderName; }`, add:

```java
    public String getMetaInstagramUserId() { return metaInstagramUserId; }
    public String getMetaInstagramAccessTokenEncrypted() { return metaInstagramAccessTokenEncrypted; }
    public String getMetaFacebookPageId() { return metaFacebookPageId; }
    public String getMetaFacebookPageAccessTokenEncrypted() { return metaFacebookPageAccessTokenEncrypted; }
```

- [ ] **Step 4: Add the 4 columns to the JPA entity**

In `SystemSettingsEntity.java`, immediately after the `n8n_token_header_name` column block:

```java
    @Column(name = "meta_instagram_user_id", length = 120)
    private String metaInstagramUserId;

    @Column(name = "meta_instagram_access_token_encrypted", columnDefinition = "TEXT")
    private String metaInstagramAccessTokenEncrypted;

    @Column(name = "meta_facebook_page_id", length = 120)
    private String metaFacebookPageId;

    @Column(name = "meta_facebook_page_access_token_encrypted", columnDefinition = "TEXT")
    private String metaFacebookPageAccessTokenEncrypted;
```

And immediately after the `getN8nTokenHeaderName`/`setN8nTokenHeaderName` pair:

```java
    public String getMetaInstagramUserId() { return metaInstagramUserId; }
    public void setMetaInstagramUserId(String metaInstagramUserId) { this.metaInstagramUserId = metaInstagramUserId; }
    public String getMetaInstagramAccessTokenEncrypted() { return metaInstagramAccessTokenEncrypted; }
    public void setMetaInstagramAccessTokenEncrypted(String v) { this.metaInstagramAccessTokenEncrypted = v; }
    public String getMetaFacebookPageId() { return metaFacebookPageId; }
    public void setMetaFacebookPageId(String metaFacebookPageId) { this.metaFacebookPageId = metaFacebookPageId; }
    public String getMetaFacebookPageAccessTokenEncrypted() { return metaFacebookPageAccessTokenEncrypted; }
    public void setMetaFacebookPageAccessTokenEncrypted(String v) { this.metaFacebookPageAccessTokenEncrypted = v; }
```

In `SystemSettingsRepositoryAdapter.java`, this domain→entity direction, immediately after `entity.setN8nApiKeyEncrypted(settings.getN8nApiKeyEncrypted());`:
```java
        entity.setMetaInstagramUserId(settings.getMetaInstagramUserId());
        entity.setMetaInstagramAccessTokenEncrypted(settings.getMetaInstagramAccessTokenEncrypted());
        entity.setMetaFacebookPageId(settings.getMetaFacebookPageId());
        entity.setMetaFacebookPageAccessTokenEncrypted(settings.getMetaFacebookPageAccessTokenEncrypted());
```
and the entity→domain direction (the arguments feeding `SystemSettings.restore(...)`), immediately after `entity.getN8nApiKeyEncrypted(),`:
```java
                entity.getMetaInstagramUserId(),
                entity.getMetaInstagramAccessTokenEncrypted(),
                entity.getMetaFacebookPageId(),
                entity.getMetaFacebookPageAccessTokenEncrypted(),
```

- [ ] **Step 5: Thread the 4 fields through DTO, request, command, use case, controller, mapper**

`SystemSettingsDto.java` — immediately after `boolean n8nApiKeyConfigured,`:
```java
        String metaInstagramUserId,
        boolean metaInstagramAccessTokenConfigured,
        String metaFacebookPageId,
        boolean metaFacebookPageAccessTokenConfigured,
```

`UpdateSystemSettingsRequest.java` — immediately after `Boolean clearN8nApiKey,`:
```java
        @Size(max = 120) String metaInstagramUserId,
        @Size(max = 500) String metaInstagramAccessToken,
        Boolean clearMetaInstagramAccessToken,
        @Size(max = 120) String metaFacebookPageId,
        @Size(max = 500) String metaFacebookPageAccessToken,
        Boolean clearMetaFacebookPageAccessToken,
```

`UpdateSystemSettingsCommand.java` — immediately after `Boolean clearN8nApiKey,`:
```java
        String metaInstagramUserId,
        String metaInstagramAccessToken,
        Boolean clearMetaInstagramAccessToken,
        String metaFacebookPageId,
        String metaFacebookPageAccessToken,
        Boolean clearMetaFacebookPageAccessToken,
```

`UpdateSystemSettingsUseCase.java` — immediately after the `nextN8nApiKey` block:
```java
        String nextMetaInstagramAccessToken = resolveEncryptedSecret(
                settings.getMetaInstagramAccessTokenEncrypted(),
                command.metaInstagramAccessToken(),
                command.clearMetaInstagramAccessToken()
        );
        String nextMetaFacebookPageAccessToken = resolveEncryptedSecret(
                settings.getMetaFacebookPageAccessTokenEncrypted(),
                command.metaFacebookPageAccessToken(),
                command.clearMetaFacebookPageAccessToken()
        );
```
Then in the `settings.update(...)` call, immediately after the `nextN8nApiKey,` argument, add:
```java
                command.metaInstagramUserId(),
                nextMetaInstagramAccessToken,
                command.metaFacebookPageId(),
                nextMetaFacebookPageAccessToken,
```

`SystemSettingsController.java` — in the request→command mapping, immediately after `request.clearN8nApiKey(),`:
```java
                request.metaInstagramUserId(),
                request.metaInstagramAccessToken(),
                request.clearMetaInstagramAccessToken(),
                request.metaFacebookPageId(),
                request.metaFacebookPageAccessToken(),
                request.clearMetaFacebookPageAccessToken(),
```

`SystemSettingsMapper.java` — immediately after the `settings.getN8nApiKeyEncrypted() != null && !settings.getN8nApiKeyEncrypted().isBlank(),` line:
```java
                settings.getMetaInstagramUserId(),
                settings.getMetaInstagramAccessTokenEncrypted() != null && !settings.getMetaInstagramAccessTokenEncrypted().isBlank(),
                settings.getMetaFacebookPageId(),
                settings.getMetaFacebookPageAccessTokenEncrypted() != null && !settings.getMetaFacebookPageAccessTokenEncrypted().isBlank(),
```

- [ ] **Step 6: Update the one existing test call site**

In `UpdateSystemSettingsUseCaseTest.java`, find the line `null, null, null, null, // n8n fields` inside the single `new UpdateSystemSettingsCommand(...)` call, and immediately after it add:
```java
                null, null, null, null, null, null, // meta fields
```

- [ ] **Step 7: Compile and run the settings test suite**

Run: `cd backend && mvn test -Dtest=UpdateSystemSettingsUseCaseTest,SystemSettingsMapperTest`
Expected: PASS (if `SystemSettingsMapperTest` doesn't exist, just run the first one — confirm with `mvn test -Dtest=UpdateSystemSettingsUseCaseTest`)

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V99__social_publishing_meta_credentials.sql \
        backend/src/main/java/com/pilarestilo/systemsettings/ \
        backend/src/test/java/com/pilarestilo/systemsettings/
git commit -m "feat(systemsettings): add Meta Instagram/Facebook credential fields

V99 adds 4 nullable system_settings columns, mirroring the existing n8n
pattern: 2 plain ids, 2 encrypted access tokens with env-var fallback
resolved later by MetaPublishingConfigResolver."
```

---

## Task 2: `MetaPublishingConfigResolver` + config registration

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/MetaPublishingConfigResolver.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- Modify: `infra/.env.example`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/MetaPublishingConfigResolverTest.java`

**Interfaces:**
- Consumes: `SystemSettingsRepository.get()` (already used by `SocialPublishingN8nConfigResolver`), `SystemSettingsCryptoService.decrypt(String)`, `SystemSettings.getMetaInstagramUserId()` etc. from Task 1.
- Produces: `MetaPublishingConfigResolver.resolve()` returning `MetaPublishingConfigResolver.EffectiveConfig` with accessors `instagramUserId()`, `instagramAccessToken()`, `instagramBaseUrl()`, `facebookPageId()`, `facebookPageAccessToken()`, `facebookBaseUrl()`, `publicMediaBaseUrl()` — Task 3's adapters and dispatcher consume this.

- [ ] **Step 1: Write the failing test**

```java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaPublishingConfigResolverTest {

    @Mock
    SystemSettingsRepository systemSettingsRepository;

    @Mock
    SystemSettingsCryptoService cryptoService;

    @Mock
    SystemSettings settings;

    @BeforeEach
    void setUp() {
        when(systemSettingsRepository.get()).thenReturn(settings);
    }

    @Test
    void env_values_win_over_system_settings_when_both_present() {
        MetaPublishingConfigResolver resolver = new MetaPublishingConfigResolver(
                systemSettingsRepository, cryptoService,
                "env-ig-user", "env-ig-token", "https://graph.instagram.com/v23.0",
                "env-fb-page", "env-fb-token", "https://graph.facebook.com/v23.0",
                "https://pilarestilo.com"
        );

        var config = resolver.resolve();

        assertEquals("env-ig-user", config.instagramUserId());
        assertEquals("env-ig-token", config.instagramAccessToken());
        assertEquals("env-fb-page", config.facebookPageId());
        assertEquals("env-fb-token", config.facebookPageAccessToken());
        assertEquals("https://pilarestilo.com", config.publicMediaBaseUrl());
    }

    @Test
    void falls_back_to_decrypted_system_settings_when_env_is_blank() {
        when(settings.getMetaInstagramUserId()).thenReturn("db-ig-user");
        when(settings.getMetaInstagramAccessTokenEncrypted()).thenReturn("cipher-ig");
        when(cryptoService.decrypt("cipher-ig")).thenReturn("db-ig-token");

        MetaPublishingConfigResolver resolver = new MetaPublishingConfigResolver(
                systemSettingsRepository, cryptoService,
                "", "", "https://graph.instagram.com/v23.0",
                "", "", "https://graph.facebook.com/v23.0",
                ""
        );

        var config = resolver.resolve();

        assertEquals("db-ig-user", config.instagramUserId());
        assertEquals("db-ig-token", config.instagramAccessToken());
    }

    @Test
    void returns_null_for_unconfigured_credentials() {
        MetaPublishingConfigResolver resolver = new MetaPublishingConfigResolver(
                systemSettingsRepository, cryptoService,
                "", "", "https://graph.instagram.com/v23.0",
                "", "", "https://graph.facebook.com/v23.0",
                ""
        );

        var config = resolver.resolve();

        assertNull(config.instagramUserId());
        assertNull(config.instagramAccessToken());
        assertNull(config.publicMediaBaseUrl());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && mvn test -Dtest=MetaPublishingConfigResolverTest`
Expected: FAIL — `MetaPublishingConfigResolver` does not exist yet.

- [ ] **Step 3: Implement `MetaPublishingConfigResolver`**

```java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves Instagram/Facebook posting credentials the same way SocialPublishingN8nConfigResolver
 * resolved its webhook config: system_settings (encrypted) first, env var fallback second. Base
 * URLs are env-only — they are endpoint choices, not secrets, and don't need panel editing.
 */
@Component
public class MetaPublishingConfigResolver {

    private static final Logger log = LoggerFactory.getLogger(MetaPublishingConfigResolver.class);

    private final SystemSettingsRepository systemSettingsRepository;
    private final SystemSettingsCryptoService cryptoService;
    private final String envInstagramUserId;
    private final String envInstagramAccessToken;
    private final String instagramBaseUrl;
    private final String envFacebookPageId;
    private final String envFacebookPageAccessToken;
    private final String facebookBaseUrl;
    private final String publicMediaBaseUrl;

    public MetaPublishingConfigResolver(
            SystemSettingsRepository systemSettingsRepository,
            SystemSettingsCryptoService cryptoService,
            @Value("${app.social-publishing.meta.instagram.user-id:}") String envInstagramUserId,
            @Value("${app.social-publishing.meta.instagram.access-token:}") String envInstagramAccessToken,
            @Value("${app.social-publishing.meta.instagram.base-url:https://graph.instagram.com/v23.0}") String instagramBaseUrl,
            @Value("${app.social-publishing.meta.facebook.page-id:}") String envFacebookPageId,
            @Value("${app.social-publishing.meta.facebook.page-access-token:}") String envFacebookPageAccessToken,
            @Value("${app.social-publishing.meta.facebook.base-url:https://graph.facebook.com/v23.0}") String facebookBaseUrl,
            @Value("${app.social-publishing.meta.public-media-base-url:}") String publicMediaBaseUrl
    ) {
        this.systemSettingsRepository = systemSettingsRepository;
        this.cryptoService = cryptoService;
        this.envInstagramUserId = normalize(envInstagramUserId);
        this.envInstagramAccessToken = normalize(envInstagramAccessToken);
        this.instagramBaseUrl = instagramBaseUrl;
        this.envFacebookPageId = normalize(envFacebookPageId);
        this.envFacebookPageAccessToken = normalize(envFacebookPageAccessToken);
        this.facebookBaseUrl = facebookBaseUrl;
        this.publicMediaBaseUrl = normalize(publicMediaBaseUrl);
    }

    public EffectiveConfig resolve() {
        var settings = systemSettingsRepository.get();
        String instagramUserId = firstNonBlank(envInstagramUserId, settings.getMetaInstagramUserId());
        String instagramAccessToken = firstNonBlank(envInstagramAccessToken,
                decryptSecret(settings.getMetaInstagramAccessTokenEncrypted(), "meta instagram access token"));
        String facebookPageId = firstNonBlank(envFacebookPageId, settings.getMetaFacebookPageId());
        String facebookPageAccessToken = firstNonBlank(envFacebookPageAccessToken,
                decryptSecret(settings.getMetaFacebookPageAccessTokenEncrypted(), "meta facebook page access token"));

        return new EffectiveConfig(
                instagramUserId, instagramAccessToken, instagramBaseUrl,
                facebookPageId, facebookPageAccessToken, facebookBaseUrl,
                publicMediaBaseUrl.isBlank() ? null : publicMediaBaseUrl
        );
    }

    private String decryptSecret(String encryptedValue, String label) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        try {
            String decrypted = cryptoService.decrypt(encryptedValue);
            return (decrypted == null || decrypted.isBlank()) ? null : decrypted.trim();
        } catch (DomainException ex) {
            log.warn("[SOCIAL:META] could not decrypt {}: {}", label, ex.getMessage());
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record EffectiveConfig(
            String instagramUserId,
            String instagramAccessToken,
            String instagramBaseUrl,
            String facebookPageId,
            String facebookPageAccessToken,
            String facebookBaseUrl,
            String publicMediaBaseUrl
    ) {}
}
```

- [ ] **Step 4: Run the test again to verify it passes**

Run: `cd backend && mvn test -Dtest=MetaPublishingConfigResolverTest`
Expected: PASS

- [ ] **Step 5: Register the config keys**

In `application.yml`, immediately after the existing `social-publishing: n8n: ...` block (keeping the same 2-space-per-level indentation, as a sibling of `n8n:` under `social-publishing:`):

```yaml
    meta:
      instagram:
        user-id: ${APP_SOCIAL_PUBLISHING_META_INSTAGRAM_USER_ID:}
        access-token: ${APP_SOCIAL_PUBLISHING_META_INSTAGRAM_ACCESS_TOKEN:}
        base-url: ${APP_SOCIAL_PUBLISHING_META_INSTAGRAM_BASE_URL:https://graph.instagram.com/v23.0}
      facebook:
        page-id: ${APP_SOCIAL_PUBLISHING_META_FACEBOOK_PAGE_ID:}
        page-access-token: ${APP_SOCIAL_PUBLISHING_META_FACEBOOK_PAGE_ACCESS_TOKEN:}
        base-url: ${APP_SOCIAL_PUBLISHING_META_FACEBOOK_BASE_URL:https://graph.facebook.com/v23.0}
      public-media-base-url: ${APP_SOCIAL_PUBLISHING_META_PUBLIC_MEDIA_BASE_URL:}
```

In `additional-spring-configuration-metadata.json`, add a `groups` entry alongside the `app.social-publishing.n8n` one:
```json
    {
      "name": "app.social-publishing.meta"
    },
```
and `properties` entries (alphabetically ordered among the existing `app.social-publishing.n8n.*` block):
```json
    {
      "name": "app.social-publishing.meta.facebook.base-url",
      "type": "java.lang.String",
      "defaultValue": "https://graph.facebook.com/v23.0"
    },
    {
      "name": "app.social-publishing.meta.facebook.page-access-token",
      "type": "java.lang.String"
    },
    {
      "name": "app.social-publishing.meta.facebook.page-id",
      "type": "java.lang.String"
    },
    {
      "name": "app.social-publishing.meta.instagram.access-token",
      "type": "java.lang.String"
    },
    {
      "name": "app.social-publishing.meta.instagram.base-url",
      "type": "java.lang.String",
      "defaultValue": "https://graph.instagram.com/v23.0"
    },
    {
      "name": "app.social-publishing.meta.instagram.user-id",
      "type": "java.lang.String"
    },
    {
      "name": "app.social-publishing.meta.public-media-base-url",
      "type": "java.lang.String"
    },
```

In `infra/.env.example`, add a new section near the `APP_SOCIAL_PUBLISHING_N8N_*` lines (search for them — they were confirmed absent from the file before this task, so this is the first time any `APP_SOCIAL_PUBLISHING_*` line appears there):
```
# Social publishing — Meta (Instagram + Facebook) direct posting (Increment H)
APP_SOCIAL_PUBLISHING_META_INSTAGRAM_USER_ID=
APP_SOCIAL_PUBLISHING_META_INSTAGRAM_ACCESS_TOKEN=
APP_SOCIAL_PUBLISHING_META_FACEBOOK_PAGE_ID=
APP_SOCIAL_PUBLISHING_META_FACEBOOK_PAGE_ACCESS_TOKEN=
# Absolute base URL products live behind (Meta fetches image_url itself, server-side — a relative
# /api/media/... path will not work). e.g. https://pilarestilo.com
APP_SOCIAL_PUBLISHING_META_PUBLIC_MEDIA_BASE_URL=
```

- [ ] **Step 6: Run the full backend test suite to confirm nothing else broke**

Run: `cd backend && mvn test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/ \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/ \
        backend/src/main/resources/application.yml \
        backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json \
        infra/.env.example
git commit -m "feat(publication): add MetaPublishingConfigResolver + config keys

Resolves Instagram/Facebook credentials from system_settings (encrypted)
with env-var fallback, mirroring SocialPublishingN8nConfigResolver."
```

---

## Task 3: Meta adapters, dispatch port, and dispatcher (new, unwired)

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/application/ports/PublicationDispatcher.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationDispatchPayload.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/SocialPlatformPublisher.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapter.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapter.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/MetaDirectPublicationDispatcher.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapterTest.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapterTest.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/MetaDirectPublicationDispatcherTest.java`

**Interfaces:**
- Consumes: `MetaPublishingConfigResolver.resolve()` from Task 2.
- Produces: `PublicationDispatcher` interface with `dispatch(UUID publicationId, String idempotencyKey, PublicationDispatchPayload payload) → DispatchResult(String requestId, String payloadHash, PublicationAttemptStatus status, String remotePostId, String errorCode, String errorMessage)` and `PublicationDispatchPayload(UUID productId, PublicationPlatform platform, PublicationChannelType channelType, String caption, List<String> hashtags, String mediaUrl)` with a `fullCaptionText()` convenience method — Task 4 rewires `PublicationService` onto these.

These files are new and not yet wired to `PublicationService` (that's Task 4) — they are fully testable in isolation, which is the point of doing them as their own reviewable step before the cutover.

- [ ] **Step 1: Write the new port and payload**

```java
// backend/src/main/java/com/pilarestilo/publication/application/ports/PublicationDispatcher.java
package com.pilarestilo.publication.application.ports;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;

import java.util.UUID;

public interface PublicationDispatcher {
    DispatchResult dispatch(UUID publicationId, String idempotencyKey, PublicationDispatchPayload payload);

    record DispatchResult(
            String requestId,
            String payloadHash,
            PublicationAttemptStatus status,
            String remotePostId,
            String errorCode,
            String errorMessage
    ) {}
}
```

```java
// backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationDispatchPayload.java
package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.util.List;
import java.util.UUID;

public record PublicationDispatchPayload(
        UUID productId,
        PublicationPlatform platform,
        PublicationChannelType channelType,
        String caption,
        List<String> hashtags,
        String mediaUrl
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

- [ ] **Step 2: Write the failing adapter tests**

```java
// backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapterTest.java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InstagramGraphPublisherAdapterTest {

    private final PublicationDispatchPayload payload = new PublicationDispatchPayload(
            UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
            "Chaqueta a solo $49.990", List.of("#pilarestilo"), "https://cdn.example.com/chaqueta.jpg"
    );

    @Test
    void publishes_via_two_calls_and_returns_the_final_post_id() {
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

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, configResolver);
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("178923456", result.remotePostId());
        server.verify();
    }

    @Test
    void returns_a_failed_result_when_meta_rejects_the_request() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "17841423631997093", "token-ig", "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null
        ));

        server.expect(requestTo(org.hamcrest.Matchers.containsString("/media")))
                .andRespond(withServerError());

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(builder, configResolver);
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
        assertEquals("INSTAGRAM_PUBLISH_ERROR", result.errorCode());
    }

    @Test
    void returns_a_failed_result_when_credentials_are_not_configured() {
        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null
        ));

        InstagramGraphPublisherAdapter adapter = new InstagramGraphPublisherAdapter(RestClient.builder(), configResolver);
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
    }
}
```

```java
// backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapterTest.java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacebookPagePublisherAdapterTest {

    private final PublicationDispatchPayload payload = new PublicationDispatchPayload(
            UUID.randomUUID(), PublicationPlatform.FACEBOOK, PublicationChannelType.FEED_POST,
            "Chaqueta a solo $49.990", List.of("#pilarestilo"), "https://cdn.example.com/chaqueta.jpg"
    );

    @Test
    void publishes_via_one_call_and_returns_the_post_id() {
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

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        assertEquals("1023624300843445_555", result.remotePostId());
        server.verify();
    }

    @Test
    void returns_a_failed_result_when_credentials_are_not_configured() {
        MetaPublishingConfigResolver configResolver = mock(MetaPublishingConfigResolver.class);
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0",
                null, null, "https://graph.facebook.com/v23.0", null
        ));

        FacebookPagePublisherAdapter adapter = new FacebookPagePublisherAdapter(RestClient.builder(), configResolver);
        PublicationDispatcher.DispatchResult result = adapter.publish(payload);

        assertEquals(PublicationAttemptStatus.FAILED, result.status());
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd backend && mvn test -Dtest=InstagramGraphPublisherAdapterTest,FacebookPagePublisherAdapterTest`
Expected: FAIL — the adapter classes don't exist yet.

- [ ] **Step 4: Implement `SocialPlatformPublisher` and the two adapters**

```java
// backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/SocialPlatformPublisher.java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;

interface SocialPlatformPublisher {
    PublicationDispatcher.DispatchResult publish(PublicationDispatchPayload payload);
}
```

```java
// backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/InstagramGraphPublisherAdapter.java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

@Component
public class InstagramGraphPublisherAdapter implements SocialPlatformPublisher {

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;

    public InstagramGraphPublisherAdapter(RestClient.Builder restClientBuilder,
                                          MetaPublishingConfigResolver configResolver) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
    }

    @Override
    public PublicationDispatcher.DispatchResult publish(PublicationDispatchPayload payload) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.instagramUserId() == null || config.instagramAccessToken() == null) {
            return failed("Instagram credentials are not configured");
        }

        RestClient client = restClientBuilder.baseUrl(config.instagramBaseUrl()).build();
        try {
            Map<String, Object> created = client.post()
                    .uri("/{userId}/media?image_url={imageUrl}&caption={caption}&access_token={token}",
                            config.instagramUserId(), payload.mediaUrl(), payload.fullCaptionText(), config.instagramAccessToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            String creationId = String.valueOf(created.get("id"));

            Map<String, Object> published = client.post()
                    .uri("/{userId}/media_publish?creation_id={creationId}&access_token={token}",
                            config.instagramUserId(), creationId, config.instagramAccessToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            String remotePostId = String.valueOf(published.get("id"));

            return new PublicationDispatcher.DispatchResult(
                    UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED, remotePostId, null, null);
        } catch (RestClientException ex) {
            return failed(ex.getMessage());
        }
    }

    private PublicationDispatcher.DispatchResult failed(String message) {
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.FAILED, null,
                "INSTAGRAM_PUBLISH_ERROR", message);
    }
}
```

```java
// backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/FacebookPagePublisherAdapter.java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

@Component
public class FacebookPagePublisherAdapter implements SocialPlatformPublisher {

    private final RestClient.Builder restClientBuilder;
    private final MetaPublishingConfigResolver configResolver;

    public FacebookPagePublisherAdapter(RestClient.Builder restClientBuilder,
                                        MetaPublishingConfigResolver configResolver) {
        this.restClientBuilder = restClientBuilder;
        this.configResolver = configResolver;
    }

    @Override
    public PublicationDispatcher.DispatchResult publish(PublicationDispatchPayload payload) {
        MetaPublishingConfigResolver.EffectiveConfig config = configResolver.resolve();
        if (config.facebookPageId() == null || config.facebookPageAccessToken() == null) {
            return failed("Facebook credentials are not configured");
        }

        RestClient client = restClientBuilder.baseUrl(config.facebookBaseUrl()).build();
        try {
            Map<String, Object> response = client.post()
                    .uri("/{pageId}/photos?url={imageUrl}&caption={caption}&access_token={token}",
                            config.facebookPageId(), payload.mediaUrl(), payload.fullCaptionText(), config.facebookPageAccessToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            Object postId = response.get("post_id") != null ? response.get("post_id") : response.get("id");

            return new PublicationDispatcher.DispatchResult(
                    UUID.randomUUID().toString(), null, PublicationAttemptStatus.SUCCEEDED,
                    String.valueOf(postId), null, null);
        } catch (RestClientException ex) {
            return failed(ex.getMessage());
        }
    }

    private PublicationDispatcher.DispatchResult failed(String message) {
        return new PublicationDispatcher.DispatchResult(
                UUID.randomUUID().toString(), null, PublicationAttemptStatus.FAILED, null,
                "FACEBOOK_PUBLISH_ERROR", message);
    }
}
```

- [ ] **Step 5: Run the adapter tests to verify they pass**

Run: `cd backend && mvn test -Dtest=InstagramGraphPublisherAdapterTest,FacebookPagePublisherAdapterTest`
Expected: PASS

- [ ] **Step 6: Write the failing dispatcher test**

```java
// backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/MetaDirectPublicationDispatcherTest.java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationAttemptStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaDirectPublicationDispatcherTest {

    @Mock InstagramGraphPublisherAdapter instagram;
    @Mock FacebookPagePublisherAdapter facebook;
    @Mock MetaPublishingConfigResolver configResolver;

    MetaDirectPublicationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new MetaDirectPublicationDispatcher(instagram, facebook, configResolver);
    }

    @Test
    void routes_instagram_platform_to_the_instagram_adapter_with_an_absolute_url() {
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "ig-user", "ig-token", "https://graph.instagram.com/v23.0", null, null,
                "https://graph.facebook.com/v23.0", "https://pilarestilo.com"));
        when(instagram.publish(any())).thenReturn(new PublicationDispatcher.DispatchResult(
                "req-1", null, PublicationAttemptStatus.SUCCEEDED, "post-1", null, null));

        PublicationDispatchPayload payload = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "Caption", List.of(), "/api/media/products/x.jpg");

        PublicationDispatcher.DispatchResult result = dispatcher.dispatch(UUID.randomUUID(), "idem-1", payload);

        assertEquals(PublicationAttemptStatus.SUCCEEDED, result.status());
        org.mockito.ArgumentCaptor<PublicationDispatchPayload> captor =
                org.mockito.ArgumentCaptor.forClass(PublicationDispatchPayload.class);
        org.mockito.Mockito.verify(instagram).publish(captor.capture());
        assertEquals("https://pilarestilo.com/api/media/products/x.jpg", captor.getValue().mediaUrl());
    }

    @Test
    void leaves_an_already_absolute_url_untouched() {
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                null, null, "https://graph.instagram.com/v23.0", "fb-page", "fb-token",
                "https://graph.facebook.com/v23.0", null));
        when(facebook.publish(any())).thenReturn(new PublicationDispatcher.DispatchResult(
                "req-2", null, PublicationAttemptStatus.SUCCEEDED, "post-2", null, null));

        PublicationDispatchPayload payload = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.FACEBOOK, PublicationChannelType.FEED_POST,
                "Caption", List.of(), "https://cdn.example.com/x.jpg");

        dispatcher.dispatch(UUID.randomUUID(), "idem-2", payload);

        org.mockito.ArgumentCaptor<PublicationDispatchPayload> captor =
                org.mockito.ArgumentCaptor.forClass(PublicationDispatchPayload.class);
        org.mockito.Mockito.verify(facebook).publish(captor.capture());
        assertEquals("https://cdn.example.com/x.jpg", captor.getValue().mediaUrl());
    }

    @Test
    void throws_when_url_is_relative_and_no_public_base_is_configured() {
        when(configResolver.resolve()).thenReturn(new MetaPublishingConfigResolver.EffectiveConfig(
                "ig-user", "ig-token", "https://graph.instagram.com/v23.0", null, null,
                "https://graph.facebook.com/v23.0", null));

        PublicationDispatchPayload payload = new PublicationDispatchPayload(
                UUID.randomUUID(), PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                "Caption", List.of(), "/api/media/products/x.jpg");

        assertThrows(DomainException.class, () -> dispatcher.dispatch(UUID.randomUUID(), "idem-3", payload));
    }
}
```

- [ ] **Step 7: Run it to verify it fails**

Run: `cd backend && mvn test -Dtest=MetaDirectPublicationDispatcherTest`
Expected: FAIL — `MetaDirectPublicationDispatcher` doesn't exist yet.

- [ ] **Step 8: Implement `MetaDirectPublicationDispatcher`**

```java
package com.pilarestilo.publication.infrastructure.meta;

import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;
import com.pilarestilo.publication.application.ports.PublicationDispatcher;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MetaDirectPublicationDispatcher implements PublicationDispatcher {

    private final InstagramGraphPublisherAdapter instagram;
    private final FacebookPagePublisherAdapter facebook;
    private final MetaPublishingConfigResolver configResolver;

    public MetaDirectPublicationDispatcher(InstagramGraphPublisherAdapter instagram,
                                           FacebookPagePublisherAdapter facebook,
                                           MetaPublishingConfigResolver configResolver) {
        this.instagram = instagram;
        this.facebook = facebook;
        this.configResolver = configResolver;
    }

    @Override
    public DispatchResult dispatch(UUID publicationId, String idempotencyKey, PublicationDispatchPayload payload) {
        String absoluteMediaUrl = resolveAbsoluteUrl(payload.mediaUrl(), configResolver.resolve().publicMediaBaseUrl());
        PublicationDispatchPayload resolvedPayload = new PublicationDispatchPayload(
                payload.productId(), payload.platform(), payload.channelType(),
                payload.caption(), payload.hashtags(), absoluteMediaUrl);
        return publisherFor(payload.platform()).publish(resolvedPayload);
    }

    private SocialPlatformPublisher publisherFor(PublicationPlatform platform) {
        return switch (platform) {
            case INSTAGRAM -> instagram;
            case FACEBOOK -> facebook;
        };
    }

    private String resolveAbsoluteUrl(String mediaUrl, String publicBaseUrl) {
        if (mediaUrl != null && (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://"))) {
            return mediaUrl;
        }
        if (mediaUrl == null || mediaUrl.isBlank()) {
            throw new DomainException("Cannot dispatch publication without a media URL");
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new DomainException(
                    "Cannot resolve absolute media URL: app.social-publishing.meta.public-media-base-url is not configured");
        }
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        String path = mediaUrl.startsWith("/") ? mediaUrl : "/" + mediaUrl;
        return base + path;
    }
}
```

- [ ] **Step 9: Run the dispatcher test to verify it passes, then the full backend suite**

Run: `cd backend && mvn test -Dtest=MetaDirectPublicationDispatcherTest`
Expected: PASS

Run: `cd backend && mvn test`
Expected: PASS (these new beans aren't wired into `PublicationService` yet, so nothing else is affected)

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/ports/PublicationDispatcher.java \
        backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationDispatchPayload.java \
        backend/src/main/java/com/pilarestilo/publication/infrastructure/meta/ \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/meta/
git commit -m "feat(publication): add Meta adapters and the widened dispatch port

New PublicationDispatcher port, PublicationDispatchPayload, the two
platform adapters and MetaDirectPublicationDispatcher — not yet wired
into PublicationService. Adapter selection is constructor-injected
concretes + an exhaustive switch, matching SystemSettingsNotificationSender,
not a Map<Enum,Interface> (no precedent for that pattern in this repo)."
```

---

## Task 4: Cutover — rewire `PublicationService`, fix the rollback bug, delete n8n

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/publication/application/PublicationService.java`
- Delete: `backend/src/main/java/com/pilarestilo/publication/application/ports/PublicationWebhookDispatcher.java`
- Delete: `backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationDispatchWebhookPayload.java`
- Delete: `backend/src/main/java/com/pilarestilo/publication/application/commands/PublicationExternalResultCommand.java`
- Delete: `backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationExternalResultDto.java`
- Delete: `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/controllers/PublicationWebhookController.java`
- Delete: `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/requests/PublicationExternalResultRequest.java`
- Delete: `backend/src/main/java/com/pilarestilo/publication/infrastructure/n8n/N8nPublicationWebhookDispatcher.java`
- Delete: `backend/src/main/java/com/pilarestilo/publication/infrastructure/n8n/SocialPublishingN8nConfigResolver.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/infrastructure/bootstrap/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml` (remove the `n8n:` block under `social-publishing:`)
- Modify: `backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json` (remove the n8n group + 4 properties)
- Modify: `backend/src/test/java/com/pilarestilo/publication/application/PublicationServiceTest.java`
- Modify: `backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationControllerIT.java`
- Modify: `docs/n8n-integration.md` (remove or mark obsolete the publication-dispatch section — search for "publication"/"Publication" in that file and delete the matching section)

**Interfaces:**
- Consumes: `PublicationDispatcher`, `PublicationDispatchPayload`, `MetaDirectPublicationDispatcher` from Task 3 (Spring wires `MetaDirectPublicationDispatcher` as the sole `PublicationDispatcher` bean once the n8n implementation is deleted).
- Produces: `PublicationService.dispatch(UUID, UUID)` / `.retry(UUID, UUID)` now return a `PublicationDto` whose `status()` reflects the real outcome (`PUBLISHED` or `FAILED`) without throwing on an ordinary dispatch failure — Task 5's batch use case relies on reading `dispatched.status()` rather than catching an exception for the expected-failure case.

- [ ] **Step 1: Update `PublicationServiceTest` first (still red — this is the spec for the fix)**

Replace the `@Mock PublicationWebhookDispatcher webhookDispatcher;` field with `@Mock PublicationDispatcher publicationDispatcher;`, update the `service = new PublicationService(...)` call in `setUp()` to pass `publicationDispatcher`, and update imports (`PublicationWebhookDispatcher` → `PublicationDispatcher`, add `PublicationDispatchPayload`, remove `PublicationExternalResultCommand`).

Replace the `dispatch_from_approved_publication_creates_attempt_and_publishes_event` test's stub:
```java
when(publicationDispatcher.dispatch(any(), anyString(), any()))
        .thenReturn(new PublicationDispatcher.DispatchResult(
                "req-1", "hash-1", PublicationAttemptStatus.SUCCEEDED, "remote-1", null, null));
```
and add assertions that it ends up `PUBLISHED`:
```java
assertEquals(PublicationStatus.PUBLISHED, dto.status());
assertEquals("remote-1", dto.externalPostId());
verify(eventPublisher).publish(any(PublicationDispatchCompleted.class));
```
(remove the old `PublicationStatus.PUBLISHING`/`PublicationDispatchRequested` assertions — dispatch is synchronous now, it doesn't stop at PUBLISHING).

Delete the `external_success_marks_publication_published` test entirely (`registerExternalResult` no longer exists).

Add two new tests:
```java
@Test
void dispatch_persists_failure_without_losing_the_record_when_dispatcher_reports_failure() {
    UUID publicationId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    PublicationEntity entity = approvedPublication(publicationId, productId);
    when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
    when(productRepository.findById(productId)).thenReturn(Optional.of(
            Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(49990), "CLP"),
                    "https://img", ProductCondition.NEW, "Pilar", 2)
    ));
    when(publicationDispatcher.dispatch(any(), anyString(), any()))
            .thenReturn(new PublicationDispatcher.DispatchResult(
                    "req-1", "hash-1", PublicationAttemptStatus.FAILED, null,
                    "INSTAGRAM_PUBLISH_ERROR", "Rate limited"));

    PublicationDto dto = service.dispatch(publicationId, UUID.randomUUID());

    // This is the regression test for the rollback bug: before the fix, dispatchInternal
    // rethrew after saving, which rolled back that same save — the FAILED status and error
    // never actually reached the database. Now it must.
    assertEquals(PublicationStatus.FAILED, dto.status());
    assertEquals("INSTAGRAM_PUBLISH_ERROR", dto.lastErrorCode());
    assertEquals("Rate limited", dto.lastErrorMessage());
    verify(publicationRepository, atLeastOnce()).save(any(PublicationEntity.class));
}

@Test
void dispatch_persists_failure_even_when_the_dispatcher_throws_unexpectedly() {
    UUID publicationId = UUID.randomUUID();
    PublicationEntity entity = approvedPublication(publicationId, null);
    when(publicationRepository.findById(publicationId)).thenReturn(Optional.of(entity));
    when(publicationDispatcher.dispatch(any(), anyString(), any()))
            .thenThrow(new RuntimeException("connection reset"));

    PublicationDto dto = service.dispatch(publicationId, UUID.randomUUID());

    assertEquals(PublicationStatus.FAILED, dto.status());
    assertEquals("connection reset", dto.lastErrorMessage());
}
```
(add `import static org.mockito.Mockito.atLeastOnce;` and `import com.pilarestilo.publication.application.ports.PublicationDispatcher;` and `import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;` to the test file's imports; the `Money`/`ProductCondition` imports already exist in this file per Task 3's earlier read.)

- [ ] **Step 2: Run the test file to verify it fails to compile / fails**

Run: `cd backend && mvn test -Dtest=PublicationServiceTest`
Expected: FAIL (compile error — `PublicationService`'s constructor still takes the old type, `registerExternalResult` still exists and is still called by the not-yet-deleted test line if you missed removing it)

- [ ] **Step 3: Rewire `PublicationService`**

Update the imports: replace `import com.pilarestilo.publication.application.ports.PublicationWebhookDispatcher;` with `import com.pilarestilo.publication.application.ports.PublicationDispatcher;`, replace `import com.pilarestilo.publication.application.dto.PublicationDispatchWebhookPayload;` with `import com.pilarestilo.publication.application.dto.PublicationDispatchPayload;`, remove `import com.pilarestilo.publication.application.commands.PublicationExternalResultCommand;` and `import com.pilarestilo.publication.application.dto.PublicationExternalResultDto;`.

Rename the field and constructor parameter `publicationWebhookDispatcher` (type `PublicationWebhookDispatcher`) to `publicationDispatcher` (type `PublicationDispatcher`).

Replace `dispatch`, `retry`, and `dispatchInternal` with:
```java
    @Transactional
    public PublicationDto dispatch(UUID id, UUID actorUserId) {
        return dispatchInternal(id, PublicationAttemptTriggerType.MANUAL);
    }

    @Transactional
    public PublicationDto retry(UUID id, UUID actorUserId) {
        PublicationEntity entity = findById(id);
        if (entity.getStatus() != PublicationStatus.FAILED) {
            throw new DomainException("Only FAILED publications can be retried");
        }
        entity.setRetryCount(entity.getRetryCount() + 1);
        publicationRepository.save(entity);
        return dispatchInternal(id, PublicationAttemptTriggerType.RETRY);
    }

    private PublicationDto dispatchInternal(UUID id, PublicationAttemptTriggerType triggerType) {
        PublicationEntity entity = findById(id);
        if (!(entity.getStatus() == PublicationStatus.APPROVED || entity.getStatus() == PublicationStatus.SCHEDULED)) {
            throw new DomainException("Publication cannot be dispatched from status " + entity.getStatus());
        }

        Instant now = Instant.now();
        entity.setStatus(PublicationStatus.PUBLISHING);
        entity.setUpdatedAt(now);

        if (entity.getSourceType() == PublicationSourceType.PRODUCT) {
            UUID snapshotProductId = entity.getProductId() != null ? entity.getProductId() : entity.getSourceId();
            if (snapshotProductId != null) {
                Product product = productRepository.findById(snapshotProductId)
                        .orElseThrow(() -> new DomainException("Product snapshot source not found: " + snapshotProductId));
                addSnapshot(entity, PublicationSnapshotType.SOURCE_PRODUCT, buildProductSnapshot(product), now);
            }
        }

        PublicationDispatchPayload payload = buildDispatchPayload(entity);
        addSnapshot(entity, PublicationSnapshotType.OUTBOUND_WEBHOOK, toMap(payload), now);

        PublicationAttemptEntity attempt = new PublicationAttemptEntity();
        attempt.setId(UUID.randomUUID());
        attempt.setPublication(entity);
        attempt.setAttemptNumber(entity.getAttempts().size() + 1);
        attempt.setTriggerType(triggerType);
        attempt.setStatus(PublicationAttemptStatus.STARTED);
        attempt.setStartedAt(now);
        entity.getAttempts().add(attempt);

        PublicationDispatcher.DispatchResult result;
        try {
            result = publicationDispatcher.dispatch(entity.getId(), entity.getIdempotencyKey(), payload);
        } catch (RuntimeException ex) {
            result = new PublicationDispatcher.DispatchResult(
                    null, null, PublicationAttemptStatus.FAILED, null, DISPATCH_ERROR_CODE, ex.getMessage());
        }

        Instant finishedAt = Instant.now();
        attempt.setRequestId(result.requestId());
        attempt.setPayloadHash(result.payloadHash());
        attempt.setStatus(result.status());
        attempt.setFinishedAt(finishedAt);
        attempt.setRemotePostId(result.remotePostId());
        attempt.setErrorCode(result.errorCode());
        attempt.setErrorMessage(result.errorMessage());
        entity.setUpdatedAt(finishedAt);

        if (result.status() == PublicationAttemptStatus.SUCCEEDED) {
            entity.setStatus(PublicationStatus.PUBLISHED);
            entity.setPublishedAt(finishedAt);
            entity.setExternalPostId(result.remotePostId());
            entity.setLastErrorCode(null);
            entity.setLastErrorMessage(null);
            PublicationEntity saved = publicationRepository.save(entity);
            eventPublisher.publish(new PublicationDispatchCompleted(saved.getId(), attempt.getAttemptNumber(), result.remotePostId()));
            return toDto(saved);
        }

        entity.setStatus(PublicationStatus.FAILED);
        entity.setLastErrorCode(result.errorCode());
        entity.setLastErrorMessage(result.errorMessage());
        PublicationEntity saved = publicationRepository.save(entity);
        eventPublisher.publish(new PublicationDispatchFailed(saved.getId(), attempt.getAttemptNumber(), result.errorCode()));
        return toDto(saved);
    }
```

Replace `buildWebhookPayload` with:
```java
    private PublicationDispatchPayload buildDispatchPayload(PublicationEntity entity) {
        PublicationMediaBundleEntity bundle = entity.getMediaBundles().isEmpty() ? null : entity.getMediaBundles().get(0);
        return new PublicationDispatchPayload(
                entity.getProductId(),
                entity.getPlatform(),
                entity.getChannelType(),
                entity.getCaption(),
                readHashtags(entity.getHashtagsJson()),
                bundle == null ? null : bundle.getPrimaryAssetUrl()
        );
    }
```

Update `toMap`'s parameter type from `PublicationDispatchWebhookPayload` to `PublicationDispatchPayload`.

Delete the entire `registerExternalResult` method and its now-unused imports (`PublicationExternalResultCommand`, `PublicationExternalResultDto`, `NoSuchElementException` stays — still used by `findById`).

- [ ] **Step 4: Run `PublicationServiceTest` to verify it passes**

Run: `cd backend && mvn test -Dtest=PublicationServiceTest`
Expected: PASS

- [ ] **Step 5: Delete the n8n package and the external-result machinery**

```bash
rm backend/src/main/java/com/pilarestilo/publication/infrastructure/n8n/N8nPublicationWebhookDispatcher.java
rm backend/src/main/java/com/pilarestilo/publication/infrastructure/n8n/SocialPublishingN8nConfigResolver.java
rmdir backend/src/main/java/com/pilarestilo/publication/infrastructure/n8n 2>/dev/null || true
rm backend/src/main/java/com/pilarestilo/publication/infrastructure/web/controllers/PublicationWebhookController.java
rm backend/src/main/java/com/pilarestilo/publication/infrastructure/web/requests/PublicationExternalResultRequest.java
rm backend/src/main/java/com/pilarestilo/publication/application/ports/PublicationWebhookDispatcher.java
rm backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationDispatchWebhookPayload.java
rm backend/src/main/java/com/pilarestilo/publication/application/commands/PublicationExternalResultCommand.java
rm backend/src/main/java/com/pilarestilo/publication/application/dto/PublicationExternalResultDto.java
```

In `SecurityConfig.java`, delete the line:
```java
.requestMatchers(HttpMethod.POST, "/api/publications/*/external-result").permitAll()
```

In `application.yml`, delete the `n8n:` block (4 lines) under `social-publishing:`, leaving only the `meta:` block added in Task 2.

In `additional-spring-configuration-metadata.json`, delete the `app.social-publishing.n8n` group entry and its 4 `properties` entries.

In `docs/n8n-integration.md`, find and delete the section documenting the publication-dispatch webhook (search for "publication" case-insensitively).

- [ ] **Step 6: Update `PublicationControllerIT`**

Remove `registry.add("app.social-publishing.n8n.callback-token", () -> "test-social-token");` from `configureProps`.

In `admin_can_approve_dispatch_and_finalize_publication_via_callback`, rename it to `admin_can_approve_and_dispatch_a_publication_synchronously` and replace everything from the `mvc.perform(post("/api/admin/publications/{id}/dispatch", ...))` call onward with:
```java
        mvc.perform(post("/api/admin/publications/{id}/dispatch", publicationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                // No Meta credentials are configured in this test environment, so the outbound
                // call itself fails — but that failure must actually reach the database, which is
                // exactly the rollback bug this cutover fixed. Before the fix this row would have
                // stayed at PUBLISHING forever with no error recorded.
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts", hasSize(1)))
                .andExpect(jsonPath("$.lastErrorCode").exists());

        mvc.perform(get("/api/admin/publications/{id}", publicationId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }
```
(delete the old `mvc.perform(post("/api/publications/{id}/external-result", ...))` block entirely — that route no longer exists.)

- [ ] **Step 7: Run the full IT + unit suite**

Run: `cd backend && mvn verify -Dtest=PublicationServiceTest,PublicationControllerIT -DfailIfNoTests=false`
Expected: PASS

Run: `cd backend && mvn test`
Expected: PASS (confirms nothing else in the backend referenced the deleted n8n classes)

- [ ] **Step 8: Commit**

```bash
git add -A backend/src/main/java/com/pilarestilo/publication/ \
        backend/src/main/java/com/pilarestilo/shared/infrastructure/bootstrap/SecurityConfig.java \
        backend/src/main/resources/application.yml \
        backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json \
        backend/src/test/java/com/pilarestilo/publication/ \
        docs/n8n-integration.md
git commit -m "feat(publication): cut over to direct Meta dispatch, delete n8n

PublicationService now dispatches through PublicationDispatcher
(MetaDirectPublicationDispatcher is the only implementation left).
Fixes a real bug along the way: dispatchInternal used to persist a
FAILED status and then rethrow, rolling back that same save inside its
own @Transactional boundary — invisible only because the n8n dispatcher
never threw. Ordinary dispatch failures are now a normal return value,
not an exception, so they actually reach the database. registerExternalResult
and the whole n8n webhook path are deleted, not bypassed."
```

---

## Task 5: `PublishProductsBatchUseCase`

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/application/commands/PublishProductsBatchCommand.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/application/dto/PublishProductsBatchResult.java`
- Create: `backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCase.java`
- Test: `backend/src/test/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCaseTest.java`

**Interfaces:**
- Consumes: `PublicationService.create(CreatePublicationCommand, UUID) → CreatePublicationResult`, `PublicationService.dispatch(UUID, UUID) → PublicationDto` (both already exist), `ProductRepository.findById(UUID) → Optional<Product>` (already exists).
- Produces: `PublishProductsBatchUseCase.execute(PublishProductsBatchCommand, UUID actorUserId) → PublishProductsBatchResult` — Task 6's controller endpoint calls this directly.

- [ ] **Step 1: Write the failing tests**

```java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
import com.pilarestilo.publication.application.dto.PublicationDto;
import com.pilarestilo.publication.application.dto.PublishProductsBatchResult;
import com.pilarestilo.publication.domain.enums.PublicationApprovalStatus;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishProductsBatchUseCaseTest {

    @Mock PublicationService publicationService;
    @Mock ProductRepository productRepository;

    PublishProductsBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PublishProductsBatchUseCase(publicationService, productRepository);
    }

    @Test
    void interpolates_caption_template_per_product_and_dispatches_each_selected_platform() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(49990), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 2);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any()))
                .thenReturn(publishedDto(publicationId));

        PublishProductsBatchResult result = useCase.execute(new PublishProductsBatchCommand(
                List.of(productId),
                Set.of(PublicationPlatform.INSTAGRAM, PublicationPlatform.FACEBOOK),
                "{producto} a solo {precio}!",
                List.of("#pilarestilo"),
                "Liquidacion"
        ), UUID.randomUUID());

        assertEquals(2, result.items().size());
        assertTrue(result.items().stream().allMatch(PublishProductsBatchResult.PublicationItemResult::success));

        ArgumentCaptor<CreatePublicationCommand> captor = ArgumentCaptor.forClass(CreatePublicationCommand.class);
        verify(publicationService, times(2)).create(captor.capture(), any());
        assertTrue(captor.getAllValues().stream()
                .allMatch(cmd -> cmd.caption().equals("Chaqueta a solo $49.990!")));
        assertTrue(captor.getAllValues().stream()
                .allMatch(cmd -> cmd.campaignLabel().equals("Liquidacion")));
        assertTrue(captor.getAllValues().stream().noneMatch(CreatePublicationCommand::approvalRequired));
    }

    @Test
    void one_missing_product_does_not_stop_the_rest_of_the_batch() {
        UUID okProductId = UUID.randomUUID();
        UUID missingProductId = UUID.randomUUID();
        Product okProduct = Product.create("Chaqueta", "desc", new Money(BigDecimal.valueOf(10000), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 2);
        when(productRepository.findById(okProductId)).thenReturn(Optional.of(okProduct));
        when(productRepository.findById(missingProductId)).thenReturn(Optional.empty());

        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any()))
                .thenReturn(publishedDto(publicationId));

        PublishProductsBatchResult result = useCase.execute(new PublishProductsBatchCommand(
                List.of(missingProductId, okProductId),
                Set.of(PublicationPlatform.INSTAGRAM),
                "{producto}", List.of(), null
        ), UUID.randomUUID());

        assertEquals(2, result.items().size());
        assertFalse(result.items().get(0).success());
        assertTrue(result.items().get(0).errorMessage().contains("no encontrado"));
        assertTrue(result.items().get(1).success());
    }

    @Test
    void a_thrown_exception_for_one_item_is_recorded_without_stopping_the_batch() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Bolso", "desc", new Money(BigDecimal.valueOf(5000), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 1);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenThrow(new DomainException("boom"));

        PublishProductsBatchResult result = useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.FACEBOOK), "{producto}", List.of(), null
        ), UUID.randomUUID());

        assertEquals(1, result.items().size());
        assertFalse(result.items().get(0).success());
        assertEquals("boom", result.items().get(0).errorMessage());
    }

    @Test
    void a_dispatch_result_that_is_not_published_is_reported_as_a_failure_with_its_error() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create("Falda", "desc", new Money(BigDecimal.valueOf(8000), "CLP"),
                "https://img", ProductCondition.NEW, "Pilar", 1);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        UUID publicationId = UUID.randomUUID();
        when(publicationService.create(any(CreatePublicationCommand.class), any()))
                .thenReturn(new CreatePublicationResult(publishedDto(publicationId), true));
        when(publicationService.dispatch(eq(publicationId), any()))
                .thenReturn(failedDto(publicationId, "Instagram credentials are not configured"));

        PublishProductsBatchResult result = useCase.execute(new PublishProductsBatchCommand(
                List.of(productId), Set.of(PublicationPlatform.INSTAGRAM), "{producto}", List.of(), null
        ), UUID.randomUUID());

        assertFalse(result.items().get(0).success());
        assertEquals("Instagram credentials are not configured", result.items().get(0).errorMessage());
    }

    private PublicationDto publishedDto(UUID id) {
        return dto(id, PublicationStatus.PUBLISHED, null);
    }

    private PublicationDto failedDto(UUID id, String errorMessage) {
        return dto(id, PublicationStatus.FAILED, errorMessage);
    }

    private PublicationDto dto(UUID id, PublicationStatus status, String lastErrorMessage) {
        return new PublicationDto(
                id, null, PublicationSourceType.PRODUCT, null,
                PublicationPlatform.INSTAGRAM, PublicationChannelType.FEED_POST,
                status, PublicationApprovalStatus.NOT_REQUIRED,
                "caption", List.of(), "es-CL", null, null, Instant.now(), "remote-1",
                "idem-1", 1, 1, null, lastErrorMessage, 0, null, null,
                Instant.now(), Instant.now(),
                List.of(), List.of(), List.of(), List.of()
        );
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn test -Dtest=PublishProductsBatchUseCaseTest`
Expected: FAIL — none of `PublishProductsBatchCommand`, `PublishProductsBatchResult`, `PublishProductsBatchUseCase` exist yet.

- [ ] **Step 3: Implement the command, result, and use case**

```java
// backend/src/main/java/com/pilarestilo/publication/application/commands/PublishProductsBatchCommand.java
package com.pilarestilo.publication.application.commands;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PublishProductsBatchCommand(
        List<UUID> productIds,
        Set<PublicationPlatform> platforms,
        String captionTemplate,
        List<String> hashtags,
        String campaignLabel
) {}
```

```java
// backend/src/main/java/com/pilarestilo/publication/application/dto/PublishProductsBatchResult.java
package com.pilarestilo.publication.application.dto;

import com.pilarestilo.publication.domain.enums.PublicationPlatform;

import java.util.List;
import java.util.UUID;

public record PublishProductsBatchResult(
        List<PublicationItemResult> items
) {
    public record PublicationItemResult(
            UUID productId,
            PublicationPlatform platform,
            boolean success,
            UUID publicationId,
            String errorMessage
    ) {}
}
```

```java
// backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCase.java
package com.pilarestilo.publication.application.usecases;

import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.publication.application.PublicationService;
import com.pilarestilo.publication.application.commands.CreatePublicationCommand;
import com.pilarestilo.publication.application.commands.PublishProductsBatchCommand;
import com.pilarestilo.publication.application.dto.CreatePublicationResult;
import com.pilarestilo.publication.application.dto.PublicationDto;
import com.pilarestilo.publication.application.dto.PublishProductsBatchResult;
import com.pilarestilo.publication.domain.enums.PublicationChannelType;
import com.pilarestilo.publication.domain.enums.PublicationMediaBundleType;
import com.pilarestilo.publication.domain.enums.PublicationPlatform;
import com.pilarestilo.publication.domain.enums.PublicationSourceType;
import com.pilarestilo.publication.domain.enums.PublicationStatus;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates a multi-product, multi-platform publish. Deliberately not @Transactional: each
 * item's create()+dispatch() opens its own transaction on PublicationService (a different Spring
 * bean, so its @Transactional proxy applies independently). Wrapping this loop in one outer
 * transaction would mark it rollback-only the moment any single item's call threw, losing every
 * other item's already-recorded result too — the opposite of "each row is independent."
 */
@Component
public class PublishProductsBatchUseCase {

    private final PublicationService publicationService;
    private final ProductRepository productRepository;

    public PublishProductsBatchUseCase(PublicationService publicationService, ProductRepository productRepository) {
        this.publicationService = publicationService;
        this.productRepository = productRepository;
    }

    public PublishProductsBatchResult execute(PublishProductsBatchCommand command, UUID actorUserId) {
        List<PublishProductsBatchResult.PublicationItemResult> items = new ArrayList<>();

        for (UUID productId : command.productIds()) {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) {
                for (PublicationPlatform platform : command.platforms()) {
                    items.add(new PublishProductsBatchResult.PublicationItemResult(
                            productId, platform, false, null, "Producto no encontrado: " + productId));
                }
                continue;
            }
            String caption = interpolate(command.captionTemplate(), product);
            for (PublicationPlatform platform : command.platforms()) {
                items.add(publishOne(productId, product, platform, caption, command, actorUserId));
            }
        }

        return new PublishProductsBatchResult(items);
    }

    private PublishProductsBatchResult.PublicationItemResult publishOne(UUID productId,
                                                                        Product product,
                                                                        PublicationPlatform platform,
                                                                        String caption,
                                                                        PublishProductsBatchCommand command,
                                                                        UUID actorUserId) {
        try {
            CreatePublicationCommand createCommand = new CreatePublicationCommand(
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
                    null,
                    "pub-batch-" + productId + "-" + platform.name() + "-" + UUID.randomUUID(),
                    List.of(new CreatePublicationCommand.MediaBundleCommand(
                            PublicationMediaBundleType.SOCIAL_FEED,
                            product.getImageUrl(),
                            Map.of()
                    ))
            );
            CreatePublicationResult created = publicationService.create(createCommand, actorUserId);
            PublicationDto dispatched = publicationService.dispatch(created.publication().id(), actorUserId);

            boolean success = dispatched.status() == PublicationStatus.PUBLISHED;
            return new PublishProductsBatchResult.PublicationItemResult(
                    productId, platform, success, dispatched.id(),
                    success ? null : dispatched.lastErrorMessage());
        } catch (DomainException ex) {
            return new PublishProductsBatchResult.PublicationItemResult(
                    productId, platform, false, null, ex.getMessage());
        }
    }

    private String interpolate(String template, Product product) {
        String priceText = NumberFormat.getInstance(Locale.of("es", "CL")).format(product.getPrice().amount());
        return template
                .replace("{producto}", product.getName())
                .replace("{precio}", "$" + priceText);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && mvn test -Dtest=PublishProductsBatchUseCaseTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/application/commands/PublishProductsBatchCommand.java \
        backend/src/main/java/com/pilarestilo/publication/application/dto/PublishProductsBatchResult.java \
        backend/src/main/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCase.java \
        backend/src/test/java/com/pilarestilo/publication/application/usecases/PublishProductsBatchUseCaseTest.java
git commit -m "feat(publication): add PublishProductsBatchUseCase

Multi-product x multi-platform batch orchestrator, reusing the existing
create()/dispatch() pair per item. Not @Transactional by design — see
class Javadoc."
```

---

## Task 6: Batch controller endpoint

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/requests/PublishProductsBatchRequest.java`
- Modify: `backend/src/main/java/com/pilarestilo/publication/infrastructure/web/controllers/PublicationController.java`
- Modify: `backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationControllerIT.java`

**Interfaces:**
- Consumes: `PublishProductsBatchUseCase.execute(...)` from Task 5.
- Produces: `POST /api/admin/publications/batch` → 200, JSON body matching `PublishProductsBatchResult` — Task 7's frontend API client calls this.

- [ ] **Step 1: Write the failing IT tests**

Add to `PublicationControllerIT.java` (add `@Autowired ProductRepository productRepository;` to the field list, and imports `com.pilarestilo.product.domain.ports.ProductRepository`, `com.pilarestilo.product.domain.model.Product`, `com.pilarestilo.product.domain.enums.ProductCondition`, `com.pilarestilo.shared.application.Money`, `java.math.BigDecimal`):

```java
    @Test
    void batch_endpoint_requires_publications_update_permission() throws Exception {
        String sellerToken = jwtTokenProvider.generateAccessToken(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "seller-batch@pilarestilo.com",
                UserRole.SELLER,
                List.of("productos"),
                List.of("publications.read")
        );

        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(UUID.randomUUID().toString()),
                                "platforms", List.of("INSTAGRAM"),
                                "captionTemplate", "{producto}"
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void batch_publishes_each_product_times_platform_combination_and_survives_a_missing_product() throws Exception {
        String adminToken = loginAdmin();
        Product product = Product.create("Chaqueta boutique", "desc",
                new Money(BigDecimal.valueOf(49990), "CLP"), "https://cdn.example.com/chaqueta.jpg",
                ProductCondition.NEW, "Pilar", 5);
        Product saved = productRepository.save(product);
        String missingProductId = UUID.randomUUID().toString();

        mvc.perform(post("/api/admin/publications/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "productIds", List.of(saved.getId().toString(), missingProductId),
                                "platforms", List.of("INSTAGRAM", "FACEBOOK"),
                                "captionTemplate", "{producto} a solo {precio}",
                                "hashtags", List.of("#pilarestilo"),
                                "campaignLabel", "Liquidacion"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].productId").value(saved.getId().toString()))
                .andExpect(jsonPath("$.items[0].platform").value("INSTAGRAM"))
                .andExpect(jsonPath("$.items[0].publicationId").exists())
                // No Meta credentials configured in this test environment: the dispatch itself
                // fails, but a real Publication row must exist — proving the item was actually
                // created and dispatched, not silently skipped.
                .andExpect(jsonPath("$.items[0].success").value(false))
                .andExpect(jsonPath("$.items[2].productId").value(missingProductId))
                .andExpect(jsonPath("$.items[2].publicationId").doesNotExist())
                .andExpect(jsonPath("$.items[2].errorMessage",
                        org.hamcrest.Matchers.containsString("no encontrado")));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && mvn verify -Dtest=PublicationControllerIT`
Expected: FAIL — `/api/admin/publications/batch` returns 404 (no such endpoint yet).

- [ ] **Step 3: Implement the request DTO and controller endpoint**

```java
// backend/src/main/java/com/pilarestilo/publication/infrastructure/web/requests/PublishProductsBatchRequest.java
package com.pilarestilo.publication.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record PublishProductsBatchRequest(
        @NotEmpty List<UUID> productIds,
        @NotEmpty List<@NotBlank String> platforms,
        @NotBlank String captionTemplate,
        List<String> hashtags,
        String campaignLabel
) {}
```

In `PublicationController.java`, add the constructor dependency and the endpoint (add imports `com.pilarestilo.publication.application.usecases.PublishProductsBatchUseCase`, `com.pilarestilo.publication.application.commands.PublishProductsBatchCommand`, `com.pilarestilo.publication.application.dto.PublishProductsBatchResult`, `com.pilarestilo.publication.infrastructure.web.requests.PublishProductsBatchRequest`, `java.util.LinkedHashSet`, `java.util.Set`, `java.util.stream.Collectors`):

```java
    private final PublishProductsBatchUseCase publishProductsBatchUseCase;

    public PublicationController(PublicationService publicationService,
                                 PublishProductsBatchUseCase publishProductsBatchUseCase) {
        this.publicationService = publicationService;
        this.publishProductsBatchUseCase = publishProductsBatchUseCase;
    }
```
(this changes the existing single-arg constructor to two args — Spring autowires both by type, no other change needed)

```java
    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN') or @rbac.hasPermission(authentication, T(com.pilarestilo.shared.rbac.domain.PermissionRegistry).PUBLICATIONS_UPDATE)")
    public PublishProductsBatchResult publishBatch(@Valid @RequestBody PublishProductsBatchRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return publishProductsBatchUseCase.execute(toBatchCommand(request), currentUser == null ? null : currentUser.id());
    }

    private PublishProductsBatchCommand toBatchCommand(PublishProductsBatchRequest request) {
        Set<PublicationPlatform> platforms = request.platforms().stream()
                .map(p -> PublicationPlatform.valueOf(p.trim().toUpperCase()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new PublishProductsBatchCommand(
                request.productIds(),
                platforms,
                request.captionTemplate(),
                request.hashtags() == null ? List.of() : request.hashtags(),
                request.campaignLabel()
        );
    }
```

- [ ] **Step 4: Run the IT tests to verify they pass**

Run: `cd backend && mvn verify -Dtest=PublicationControllerIT`
Expected: PASS

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && mvn verify`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/publication/infrastructure/web/ \
        backend/src/test/java/com/pilarestilo/publication/infrastructure/web/PublicationControllerIT.java
git commit -m "feat(publication): add POST /api/admin/publications/batch endpoint"
```

---

## Task 7: Frontend API client

**Files:**
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Consumes: `apiFetch<T>(path, init)`, `authHeaders(token)` (both already exist in this file).
- Produces: `publishProductsBatch(body, token) → Promise<PublishProductsBatchResponse>`, and the `PublishProductsBatchRequest`/`PublishProductsBatchItemResult`/`PublishProductsBatchResponse` types — Task 8's `PublicacionesPage.tsx` imports these.

- [ ] **Step 1: Add the types and function**

Add near `registerExternalSale` (around line 1493) in `frontend/src/lib/api.ts`:

```typescript
export interface PublishProductsBatchRequest {
  productIds: string[];
  platforms: Array<'INSTAGRAM' | 'FACEBOOK'>;
  captionTemplate: string;
  hashtags?: string[];
  campaignLabel?: string;
}

export interface PublishProductsBatchItemResult {
  productId: string;
  platform: 'INSTAGRAM' | 'FACEBOOK';
  success: boolean;
  publicationId: string | null;
  errorMessage: string | null;
}

export interface PublishProductsBatchResponse {
  items: PublishProductsBatchItemResult[];
}

export async function publishProductsBatch(
  body: PublishProductsBatchRequest,
  token: string,
): Promise<PublishProductsBatchResponse> {
  return apiFetch<PublishProductsBatchResponse>('/admin/publications/batch', {
    method: 'POST',
    body: JSON.stringify(body),
    headers: authHeaders(token),
  });
}
```

- [ ] **Step 2: Run the frontend typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS (no type errors)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(frontend): add publishProductsBatch API client function"
```

---

## Task 8: `PublicacionesPage` + sidebar entry

**Files:**
- Create: `frontend/src/islands/admin/PublicacionesPage.tsx`
- Create: `frontend/src/pages/admin/publicaciones.astro`
- Modify: `frontend/src/islands/admin/AdminSidebar.tsx`
- Test: `frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx`

**Interfaces:**
- Consumes: `searchProducts`, `publishProductsBatch`, `ProductDto` from `frontend/src/lib/api.ts` (Task 7 + pre-existing); `useAuthStore`, `readAuthTokenCookie` from `frontend/src/lib/authStore`.

`/admin/publicaciones` is confirmed free this session — the prior occupant (the AI photo-drafting tool) was renamed to `/admin/fichas-ia` earlier in this project.

- [ ] **Step 1: Write the failing tests**

```tsx
// frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import PublicacionesPage from '../PublicacionesPage';
import { searchProducts, publishProductsBatch } from '../../../lib/api';

vi.mock('../../../lib/api', () => ({
  searchProducts: vi.fn(),
  publishProductsBatch: vi.fn(),
}));

vi.mock('../../../lib/authStore', () => ({
  useAuthStore: () => ({ token: 't' }),
  readAuthTokenCookie: () => 't',
}));

beforeEach(() => {
  vi.mocked(searchProducts).mockResolvedValue({
    content: [
      {
        id: 'p1',
        name: 'Chaqueta',
        price: { amount: 49990, currency: 'CLP' },
        imageUrl: 'https://img/chaqueta.jpg',
      } as never,
    ],
    totalElements: 1,
    totalPages: 1,
    size: 24,
    number: 0,
  } as never);
  vi.mocked(publishProductsBatch).mockResolvedValue({
    items: [
      { productId: 'p1', platform: 'INSTAGRAM', success: true, publicationId: 'pub-1', errorMessage: null },
      { productId: 'p1', platform: 'FACEBOOK', success: false, publicationId: null, errorMessage: 'Credenciales no configuradas' },
    ],
  } as never);
});

async function selectTheProduct(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByPlaceholderText(/buscar producto/i), 'cha');
  const hit = await screen.findByRole('button', { name: /chaqueta/i });
  await user.click(hit);
}

describe('PublicacionesPage', () => {
  it('interpolates the caption template in the preview', async () => {
    const user = userEvent.setup();
    render(<PublicacionesPage />);
    await selectTheProduct(user);

    expect(await screen.findByText(/chaqueta a solo \$49\.990/i)).toBeInTheDocument();
  });

  it('publishes the batch and renders a mixed result', async () => {
    const user = userEvent.setup();
    render(<PublicacionesPage />);
    await selectTheProduct(user);

    await user.click(screen.getByRole('button', { name: /publicar ahora/i }));

    await waitFor(() => expect(publishProductsBatch).toHaveBeenCalled());
    expect(await screen.findByText(/credenciales no configuradas/i)).toBeInTheDocument();
  });

  it('disables the publish button until a product is selected', () => {
    render(<PublicacionesPage />);
    expect(screen.getByRole('button', { name: /publicar ahora/i })).toBeDisabled();
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/PublicacionesPage.test.tsx`
Expected: FAIL — `PublicacionesPage` doesn't exist yet.

- [ ] **Step 3: Implement `PublicacionesPage.tsx`**

```tsx
import { useEffect, useMemo, useState } from 'react';
import { Loader2, Search, Send } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../../lib/authStore';
import {
  searchProducts,
  publishProductsBatch,
  type ProductDto,
  type PublishProductsBatchItemResult,
} from '../../lib/api';

type Platform = 'INSTAGRAM' | 'FACEBOOK';

const PLATFORM_LABELS: Record<Platform, string> = {
  INSTAGRAM: 'Instagram',
  FACEBOOK: 'Facebook',
};

function formatClp(amount: number): string {
  return new Intl.NumberFormat('es-CL').format(amount);
}

function interpolateCaption(template: string, product: ProductDto): string {
  return template
    .replaceAll('{producto}', product.name)
    .replaceAll('{precio}', `$${formatClp(product.price.amount)}`);
}

export default function PublicacionesPage() {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie() ?? '';

  const [term, setTerm] = useState('');
  const [results, setResults] = useState<ProductDto[]>([]);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState<Map<string, ProductDto>>(new Map());
  const [platforms, setPlatforms] = useState<Set<Platform>>(new Set(['INSTAGRAM', 'FACEBOOK']));
  const [captionTemplate, setCaptionTemplate] = useState(
    '{producto} a solo {precio}. Envios a todo Chile.',
  );
  const [hashtagsInput, setHashtagsInput] = useState('#pilarestilo');
  const [campaignLabel, setCampaignLabel] = useState('');
  const [publishing, setPublishing] = useState(false);
  const [publishResults, setPublishResults] = useState<PublishProductsBatchItemResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const q = term.trim();
    if (q.length < 2) {
      setResults([]);
      return;
    }
    let cancelled = false;
    setSearching(true);
    const id = setTimeout(() => {
      void searchProducts({ q, page: 0, size: 24 }, 0, 24)
        .then((page) => {
          if (!cancelled) setResults(page.content);
        })
        .catch(() => {
          if (!cancelled) setResults([]);
        })
        .finally(() => {
          if (!cancelled) setSearching(false);
        });
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(id);
    };
  }, [term]);

  function toggleProduct(product: ProductDto) {
    setSelected((prev) => {
      const next = new Map(prev);
      if (next.has(product.id)) {
        next.delete(product.id);
      } else {
        next.set(product.id, product);
      }
      return next;
    });
  }

  function togglePlatform(platform: Platform) {
    setPlatforms((prev) => {
      const next = new Set(prev);
      if (next.has(platform)) {
        next.delete(platform);
      } else {
        next.add(platform);
      }
      return next;
    });
  }

  const selectedProducts = useMemo(() => Array.from(selected.values()), [selected]);
  const hashtags = useMemo(
    () =>
      hashtagsInput
        .split(/[\s,]+/)
        .map((tag) => tag.trim())
        .filter((tag) => tag.length > 0),
    [hashtagsInput],
  );

  const canPublish =
    selectedProducts.length > 0 && platforms.size > 0 && captionTemplate.trim().length > 0 && !publishing;

  async function handlePublish() {
    if (!canPublish) return;
    setPublishing(true);
    setError(null);
    setPublishResults(null);
    try {
      const response = await publishProductsBatch(
        {
          productIds: selectedProducts.map((p) => p.id),
          platforms: Array.from(platforms),
          captionTemplate,
          hashtags,
          campaignLabel: campaignLabel.trim() || undefined,
        },
        effectiveToken,
      );
      setPublishResults(response.items);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo publicar el lote.');
    } finally {
      setPublishing(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <section>
        <h2 className="font-sans text-sm text-pe-muted mb-2">1. Elegi los productos</h2>
        <label className="relative block max-w-md">
          <span className="sr-only">Buscar producto</span>
          <Search size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-pe-muted" />
          <input
            type="search"
            value={term}
            onChange={(e) => setTerm(e.target.value)}
            placeholder="Buscar producto por nombre..."
            className="w-full bg-pe-surface border border-pe-border rounded-xs pl-9 pr-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-pe-border"
          />
        </label>

        {searching && (
          <div className="flex items-center gap-2 mt-3 text-pe-muted text-xs">
            <Loader2 size={14} className="animate-spin" /> Buscando...
          </div>
        )}

        {results.length > 0 && (
          <ul className="grid grid-cols-2 sm:grid-cols-4 gap-2 mt-3">
            {results.map((product) => {
              const isSelected = selected.has(product.id);
              return (
                <li key={product.id}>
                  <button
                    type="button"
                    onClick={() => toggleProduct(product)}
                    aria-pressed={isSelected}
                    className={[
                      'group relative block w-full overflow-hidden border text-left transition-colors',
                      isSelected ? 'border-pe-rose' : 'border-pe-border hover:border-pe-rose',
                    ].join(' ')}
                  >
                    <img src={product.imageUrl} alt={product.name} loading="lazy" className="aspect-4/5 w-full object-cover" />
                    <span className="block truncate px-1.5 py-1 font-sans text-[0.66rem]">{product.name}</span>
                    {isSelected && (
                      <span className="absolute top-1 right-1 bg-pe-rose text-pe-white text-[0.6rem] px-1.5 py-0.5 rounded-xs">
                        Elegido
                      </span>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
        )}

        {selectedProducts.length > 0 && (
          <p className="mt-2 text-xs text-pe-muted">{selectedProducts.length} producto(s) elegido(s)</p>
        )}
      </section>

      <section>
        <h2 className="font-sans text-sm text-pe-muted mb-2">2. Plataformas</h2>
        <div className="flex gap-4">
          {(['INSTAGRAM', 'FACEBOOK'] as const).map((platform) => (
            <label key={platform} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={platforms.has(platform)}
                onChange={() => togglePlatform(platform)}
                className="accent-pe-rose w-4 h-4"
              />
              {PLATFORM_LABELS[platform]}
            </label>
          ))}
        </div>
      </section>

      <section>
        <h2 className="font-sans text-sm text-pe-muted mb-2">3. Texto del post</h2>
        <label className="block">
          <span className="text-xs text-pe-muted">
            Plantilla — variables disponibles: <code>{'{producto}'}</code> y <code>{'{precio}'}</code>
          </span>
          <textarea
            value={captionTemplate}
            onChange={(e) => setCaptionTemplate(e.target.value)}
            rows={3}
            className="mt-1 w-full bg-pe-surface border border-pe-border rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-pe-border"
          />
        </label>
        <label className="block mt-3">
          <span className="text-xs text-pe-muted">Hashtags</span>
          <input
            type="text"
            value={hashtagsInput}
            onChange={(e) => setHashtagsInput(e.target.value)}
            className="mt-1 w-full bg-pe-surface border border-pe-border rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-pe-border"
          />
        </label>
        <label className="block mt-3 max-w-xs">
          <span className="text-xs text-pe-muted">Campana (opcional)</span>
          <input
            type="text"
            value={campaignLabel}
            onChange={(e) => setCampaignLabel(e.target.value)}
            placeholder="Liquidacion primavera"
            className="mt-1 w-full bg-pe-surface border border-pe-border rounded-xs px-3 py-2 text-sm outline-hidden focus:ring-1 focus:ring-pe-border"
          />
        </label>
      </section>

      {selectedProducts.length > 0 && (
        <section>
          <h2 className="font-sans text-sm text-pe-muted mb-2">4. Vista previa</h2>
          <ul className="flex flex-col gap-3">
            {selectedProducts.map((product) => (
              <li key={product.id} className="flex gap-3 border border-pe-border p-3">
                <img src={product.imageUrl} alt={product.name} className="w-16 h-20 object-cover flex-shrink-0" />
                <p className="text-sm whitespace-pre-wrap">
                  {interpolateCaption(captionTemplate, product)}
                  {hashtags.length > 0 && (
                    <>
                      {'\n\n'}
                      {hashtags.join(' ')}
                    </>
                  )}
                </p>
              </li>
            ))}
          </ul>
        </section>
      )}

      {error && (
        <p className="text-sm text-pe-danger-ink" role="alert">
          {error}
        </p>
      )}

      <button
        type="button"
        onClick={() => void handlePublish()}
        disabled={!canPublish}
        className="self-start flex items-center gap-2 bg-pe-rose text-pe-white px-4 py-2 rounded-xs text-sm disabled:opacity-50"
      >
        {publishing ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
        Publicar ahora
      </button>

      {publishResults && (
        <section>
          <h2 className="font-sans text-sm text-pe-muted mb-2">Resultado</h2>
          <ul className="flex flex-col gap-1">
            {publishResults.map((item, index) => {
              const product = selected.get(item.productId);
              return (
                <li key={`${item.productId}-${item.platform}-${index}`} className="text-sm flex items-center gap-2">
                  <span aria-hidden="true">{item.success ? '✓' : '✗'}</span>
                  <span>
                    {product ? product.name : item.productId} — {PLATFORM_LABELS[item.platform]}
                    {!item.success && item.errorMessage ? `: ${item.errorMessage}` : ''}
                  </span>
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd frontend && npx vitest run src/islands/admin/__tests__/PublicacionesPage.test.tsx`
Expected: PASS

- [ ] **Step 5: Add the Astro page**

```astro
---
import AdminLayout from '../../layouts/AdminLayout.astro';
import PublicacionesPage from '../../islands/admin/PublicacionesPage';
---

<AdminLayout
  title="Publicaciones"
  breadcrumbs={[{ label: 'Publicaciones' }]}
>
  <div class="mb-5 sm:mb-6">
    <h1 class="font-display text-pe-black text-2xl sm:text-3xl font-light">Publicaciones</h1>
    <p class="font-sans text-[0.78rem] sm:text-sm text-pe-muted mt-1">
      Elegi productos y publicalos en Instagram y Facebook en un solo lote.
    </p>
  </div>

  <PublicacionesPage client:load />
</AdminLayout>
```

- [ ] **Step 6: Add the sidebar entry**

In `AdminSidebar.tsx`, add `Megaphone` to the `lucide-react` import list, and add a new entry to `navItems` immediately after the `fichas-ia` entry:

```typescript
  { href: '/admin/publicaciones', icon: Megaphone, label: 'Publicaciones', viewKey: 'productos' },
```

- [ ] **Step 7: Run the frontend test suite and typecheck**

Run: `cd frontend && npx vitest run`
Expected: PASS

Run: `cd frontend && npx tsc --noEmit`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add frontend/src/islands/admin/PublicacionesPage.tsx \
        frontend/src/pages/admin/publicaciones.astro \
        frontend/src/islands/admin/AdminSidebar.tsx \
        frontend/src/islands/admin/__tests__/PublicacionesPage.test.tsx
git commit -m "feat(frontend): add /admin/publicaciones batch social publishing screen

Multi-product picker, platform toggles, caption template with
{producto}/{precio} variables, interpolated preview, and a mixed
success/failure result list after publishing."
```

---

## After all tasks: manual smoke test note

Testing a real, successful publish end-to-end needs actual Meta credentials in `system_settings`
(via a future settings-panel UI, or set directly in the database for now) and a publicly reachable
`app.social-publishing.meta.public-media-base-url` — `localhost:4321` in local dev is not reachable
by Meta's servers, so this step needs either a tunnel (e.g. ngrok) or the deployed environment. All
8 tasks above are fully covered by automated tests without needing real credentials or network
access; this note is only for the first real-world confirmation that a post actually appears on
Instagram/Facebook.
