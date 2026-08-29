# Self-Service Password Reset — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a self-service "forgot password" flow (request a reset link by email, set a new
password from the link) for every role, and make any password change — self-service or admin-forced
— invalidate every existing session on every device.

**Architecture:** Extends the existing `shared/auth` hexagonal module in the backend monolith. New
`password_reset_tokens` table (hash only) + `users.session_version` column carried as a JWT `sv`
claim and checked on every authenticated request and every refresh. The reset email is sent by a
**self-contained** `PasswordResetEmailSender` in `shared/auth` — its own SMTP send, reading
`system_settings` and decrypting with `SystemSettingsCryptoService` (both stay in the monolith),
with **no dependency on `notification-service`, Kafka, or `system_settings.notification_providers`**
— account recovery must not depend on the notification pipeline being up or on an admin's channel
toggle. Two new frontend pages.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway, jjwt 0.12.6,
`spring-boot-starter-mail` (JavaMailSender), BCrypt. Astro 4 SSR + React islands, Zustand.

**Spec:** `docs/superpowers/specs/2026-08-28-customer-password-reset-design.md` — read it first
(§3 security, §4 testing). This plan resolves the spec's open items and one thing the spec could
not have known: the email infrastructure moved to `services/notification-service/` in the
notification extraction (merged to `develop` 2026-08-29). The decision — self-contained SMTP in
`shared/auth`, not a call into notification-service — is in the Architecture note above.

## Global Constraints

- **All backend code lives in `backend/src/main/java/com/pilarestilo/shared/auth/`** (new pieces)
  and touches `user/` (the `User` aggregate + its JPA entity/adapter) and
  `shared/infrastructure/` (`SecurityConfig`, `ApiGatewayRateLimitFilter`). No new top-level module.
- **Migration is `V91`** — next after `V90` (current highest). If `V91` is taken when you start
  (a parallel branch), use the next free number and keep the two changes (`password_reset_tokens`
  + `users.session_version`) in one file. Never edit an applied migration.
- **The reset email never routes through `notification-service`, Kafka, or
  `system_settings.notification_providers`.** `PasswordResetEmailSender` is self-contained SMTP.
- **`spring-boot-starter-mail` must stay in `backend/pom.xml`** — the notification-extraction plan's
  cleanup task (`2026-08-28-notification-service-extraction.md` Task 16) says "drop it if nothing
  else uses `JavaMailSender`"; this feature is now that something else. Leave a comment on the dep.
- **Enumeration-safe**: `POST /api/auth/forgot-password` returns the identical 200 body whether or
  not the email exists. `POST /api/auth/reset-password` returns one generic error
  (`"El enlace no es válido o ya expiró"`) for a missing, used, or expired token — no external
  signal distinguishes them.
- **Token**: 256 bits from `SecureRandom`, base64url-encoded for the URL; only its SHA-256 hash is
  persisted. 30-minute expiry. Single use (`used_at`). Requesting a new one invalidates the
  previous unused token for that user.
- **Old JWTs without an `sv` claim** (minted before this deploy) are treated as `sv = 1` — they
  stay valid until they expire naturally, exactly as the codebase already does for the legacy
  `permissionCodes` claim. No mass logout on deploy.
- Work on `develop`; verify against the local Docker stack before any push to `master`
  (`verify-in-local-docker-before-pushing` memory). Frequent commits, one per task. Invoke the UI
  skills (`superpowers` frontend / `impeccable` / `ui-ux-pro-max`) before the frontend tasks
  (`code-review-discipline` memory).

---

## File Structure

```
backend/src/main/resources/db/migration/
  V91__password_reset_and_session_version.sql          # CREATE

backend/src/main/java/com/pilarestilo/shared/auth/
  domain/
    model/PasswordResetToken.java                      # CREATE — pure domain
    ports/PasswordResetTokenRepository.java             # CREATE
    ports/PasswordResetMailer.java                      # CREATE — one method
  application/
    usecases/RequestPasswordResetUseCase.java           # CREATE
    usecases/ResetPasswordUseCase.java                  # CREATE
  infrastructure/
    persistence/entities/PasswordResetTokenEntity.java  # CREATE
    persistence/repositories/PasswordResetTokenJpaRepository.java   # CREATE
    persistence/repositories/PasswordResetTokenRepositoryAdapter.java # CREATE
    email/SmtpPasswordResetMailer.java                  # CREATE — self-contained SMTP
    ResetTokenCleanupJob.java                           # CREATE — @Scheduled
    JwtTokenProvider.java                               # MODIFY — sv claim on access + refresh
    JwtAuthenticationFilter.java                        # MODIFY — sv check
    web/AuthController.java                             # MODIFY — 2 endpoints
    web/requests/ForgotPasswordRequest.java             # CREATE
    web/requests/ResetPasswordRequest.java              # CREATE
  application/usecases/RefreshTokenUseCase.java          # MODIFY — sv check + pass sv

backend/src/main/java/com/pilarestilo/user/
  domain/model/User.java                               # MODIFY — sessionVersion
  infrastructure/persistence/entities/UserEntity.java   # MODIFY — session_version column
  infrastructure/persistence/repositories/UserRepositoryAdapter.java  # MODIFY — map it
  application/usecases/AdminResetUserPasswordUseCase.java # MODIFY — increment sv

backend/src/main/java/com/pilarestilo/shared/
  auth/application/usecases/{Login,Register,GoogleLogin}UseCase.java  # MODIFY — pass sv
  infrastructure/bootstrap/SecurityConfig.java          # MODIFY — permitAll the 2 endpoints
  infrastructure/web/ApiGatewayRateLimitFilter.java     # MODIFY — forgot_password policy

frontend/src/
  pages/[locale]/forgot-password/index.astro            # CREATE
  pages/[locale]/reset-password/index.astro             # CREATE
  islands/auth/ForgotPasswordForm.tsx                   # CREATE
  islands/auth/ResetPasswordForm.tsx                    # CREATE
  islands/auth/RegisterPopoverForm.tsx                  # MODIFY — link on the login tab
  islands/admin/AdminLoginForm.tsx                      # MODIFY — link
  lib/api/auth.ts (or wherever login/register clients live)  # MODIFY — 2 functions
```

---

## Task 1: Migration — `password_reset_tokens` + `users.session_version`

**Files:**
- Create: `backend/src/main/resources/db/migration/V91__password_reset_and_session_version.sql`
- Test: covered by every existing `@SpringBootTest` (Flyway runs on boot under `ddl-auto: validate`)

**Interfaces:**
- Produces: table `password_reset_tokens`, column `users.session_version int not null default 1`.

- [ ] **Step 1: Write the migration**

```sql
-- Self-service password reset. Only the token's hash is stored — a leaked table is useless
-- without the raw token, same principle as a password.
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,          -- SHA-256 hex
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at     TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_password_reset_tokens_hash ON password_reset_tokens(token_hash);
CREATE INDEX idx_password_reset_tokens_user_unused
    ON password_reset_tokens(user_id) WHERE used_at IS NULL;

-- Bumped on every password change (self-service or admin-forced). Carried in the JWT as `sv`
-- and checked on every authenticated request, so a reset logs every existing session out.
ALTER TABLE users ADD COLUMN session_version INTEGER NOT NULL DEFAULT 1;
```

- [ ] **Step 2: Run the migration** — `cd backend && mvn -o test -Dtest=<any @SpringBootTest IT, e.g. NotificationsUseTheirOwnDatabaseIT>` → PASS (Flyway applies V91, `validate` is happy).

- [ ] **Step 3: Commit** — `git commit -m "feat(auth): V91 — password_reset_tokens + users.session_version"`

---

## Task 2: `User.sessionVersion` through the aggregate and persistence

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/user/domain/model/User.java`
- Modify: `backend/src/main/java/com/pilarestilo/user/infrastructure/persistence/entities/UserEntity.java`
- Modify: `backend/src/main/java/com/pilarestilo/user/infrastructure/persistence/repositories/UserRepositoryAdapter.java`
- Test: `backend/src/test/java/com/pilarestilo/user/domain/UserSessionVersionTest.java`

**Interfaces:**
- Produces: `User.getSessionVersion()` → `int` (≥ 1), `User.incrementSessionVersion()`,
  `User.setSessionVersion(int)` (rehydration only, guards ≥ 1). Field defaults to `1` on
  `User.create(...)`. `UserEntity.session_version` column mapped both directions.

- [ ] **Step 1: Write the failing test**

```java
// UserSessionVersionTest.java
@Test
void a_new_user_starts_at_session_version_1() {
    User u = User.create("a@b.com", "A B", UserRole.CUSTOMER, "hash");
    assertThat(u.getSessionVersion()).isEqualTo(1);
}

@Test
void incrementing_bumps_it() {
    User u = User.create("a@b.com", "A B", UserRole.CUSTOMER, "hash");
    u.incrementSessionVersion();
    u.incrementSessionVersion();
    assertThat(u.getSessionVersion()).isEqualTo(3);
}

@Test
void restore_rejects_below_1() {
    User u = User.create("a@b.com", "A B", UserRole.CUSTOMER, "hash");
    assertThatThrownBy(() -> u.setSessionVersion(0)).isInstanceOf(DomainException.class);
}
```

- [ ] **Step 2: Run it, confirm it fails** — `cd backend && mvn -o test -Dtest=UserSessionVersionTest`.

- [ ] **Step 3: `User` model** — add the field and methods:

```java
private int sessionVersion = 1;
// ...
public int getSessionVersion() { return sessionVersion; }

/** Bumped on every password change — invalidates every JWT issued before this. */
public void incrementSessionVersion() { this.sessionVersion++; }

/** Rehydration only. */
public void setSessionVersion(int sessionVersion) {
    if (sessionVersion < 1) {
        throw new DomainException("User session version must be at least 1");
    }
    this.sessionVersion = sessionVersion;
}
```

- [ ] **Step 4: `UserEntity`** — add:

```java
@Column(name = "session_version", nullable = false)
private int sessionVersion = 1;

public int getSessionVersion() { return sessionVersion; }
public void setSessionVersion(int sessionVersion) { this.sessionVersion = sessionVersion; }
```

- [ ] **Step 5: `UserRepositoryAdapter`** — in `toEntity(...)` add
  `entity.setSessionVersion(user.getSessionVersion());`; in `toDomain(...)`, after the
  `User.reconstruct(...)` call, add `user.setSessionVersion(entity.getSessionVersion());`.

- [ ] **Step 6: Run** — `cd backend && mvn -o test -Dtest='UserSessionVersionTest,*UserRepository*,*User*'` → PASS.

- [ ] **Step 7: Commit** — `git commit -m "feat(user): User.sessionVersion, mapped through the JPA adapter"`

---

## Task 3: `sv` claim on access + refresh tokens

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/JwtTokenProvider.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/JwtTokenProviderSvTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `generateAccessToken(UUID, String, UserRole, List<String>, List<String>, int sessionVersion)`
  (6-arg — a `sv` claim added to the existing 5-arg overload's body; keep the 5-arg one delegating
  with `sessionVersion = 1` so existing tests compile), and
  `generateRefreshToken(UUID, int sessionVersion)` (2-arg — `sv` claim; keep the 1-arg delegating
  with `1`). `parseToken(...)` unchanged — callers read `claims.get("sv", Integer.class)`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void access_token_carries_the_session_version() {
    var p = new JwtTokenProvider("dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=");
    String t = p.generateAccessToken(UUID.randomUUID(), "a@b.com", UserRole.CUSTOMER,
            List.of(), List.of(), 4);
    assertThat(p.parseToken(t).get("sv", Integer.class)).isEqualTo(4);
}

@Test
void refresh_token_carries_the_session_version() {
    var p = new JwtTokenProvider("dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=");
    String t = p.generateRefreshToken(UUID.randomUUID(), 4);
    assertThat(p.parseToken(t).get("sv", Integer.class)).isEqualTo(4);
}
```

- [ ] **Step 2: Run it, confirm it fails** — `mvn -o test -Dtest=JwtTokenProviderSvTest`.

- [ ] **Step 3: Add the claim.** In the 5-arg `generateAccessToken`, add a 6th param
  `int sessionVersion` and `.claim("sv", sessionVersion)` to the builder. Keep the old 5-arg
  signature as `return generateAccessToken(userId, email, role, permissions, permissionCodes, 1);`.
  Same for `generateRefreshToken`: add `int sessionVersion`, `.claim("sv", sessionVersion)`, keep
  the 1-arg delegating with `1`.

- [ ] **Step 4: Run** — `mvn -o test -Dtest='JwtTokenProviderSvTest,*JwtToken*'` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(auth): sv claim on access and refresh JWTs"`

---

## Task 4: `JwtAuthenticationFilter` and `RefreshTokenUseCase` reject a stale `sv`

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/infrastructure/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/application/usecases/RefreshTokenUseCase.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/JwtAuthenticationFilterSvTest.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/application/RefreshTokenUseCaseSvTest.java`

**Interfaces:**
- Consumes: `User.getSessionVersion()` (Task 2), `claims.get("sv", Integer.class)` (Task 3).
- Produces: an authenticated request whose token's `sv` (or `1` if absent) does not equal the
  user's current `session_version` → no `SecurityContext` set → the request answers 403 (this app
  wires no 401 entry point, so an unauthenticated request to a guarded route is 403), the same as
  an expired token. `RefreshTokenUseCase.execute(...)` → `DomainException` on the same mismatch.

- [ ] **Step 1: Write the failing tests**

```java
// JwtAuthenticationFilterSvTest.java — @WebMvcTest style or a direct filter unit test with a
// mock UserRepository. Key case:
@Test
void a_token_with_a_stale_sv_is_not_authenticated() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(userWithSessionVersion(3)));
    Claims claims = claimsWith(userId, "sv", 2);   // token minted at version 2, user now at 3
    filter.authenticateForTest(claims, request);   // extract a package-visible seam, or drive via doFilter
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
}

@Test
void a_token_with_no_sv_claim_is_treated_as_version_1() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(userWithSessionVersion(1)));
    Claims claims = claimsWith(userId /* no sv */);
    filter.authenticateForTest(claims, request);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
}
```

```java
// RefreshTokenUseCaseSvTest.java
@Test
void a_refresh_token_from_before_a_reset_is_rejected() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(userWithSessionVersion(3)));
    String stale = jwtTokenProvider.generateRefreshToken(userId, 2);
    assertThatThrownBy(() -> useCase.execute(stale)).isInstanceOf(DomainException.class);
}
```

- [ ] **Step 2: Run them, confirm they fail** —
  `mvn -o test -Dtest='JwtAuthenticationFilterSvTest,RefreshTokenUseCaseSvTest'`.

- [ ] **Step 3: `JwtAuthenticationFilter.authenticate(...)`** — right after the
  `if (user == null || !user.isActive()) return;` guard, add:

```java
Integer tokenSv = claims.get("sv", Integer.class);
int effectiveSv = tokenSv != null ? tokenSv : 1;
if (effectiveSv != user.getSessionVersion()) {
    return;   // token from before a password change — same outcome as an expired token
}
```

- [ ] **Step 4: `RefreshTokenUseCase`** — it already re-reads the user for fresh role/permissions.
  Parse the refresh token, and after loading the user add the same check:

```java
Integer tokenSv = claims.get("sv", Integer.class);   // claims from the parsed refresh token
int effectiveSv = tokenSv != null ? tokenSv : 1;
if (effectiveSv != user.getSessionVersion()) {
    throw new DomainException("Session no longer valid");
}
```
  Then mint the new access token with the **current** `user.getSessionVersion()` (Task 5 wiring).

- [ ] **Step 5: Run** — `mvn -o test -Dtest='*Jwt*,*Refresh*,*Auth*'` → PASS.

- [ ] **Step 6: Commit** — `git commit -m "feat(auth): reject a JWT or refresh token whose sv is behind the user's session_version"`

---

## Task 5: Pass `sessionVersion` from every token-minting use case

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/application/usecases/LoginUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/application/usecases/RegisterUseCase.java`
- Modify: `backend/src/main/java/com/pilarestilo/shared/auth/application/usecases/GoogleLoginUseCase.java`
- Modify: `RefreshTokenUseCase.java` (the mint call, continuing Task 4)
- Test: extend the existing `LoginUseCaseTest` / `RegisterUseCaseTest` if present; else a small
  assertion that the issued access token's `sv` equals the user's.

**Interfaces:**
- Consumes: the 6-arg `generateAccessToken` and 2-arg `generateRefreshToken` (Task 3),
  `User.getSessionVersion()` (Task 2).
- Produces: every freshly issued access **and** refresh token carries the user's current
  `session_version`.

- [ ] **Step 1: Extend a test** — in `LoginUseCaseTest` (or a new `LoginUseCaseSvTest`): a user at
  `session_version = 2` logs in → `jwtTokenProvider.parseToken(dto.accessToken()).get("sv") == 2`.

- [ ] **Step 2: Run it, confirm it fails.**

- [ ] **Step 3: Update the 4 mint sites** — each already holds the `User`; change
  `generateAccessToken(id, email, role, legacyViewKeys, permissionCodes)` →
  `generateAccessToken(id, email, role, legacyViewKeys, permissionCodes, user.getSessionVersion())`
  and `generateRefreshToken(user.getId())` → `generateRefreshToken(user.getId(), user.getSessionVersion())`.
  In `GoogleLoginUseCase`, a first-time Google sign-in that creates the user gets `sessionVersion = 1`
  from `User.create` — nothing special.

- [ ] **Step 4: Run** — `mvn -o test -Dtest='*Login*,*Register*,*Google*,*Refresh*'` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(auth): issue every token with the user's session_version"`

---

## Task 6: `PasswordResetToken` domain + persistence

**Files:**
- Create: `shared/auth/domain/model/PasswordResetToken.java`
- Create: `shared/auth/domain/ports/PasswordResetTokenRepository.java`
- Create: `shared/auth/infrastructure/persistence/entities/PasswordResetTokenEntity.java`
- Create: `shared/auth/infrastructure/persistence/repositories/PasswordResetTokenJpaRepository.java`
- Create: `shared/auth/infrastructure/persistence/repositories/PasswordResetTokenRepositoryAdapter.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/domain/PasswordResetTokenTest.java`

**Interfaces:**
- Produces:
  - `PasswordResetToken` (pure domain): `static PasswordResetToken issue(UUID userId, String
    tokenHash, Duration ttl)` → sets `id = randomUUID()`, `expiresAt = now + ttl`, `createdAt =
    now`, `usedAt = null`. `boolean isUsable(Instant now)` → `usedAt == null && now.isBefore(expiresAt)`.
    `void markUsed(Instant now)`. Getters: `id`, `userId`, `tokenHash`, `expiresAt`, `usedAt`, `createdAt`.
  - `PasswordResetTokenRepository` port:
    ```java
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    void invalidateUnusedForUser(UUID userId);        // sets used_at = now on all unused rows for the user
    int deleteExpiredBefore(Instant cutoff);          // for the cleanup job
    ```
  - `PasswordResetTokenEntity` maps table `password_reset_tokens` (all columns from V91).

- [ ] **Step 1: Write the failing domain test**

```java
@Test
void a_freshly_issued_token_is_usable_and_becomes_unusable_when_marked_used() {
    var t = PasswordResetToken.issue(UUID.randomUUID(), "abc123", Duration.ofMinutes(30));
    Instant now = Instant.now();
    assertThat(t.isUsable(now)).isTrue();
    t.markUsed(now);
    assertThat(t.isUsable(now)).isFalse();
}

@Test
void an_expired_token_is_not_usable() {
    var t = PasswordResetToken.issue(UUID.randomUUID(), "abc123", Duration.ofMinutes(-1));
    assertThat(t.isUsable(Instant.now())).isFalse();
}
```

- [ ] **Step 2: Run it, confirm it fails.**

- [ ] **Step 3: Write `PasswordResetToken`** (pure Java, no framework), the port interface, the JPA
  entity (`@Entity @Table(name = "password_reset_tokens")`, `@Id UUID id`, columns per V91), the
  Spring Data `PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, UUID>`
  with:
  ```java
  Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);
  @Modifying @Query("UPDATE PasswordResetTokenEntity t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
  void invalidateUnusedForUser(@Param("userId") UUID userId, @Param("now") Instant now);
  @Modifying @Query("DELETE FROM PasswordResetTokenEntity t WHERE t.expiresAt < :cutoff")
  int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
  ```
  and the `@Component PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository`
  doing entity↔domain mapping. Writes are `@Transactional`.

- [ ] **Step 4: Run** — `mvn -o test -Dtest='PasswordResetTokenTest'` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(auth): PasswordResetToken domain + hash-only persistence"`

---

## Task 7: `RequestPasswordResetUseCase`

**Files:**
- Create: `shared/auth/application/usecases/RequestPasswordResetUseCase.java`
- Create: `shared/auth/domain/ports/PasswordResetMailer.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/application/RequestPasswordResetUseCaseTest.java`

**Interfaces:**
- Consumes: `UserRepository.findByEmail(String)`, `PasswordResetTokenRepository` (Task 6).
- Produces:
  - `PasswordResetMailer` port: `void sendResetLink(String toEmail, String fullName, String rawToken)`.
  - `RequestPasswordResetUseCase.execute(String email)` → **void** (the controller always returns
    the same body). Behaviour: lower-case/trim the email; `userRepository.findByEmail(...)`; **if
    absent, return immediately — nothing created, nothing sent**; if present:
    `tokenRepository.invalidateUnusedForUser(user.id)`, generate 32 random bytes via `SecureRandom`,
    base64url-encode (no padding) as the `rawToken`, `sha256Hex(rawToken)` as the hash,
    `tokenRepository.save(PasswordResetToken.issue(user.id, hash, Duration.ofMinutes(30)))`,
    `mailer.sendResetLink(user.email, user.fullName, rawToken)`. A mailer exception is caught and
    logged at WARN — a broken SMTP host must not turn a 200 into a 500 and leak "this email exists".
  - `sha256Hex` helper: `MessageDigest.getInstance("SHA-256")` → `HexFormat.of().formatHex(...)`.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void an_existing_email_gets_a_token_and_an_email() {
    when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
    useCase.execute("A@B.com");
    verify(tokenRepository).invalidateUnusedForUser(user.getId());
    verify(tokenRepository).save(argThat(t -> t.getUserId().equals(user.getId())
            && t.getTokenHash().length() == 64));
    verify(mailer).sendResetLink(eq("a@b.com"), any(), anyString());
}

@Test
void an_unknown_email_creates_and_sends_nothing() {
    when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
    useCase.execute("ghost@nowhere.com");
    verifyNoInteractions(tokenRepository, mailer);
}

@Test
void a_dead_smtp_host_does_not_blow_up_the_request() {
    when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
    doThrow(new RuntimeException("smtp down")).when(mailer).sendResetLink(any(), any(), any());
    assertThatCode(() -> useCase.execute("a@b.com")).doesNotThrowAnyException();
}
```

- [ ] **Step 2: Run, confirm they fail.**

- [ ] **Step 3: Write the port + use case** per the Interfaces block.

- [ ] **Step 4: Run** — `mvn -o test -Dtest=RequestPasswordResetUseCaseTest` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(auth): RequestPasswordResetUseCase — enumeration-safe, one live token"`

---

## Task 8: `ResetPasswordUseCase`

**Files:**
- Create: `shared/auth/application/usecases/ResetPasswordUseCase.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/application/ResetPasswordUseCaseTest.java`

**Interfaces:**
- Consumes: `PasswordResetTokenRepository`, `UserRepository`, `PasswordEncoder`
  (`shared.auth.domain.ports.PasswordEncoder`).
- Produces: `ResetPasswordUseCase.execute(String rawToken, String newPassword)` → **void**.
  `@Transactional`. Behaviour: `hash = sha256Hex(rawToken)`;
  `tokenRepository.findByTokenHash(hash)`; if empty **or** `!token.isUsable(now())` →
  `throw new DomainException("El enlace no es válido o ya expiró")` (one message, all three cases);
  else `userRepository.findById(token.userId)` (must exist — cascade guarantees it),
  `user.changePasswordHash(passwordEncoder.encode(newPassword))`, `user.incrementSessionVersion()`,
  `userRepository.save(user)`, `token.markUsed(now())`, `tokenRepository.save(token)`.
  A minimum-length guard on `newPassword` (reuse whatever `ChangeMyPasswordUseCase` /
  `RegisterUseCase` already enforce — check and match; if 8, keep 8) throwing the same
  `DomainException` shape.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void a_valid_token_changes_the_password_bumps_sv_and_marks_the_token_used() {
    var token = PasswordResetToken.issue(userId, sha256Hex("raw"), Duration.ofMinutes(30));
    when(tokenRepository.findByTokenHash(sha256Hex("raw"))).thenReturn(Optional.of(token));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));  // user at sv=1
    when(passwordEncoder.encode("NewPass123")).thenReturn("hashed");

    useCase.execute("raw", "NewPass123");

    verify(user).changePasswordHash("hashed");
    verify(user).incrementSessionVersion();
    verify(tokenRepository).save(argThat(t -> t.getUsedAt() != null));
}

@Test
void a_used_token_fails_with_the_generic_error() {
    var token = PasswordResetToken.issue(userId, sha256Hex("raw"), Duration.ofMinutes(30));
    token.markUsed(Instant.now());
    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
    assertThatThrownBy(() -> useCase.execute("raw", "NewPass123"))
            .isInstanceOf(DomainException.class)
            .hasMessage("El enlace no es válido o ya expiró");
}

@Test
void an_expired_token_fails_with_the_same_error() { /* Duration.ofMinutes(-1) */ }

@Test
void an_unknown_token_fails_with_the_same_error() {
    when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute("nope", "NewPass123"))
            .hasMessage("El enlace no es válido o ya expiró");
}
```
  (Use a real `User` instance where `verify(user)` is not needed; a Mockito mock where it is — or
  assert on `user.getSessionVersion()` / `user.getPasswordHash()` with a real instance.)

- [ ] **Step 2: Run, confirm they fail.**

- [ ] **Step 3: Write the use case.**

- [ ] **Step 4: Run** — `mvn -o test -Dtest=ResetPasswordUseCaseTest` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(auth): ResetPasswordUseCase — single-use token, generic errors, sv bump"`

---

## Task 9: `SmtpPasswordResetMailer` — self-contained SMTP

**Files:**
- Create: `shared/auth/infrastructure/email/SmtpPasswordResetMailer.java`
- Modify: `backend/src/main/resources/application.yml` — `app.password-reset.*` keys
- Modify: `backend/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- Modify: `backend/pom.xml` — comment on `spring-boot-starter-mail`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/email/SmtpPasswordResetMailerTest.java`

**Interfaces:**
- Consumes: `com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository`,
  `com.pilarestilo.systemsettings.infrastructure.security.SystemSettingsCryptoService` — both
  **remain in the monolith** (used by the payment gateway and media secrets). Read `settings.get()`
  → `getSmtpHost()`, `getSmtpPort()`, `getSmtpUsername()`, `getSmtpPasswordEncrypted()` (decrypt
  with the crypto service), `getSmtpFromEmail()`, `isSmtpAuthEnabled()`, `isSmtpStarttlsEnabled()`.
  Env fallbacks `EMAIL_SMTP_*` (same names the notification service and the pre-extraction monolith
  used). `@Value("${app.password-reset.link-base-url:http://localhost:4321}")` and
  `${app.password-reset.token-ttl-minutes:30}` (the TTL is read here only for the email copy —
  Task 7 hard-codes 30 in `Duration.ofMinutes(30)`; keep them in sync or read the property in both).
- Produces: `@Component SmtpPasswordResetMailer implements PasswordResetMailer`. `sendResetLink`
  builds a `JavaMailSenderImpl` from the resolved config (same shape as the notification service's
  `SmtpEmailNotificationSender.buildSender`, ported minimally — no `EmailLayout`, no logo), and
  sends a multipart message: plain text + a small inline-styled HTML, subject
  `"Restablece tu contraseña — Pilar Estilo"`, body containing
  `{link-base-url}/es/reset-password?token={rawToken}` and the "expires in 30 minutes, ignore if it
  wasn't you" lines. If the SMTP config does not resolve (no host / bad port / bad from-address),
  log WARN and return — the caller (Task 7) already treats a mailer failure as non-fatal.

- [ ] **Step 1: Write the failing test** — a `@SuppressWarnings` package-private `buildSender(...)`
  seam like the notification service's, plus a recording `JavaMailSenderImpl` subclass:

```java
@Test
void sends_a_message_containing_the_reset_link_when_smtp_is_configured() {
    when(settings.get()).thenReturn(settingsWithSmtp("smtp.example.com", 587, "envios@pilarestilo.com"));
    var mailer = mailerThatRecords();
    mailer.sendResetLink("cliente@example.com", "Camila", "TOK-123");
    assertThat(recordedMime()).contains("reset-password?token=TOK-123").contains("30 minutos");
}

@Test
void is_a_no_op_when_smtp_is_not_configured() {
    when(settings.get()).thenReturn(MessagingSettingsWithNoSmtp);
    assertThatCode(() -> mailer.sendResetLink("c@e.com", "C", "T")).doesNotThrowAnyException();
}
```

- [ ] **Step 2: Run, confirm they fail.**

- [ ] **Step 3: Write `SmtpPasswordResetMailer`** — port the config-resolution + `buildSender`
  from `services/notification-service/.../SmtpEmailNotificationSender` (it is the reference; do not
  add a compile dependency on it — copy the ~40 relevant lines). Compose the message inline.

- [ ] **Step 4: `application.yml`** — under `app:`:
  ```yaml
  password-reset:
    link-base-url: ${APP_PASSWORD_RESET_LINK_BASE_URL:http://localhost:4321}
    token-ttl-minutes: ${APP_PASSWORD_RESET_TOKEN_TTL_MINUTES:30}
  ```
  Add both to `additional-spring-configuration-metadata.json` with one-line descriptions.

- [ ] **Step 5: `pom.xml`** — change the `spring-boot-starter-mail` `<dependency>` comment to:
  `<!-- JavaMailSender: SmtpPasswordResetMailer (shared/auth). Keep even after the notification
  module is deleted. -->`

- [ ] **Step 6: Run** — `mvn -o test -Dtest=SmtpPasswordResetMailerTest` → PASS.

- [ ] **Step 7: Commit** — `git commit -m "feat(auth): SmtpPasswordResetMailer — self-contained SMTP, no notification dependency"`

---

## Task 10: `AuthController` endpoints + `SecurityConfig` + rate limit

**Files:**
- Modify: `shared/auth/infrastructure/web/AuthController.java`
- Create: `shared/auth/infrastructure/web/requests/ForgotPasswordRequest.java`
- Create: `shared/auth/infrastructure/web/requests/ResetPasswordRequest.java`
- Modify: `shared/infrastructure/bootstrap/SecurityConfig.java`
- Modify: `shared/infrastructure/web/ApiGatewayRateLimitFilter.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/web/PasswordResetControllerIT.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/infrastructure/web/ApiGatewayRateLimitFilterForgotPasswordTest.java`

**Interfaces:**
- Consumes: `RequestPasswordResetUseCase`, `ResetPasswordUseCase`.
- Produces:
  - `ForgotPasswordRequest(@NotBlank @Email String email)`.
  - `ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword)`.
  - `POST /api/auth/forgot-password` → `@ResponseStatus(HttpStatus.OK)`, body
    `Map.of("message", "Si el correo existe, te enviamos un enlace para restablecer tu contraseña.")`
    — the **same** body regardless. Calls `requestPasswordResetUseCase.execute(req.email())`.
  - `POST /api/auth/reset-password` → `@ResponseStatus(HttpStatus.NO_CONTENT)`, calls
    `resetPasswordUseCase.execute(req.token(), req.newPassword())`. A `DomainException` from the use
    case is rendered by the existing global exception handler (check it maps `DomainException` →
    400 with the message; it does for `login`).
  - `SecurityConfig`: two lines after the `/api/auth/google` matcher —
    `.requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()` and
    `.requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()`.
  - `ApiGatewayRateLimitFilter`: a `@Value("${app.gateway.rate-limit.forgot-password-max-requests:5}")
    int forgotPasswordMaxRequests` field (with `Math.max(1, …)`), and in `resolvePolicy` a case
    `if ("/api/auth/forgot-password".equals(path)) return new RateLimitPolicy("forgot_password", forgotPasswordMaxRequests);`.

- [ ] **Step 1: Write the failing tests**

```java
// PasswordResetControllerIT — @SpringBootTest + @AutoConfigureMockMvc + Testcontainers (a real
// user seeded). Uses a @MockitoBean PasswordResetMailer so no SMTP is touched.
@Test
void forgot_password_returns_200_with_the_same_body_for_known_and_unknown_emails() throws Exception {
    for (String email : List.of("test_estilo@pilarestilo.com", "ghost@nowhere.invalid")) {
        mockMvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(SAME_MESSAGE));
    }
}

@Test
void the_full_flow_changes_the_password_and_invalidates_old_tokens() throws Exception {
    mockMvc.perform(post("/api/auth/forgot-password") ... known email ... ).andExpect(status().isOk());
    var token = passwordResetTokenJpaRepository.findAll().get(0);   // read the hash row
    String raw = capturedRawToken();                                // from the mock mailer's argument
    mockMvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON)
                    .content("{\"token\":\"" + raw + "\",\"newPassword\":\"BrandNew123\"}"))
            .andExpect(status().isNoContent());
    // old access token (sv=1) now 403 on /api/auth/me; a fresh login with BrandNew123 works
}

@Test
void reset_password_with_a_garbage_token_is_a_generic_400() throws Exception {
    mockMvc.perform(post("/api/auth/reset-password").contentType(APPLICATION_JSON)
                    .content("{\"token\":\"nope\",\"newPassword\":\"BrandNew123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("El enlace no es válido o ya expiró"));
}
```

```java
// ApiGatewayRateLimitFilterForgotPasswordTest — drive the filter directly, 6 POSTs from one IP,
// assert the 6th gets 429.
```

- [ ] **Step 2: Run, confirm they fail.**

- [ ] **Step 3: Implement** the requests, the two controller methods, the `SecurityConfig` lines,
  the rate-limit policy + `@Value`.

- [ ] **Step 4: `.env.example` + `docker-compose.yml`** — add
  `APP_PASSWORD_RESET_LINK_BASE_URL` (compose: `https://${DOMAIN:-localhost}`) and
  `APP_GATEWAY_RATE_LIMIT_FORGOT_PASSWORD_MAX_REQUESTS=5` to the backend service block and
  `.env.example`.

- [ ] **Step 5: Run** — `mvn -o test -Dtest='PasswordResetControllerIT,ApiGatewayRateLimitFilterForgotPasswordTest,*SecurityConfig*'` → PASS.

- [ ] **Step 6: Commit** — `git commit -m "feat(auth): POST /api/auth/forgot-password + /reset-password, rate-limited"`

---

## Task 11: Admin-forced reset also bumps `session_version`

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/user/application/usecases/AdminResetUserPasswordUseCase.java`
- Test: `backend/src/test/java/com/pilarestilo/user/application/AdminResetUserPasswordUseCaseTest.java`

**Interfaces:**
- Produces: after `user.changePasswordHash(...)` and before `userRepository.save(user)`, add
  `user.incrementSessionVersion();` — an admin resetting a compromised worker's password logs that
  worker's stolen sessions out too, same as self-service.

- [ ] **Step 1: Write the failing test** — mock/real `User` at `sv=1`, call `execute(userId, "new")`,
  assert `user.getSessionVersion() == 2` and `passwordEncoder.encode` was called.

- [ ] **Step 2: Run, confirm it fails.**

- [ ] **Step 3: Add the one line.**

- [ ] **Step 4: Run** — `mvn -o test -Dtest='AdminResetUserPasswordUseCaseTest,*UserEditDrawer*,*AdminReset*'` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(user): admin-forced password reset invalidates the worker's sessions too"`

---

## Task 12: Expired-token cleanup job

**Files:**
- Create: `shared/auth/infrastructure/ResetTokenCleanupJob.java`
- Test: `backend/src/test/java/com/pilarestilo/shared/auth/infrastructure/ResetTokenCleanupJobTest.java`

**Interfaces:**
- Consumes: `PasswordResetTokenRepository.deleteExpiredBefore(Instant)` (Task 6).
- Produces: `@Component ResetTokenCleanupJob` with
  `@Scheduled(cron = "${app.password-reset.cleanup-cron:0 30 3 * * *}")` (daily 03:30) calling
  `repository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)))` and logging the count.
  `@EnableScheduling` — check it is already on (the product-AI worker and the bank-transfer
  auto-cancel both use `@Scheduled`, so it is); if not, add it to a config class.

- [ ] **Step 1: Write the failing test** — mock repository, call `job.run()` (extract the body into
  a package-visible method), verify `deleteExpiredBefore` called with a cutoff ~24h in the past.

- [ ] **Step 2: Run, confirm it fails.**

- [ ] **Step 3: Write the job** + the `app.password-reset.cleanup-cron` metadata entry.

- [ ] **Step 4: Run** — `mvn -o test -Dtest=ResetTokenCleanupJobTest` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(auth): daily cleanup of expired password-reset tokens"`

---

## Task 13: Backend full-suite gate

**Files:** none — verification only.

- [ ] **Step 1: Stop the local compose stack** if running (Testcontainers fights it —
  `verify-in-local-docker-before-pushing` memory): `cd infra && docker compose --env-file .env
  --profile kafka --profile cache --profile microservices --profile observability --profile tracing down`.

- [ ] **Step 2: `cd backend && mvn -o clean verify`** → BUILD SUCCESS. This is the first run of the
  whole backend suite against the new schema + the `sv` filter change — the blast radius of Tasks
  3–5 is every authenticated test.

- [ ] **Step 3:** if any pre-existing auth/security test fails because it now needs an `sv` claim or
  a `session_version` on a hand-built `UserEntity`, fix that test in this task (it is a real
  breakage of a shared contract, not new behaviour). Commit as
  `test: carry sv through the auth test fixtures`.

---

## Task 14: Frontend — the two pages and the links

**REQUIRED SUB-SKILL:** invoke `superpowers` frontend guidance / `impeccable` before writing UI
(`code-review-discipline` memory).

**Files:**
- Create: `frontend/src/pages/[locale]/forgot-password/index.astro`
- Create: `frontend/src/pages/[locale]/reset-password/index.astro`
- Create: `frontend/src/islands/auth/ForgotPasswordForm.tsx`
- Create: `frontend/src/islands/auth/ResetPasswordForm.tsx`
- Modify: `frontend/src/islands/auth/RegisterPopoverForm.tsx` (login tab) — add the link
- Modify: `frontend/src/islands/admin/AdminLoginForm.tsx` — add the link
- Modify: the auth API client module (where `login`/`register` live — grep
  `POST.*/auth/login` under `frontend/src`) — add `forgotPassword(email)` and
  `resetPassword(token, newPassword)`
- Test: `frontend/e2e/` — extend or add a spec exercising both pages via the API + a DOM smoke check

**Interfaces:**
- Consumes: `POST /api/auth/forgot-password` `{ email }` → 200 `{ message }`;
  `POST /api/auth/reset-password` `{ token, newPassword }` → 204 or 400 `{ message }`.
- Produces:
  - `/{locale}/forgot-password` — full page, same split-editorial layout as the existing
    unauthenticated login screen (`pages/[locale]/account/index.astro` when not signed in). One
    email field, submit → show the generic "revisa tu correo" confirmation inline (never reveal
    whether the address existed), a "volver a iniciar sesión" link. es + en copy.
  - `/{locale}/reset-password` — reads `token` from `Astro.url.searchParams` (SSR) / the island
    reads `window.location.search`. New-password + confirm-password fields, client-side match check
    + the same min length the backend enforces, submit → on 204 redirect to the login screen with a
    "contraseña actualizada, inicia sesión" flash; on 400 show the generic
    `"El enlace no es válido o ya expiró"` and a link back to `/forgot-password`. If `token` is
    missing entirely, render that same error state without calling the API.
  - `RegisterPopoverForm.tsx` login tab and `AdminLoginForm.tsx`: a small
    `"¿Olvidaste tu contraseña?"` / `"Forgot your password?"` link to
    `/{locale}/forgot-password` (admin form → `/es/forgot-password`, staff share the flow).

- [ ] **Step 1: API client** — add the two functions next to `login`/`register`, same fetch
  wrapper, same error shape.

- [ ] **Step 2: `ForgotPasswordForm.tsx` + `ResetPasswordForm.tsx`** — React islands, Zustand only
  if a store already exists for auth flashes; otherwise local state. `tsc` clean.

- [ ] **Step 3: The two `.astro` pages** — copy the unauthenticated login screen's layout wrapper,
  hydrate the island `client:load`.

- [ ] **Step 4: The two links.**

- [ ] **Step 5: `cd frontend && npm run build && npx tsc --noEmit`** → clean. Run the full vitest
  suite → green.

- [ ] **Step 6: e2e** — a Playwright spec: request a reset for the fixed test customer, read the
  token from the backend (`GET` is not exposed — read it from the DB via a small psql `docker exec`,
  or expose the raw token only in a `test` Spring profile — prefer the psql read, no prod surface),
  open `/es/reset-password?token=...`, submit a new password, assert redirect to login and that the
  new password logs in while the old one 403s.

- [ ] **Step 7: Commit** — `git commit -m "feat(frontend): forgot-password + reset-password pages and login links"`

---

## Task 15: Full-stack real verification

**Not code — the spec's "pre-deploy real verification" bar. Do not merge to `master` without it.**

- [ ] **Step 1:** bring up the full stack (`cd infra && docker compose --env-file .env --profile
  kafka --profile cache --profile microservices --profile observability up -d --build`), with
  `NOTIFICATION_PROVIDER` irrelevant here and `EMAIL_SMTP_*` pointed at a real inbox you control
  (or the admin panel SMTP settings filled in).

- [ ] **Step 2:** from the storefront, click "¿Olvidaste tu contraseña?" as the fixed test customer
  `test_estilo@pilarestilo.com`, submit.

- [ ] **Step 3:** confirm the email **arrives** (real inbox / MailHog / the SMTP server's log),
  the link points at `/es/reset-password?token=...`, and opening it + setting a new password
  redirects to login.

- [ ] **Step 4:** confirm the **old** session is dead: in a second browser tab still "logged in" as
  that customer from before the reset, the next action → **403** (this app has no 401 entry point;
  same status as an expired token) / bounced to login. And a **new** login with the new password
  works.

- [ ] **Step 5:** confirm enumeration safety by eye: `forgot-password` for a made-up address returns
  the same screen, and the SMTP server shows no send.

- [ ] **Step 6:** record the result in the PR description; update the `customer-password-reset-design`
  memory. Merge to `master`.

---

## Self-Review

**Spec coverage:**
- §1 migration (`password_reset_tokens` + `session_version`) → Task 1. ✅
- §1 `RequestPasswordResetUseCase` → Task 7; `ResetPasswordUseCase` → Task 8. ✅
- §1 `PasswordResetNotifier` bypassing the notification pipeline → Task 9 (`SmtpPasswordResetMailer`,
  self-contained — and the Architecture note explains why it is not a call into notification-service,
  which the spec could not have anticipated). ✅
- §1 `AuthController` 2 endpoints + `SecurityConfig` permitAll → Task 10. ✅
- §1 `ApiGatewayRateLimitFilter` new policy → Task 10. ✅
- §1 `JwtTokenProvider` `sv` claim → Task 3; `JwtAuthenticationFilter` check → Task 4; every mint
  site → Task 5. ✅
- §1 `UserEditDrawer` admin-forced reset bumps `sv` → Task 11. ✅
- §1 frontend pages + links → Task 14. ✅
- §2 data flow — all three flows realised across Tasks 7, 8, 4. ✅
- §3 token 256-bit / hash-only / single-use / prior-token-invalidation / generic errors / rate
  limit / session invalidation → Tasks 6, 7, 8, 10. ✅
- §3 expired-token cleanup → Task 12 (scheduled job — the spec left job-vs-lazy open; scheduled
  chosen, cheapest and out of the request path). ✅
- §4 unit tests → Tasks 7, 8, 4; `JwtAuthenticationFilter` stale-sv → Task 4; rate-limit test →
  Task 10; Testcontainers full flow → Task 10 (`PasswordResetControllerIT`); pre-deploy real
  verification → Task 15; `SecurityConfig` scope → Task 10. ✅
- §"Open items": reset email copy → Task 9 Step 3 (spelled in the Interfaces block: subject +
  the link + "30 minutos" + "si no fuiste tú, ignóralo"; es primary, en in the frontend pages);
  cleanup job-vs-lazy → resolved (scheduled, Task 12); rate-limit number → 5/60s as the spec
  proposed, one `@Value` to retune. ✅
- The refresh-token hole the spec's wording implies but doesn't detail (a stolen 7-day refresh
  token surviving a reset) → closed in Tasks 3–5 by putting `sv` on the refresh token and checking
  it in `RefreshTokenUseCase`.

**Placeholder scan:** the frontend task (14) references "the existing unauthenticated login screen"
and "the auth API client module" by description with a grep to locate them — these are real files
the executor finds, and the requirements (fields, redirects, copy, error states) are fully
specified. Backend tasks give real code for every new class or exact edit locations. No `TODO`/`TBD`.

**Type consistency:** `User.getSessionVersion()`/`incrementSessionVersion()`/`setSessionVersion(int)`
fixed in Task 2, used in Tasks 4, 5, 8, 11. `generateAccessToken(..., int)` /
`generateRefreshToken(UUID, int)` fixed in Task 3, called in Tasks 4, 5. `PasswordResetToken.issue`
/ `isUsable` / `markUsed` and the `PasswordResetTokenRepository` 4-method port fixed in Task 6,
consumed in Tasks 7, 8, 12. `PasswordResetMailer.sendResetLink(String, String, String)` fixed in
Task 7, implemented in Task 9. The generic error string `"El enlace no es válido o ya expiró"` is
identical in Task 8 (thrown) and Task 10 (asserted) and Task 14 (displayed).

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-08-28-customer-password-reset.md`. Two
execution options:**

**1. Subagent-Driven (recommended)** — fresh subagent per task, review between tasks.

**2. Inline Execution** — execute in this session with checkpoints.

**Which approach?**
