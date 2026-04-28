# Worker Roles, Vigency & Permissions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add worker roles (SUPERVISOR, ADMINISTRACION, DESPACHADOR), vigency dates to User, a DB-stored permission matrix, and embed permissions in JWT at login.

**Architecture:** Extend the `UserRole` enum and `User` domain model. Add a `role_permissions` table seeded with defaults. All four login/refresh use cases inject `RolePermissionRepository` to embed `permissions: List<String>` into the access token. The frontend reads permissions from the auth response and stores them in Zustand. `AdminSidebar` filters nav items by permissions. `@PreAuthorize` annotations remain the API security boundary.

**Tech Stack:** Spring Boot 3, Spring Security, JJWT, JPA + PostgreSQL, Flyway, React + Zustand (frontend).

---

## File Map

**Create:**
- `backend/.../user/domain/enums/UserRole.java` — add 3 new values
- `backend/.../shared/rbac/domain/model/RolePermission.java` — value object
- `backend/.../shared/rbac/domain/ports/RolePermissionRepository.java` — port
- `backend/.../shared/rbac/infrastructure/persistence/entities/RolePermissionEntity.java`
- `backend/.../shared/rbac/infrastructure/persistence/repositories/RolePermissionJpaRepository.java`
- `backend/.../shared/rbac/infrastructure/persistence/repositories/RolePermissionRepositoryAdapter.java`
- `backend/.../shared/rbac/application/dto/WorkerDto.java`
- `backend/.../shared/rbac/application/dto/PermissionMatrixDto.java`
- `backend/.../shared/rbac/application/usecases/AssignWorkerUseCase.java`
- `backend/.../shared/rbac/application/usecases/RevokeWorkerUseCase.java`
- `backend/.../shared/rbac/application/usecases/ListWorkersUseCase.java`
- `backend/.../shared/rbac/application/usecases/GetPermissionMatrixUseCase.java`
- `backend/.../shared/rbac/application/usecases/UpdatePermissionMatrixUseCase.java`
- `backend/.../shared/rbac/infrastructure/web/WorkerController.java`
- `backend/.../shared/rbac/infrastructure/web/PermissionController.java`
- `backend/.../shared/rbac/infrastructure/web/requests/AssignWorkerRequest.java`
- `backend/.../shared/rbac/infrastructure/web/requests/UpdatePermissionMatrixRequest.java`
- `backend/.../resources/db/migration/V35__worker_vigency.sql`
- `backend/.../resources/db/migration/V36__role_permissions.sql`
- `backend/.../resources/db/migration/V37__seed_role_permissions.sql`
- `backend/src/test/.../rbac/domain/RolePermissionTest.java`
- `backend/src/test/.../rbac/application/AssignWorkerUseCaseTest.java`
- `backend/src/test/.../rbac/infrastructure/web/WorkerPermissionsIT.java`
- `frontend/src/pages/admin/roles-permisos.astro`
- `frontend/src/islands/admin/RolesPermisosView.tsx`
- `frontend/src/islands/admin/WorkerAssignmentModal.tsx`
- `frontend/src/pages/admin/worker-waiting.astro`

**Modify:**
- `backend/.../user/domain/model/User.java` — add vigency fields
- `backend/.../user/infrastructure/persistence/entities/UserEntity.java` — add vigency columns
- `backend/.../user/infrastructure/persistence/repositories/UserRepositoryAdapter.java` — map vigency
- `backend/.../shared/auth/infrastructure/JwtTokenProvider.java` — add permissions param
- `backend/.../shared/auth/application/dto/AuthTokenDto.java` — add permissions field
- `backend/.../shared/auth/domain/AuthenticatedUser.java` — add permissions field
- `backend/.../shared/auth/infrastructure/JwtAuthenticationFilter.java` — read permissions claim
- `backend/.../shared/auth/application/usecases/LoginUseCase.java` — embed permissions
- `backend/.../shared/auth/application/usecases/GoogleLoginUseCase.java` — embed permissions
- `backend/.../shared/auth/application/usecases/RegisterUseCase.java` — embed permissions
- `backend/.../shared/auth/application/usecases/RefreshTokenUseCase.java` — embed permissions + vigency check
- `frontend/src/lib/authStore.ts` — add permissions to StoredUser
- `frontend/src/lib/api.ts` — add permissions to AuthTokenResponse
- `frontend/src/islands/auth/LoginForm.tsx` — pass permissions in setAuth
- `frontend/src/islands/auth/RegisterForm.tsx` — pass permissions in setAuth
- `frontend/src/islands/admin/AdminSidebar.tsx` — filter nav by permissions
- `frontend/src/islands/admin/UserManagement.tsx` — add assign worker button/modal

---

### Task 1: Add new UserRole values and User vigency fields

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/user/domain/enums/UserRole.java`
- Modify: `backend/src/main/java/com/pilarestilo/user/domain/model/User.java`
- Modify: `backend/src/main/java/com/pilarestilo/user/infrastructure/persistence/entities/UserEntity.java`
- Modify: `backend/src/main/java/com/pilarestilo/user/infrastructure/persistence/repositories/UserRepositoryAdapter.java`
- Create: `backend/src/main/resources/db/migration/V35__worker_vigency.sql`

- [ ] **Step 1: Write failing test for User vigency fields**

Create `backend/src/test/java/com/pilarestilo/user/domain/UserVigencyTest.java`:

```java
package com.pilarestilo.user.domain;

import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class UserVigencyTest {

    @Test
    void new_user_has_null_vigency_fields() {
        User u = User.create("a@b.com", "Test", UserRole.SELLER, "hash");
        assertNull(u.getWorkerVigencyStart());
        assertNull(u.getWorkerVigencyEnd());
    }

    @Test
    void can_set_vigency_dates() {
        User u = User.create("a@b.com", "Test", UserRole.SELLER, "hash");
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        u.setWorkerVigencyStart(start);
        u.setWorkerVigencyEnd(end);
        assertEquals(start, u.getWorkerVigencyStart());
        assertEquals(end, u.getWorkerVigencyEnd());
    }

    @Test
    void vigency_end_can_be_null_for_open_ended() {
        User u = User.create("a@b.com", "Test", UserRole.SELLER, "hash");
        u.setWorkerVigencyStart(LocalDate.of(2026, 5, 1));
        assertNull(u.getWorkerVigencyEnd());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl backend -Dtest=UserVigencyTest -q`
Expected: FAIL — `getWorkerVigencyStart` method not found.

- [ ] **Step 3: Add new roles to UserRole enum**

Replace the full content of `backend/src/main/java/com/pilarestilo/user/domain/enums/UserRole.java`:

```java
package com.pilarestilo.user.domain.enums;

public enum UserRole {
    ADMIN,
    SUPERVISOR,
    ADMINISTRACION,
    DESPACHADOR,
    SELLER,
    CUSTOMER
}
```

- [ ] **Step 4: Add vigency fields to User domain model**

In `backend/src/main/java/com/pilarestilo/user/domain/model/User.java`, add after `private boolean avatarManuallySet;`:

```java
private java.time.LocalDate workerVigencyStart;
private java.time.LocalDate workerVigencyEnd;
```

Add getters and setters after the existing `setAvatarManuallySet` method:

```java
public java.time.LocalDate getWorkerVigencyStart() { return workerVigencyStart; }
public java.time.LocalDate getWorkerVigencyEnd() { return workerVigencyEnd; }
public void setWorkerVigencyStart(java.time.LocalDate date) { this.workerVigencyStart = date; }
public void setWorkerVigencyEnd(java.time.LocalDate date) { this.workerVigencyEnd = date; }
```

- [ ] **Step 5: Add vigency columns to UserEntity**

In `UserEntity.java`, add after the `avatarManuallySet` field:

```java
@Column(name = "worker_vigency_start")
private java.time.LocalDate workerVigencyStart;

@Column(name = "worker_vigency_end")
private java.time.LocalDate workerVigencyEnd;
```

Add getters/setters:

```java
public java.time.LocalDate getWorkerVigencyStart() { return workerVigencyStart; }
public void setWorkerVigencyStart(java.time.LocalDate d) { this.workerVigencyStart = d; }
public java.time.LocalDate getWorkerVigencyEnd() { return workerVigencyEnd; }
public void setWorkerVigencyEnd(java.time.LocalDate d) { this.workerVigencyEnd = d; }
```

- [ ] **Step 6: Map vigency in UserRepositoryAdapter**

In `toEntity(User user)`, after `entity.setAvatarManuallySet(...)`:
```java
entity.setWorkerVigencyStart(user.getWorkerVigencyStart());
entity.setWorkerVigencyEnd(user.getWorkerVigencyEnd());
```

In `toDomain(UserEntity entity)`, after `user.setAvatarManuallySet(...)`:
```java
user.setWorkerVigencyStart(entity.getWorkerVigencyStart());
user.setWorkerVigencyEnd(entity.getWorkerVigencyEnd());
```

- [ ] **Step 7: Create Flyway migration V35**

Create `backend/src/main/resources/db/migration/V35__worker_vigency.sql`:

```sql
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS worker_vigency_start DATE,
    ADD COLUMN IF NOT EXISTS worker_vigency_end DATE;
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./mvnw test -pl backend -Dtest=UserVigencyTest -q`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/user/domain/enums/UserRole.java \
        backend/src/main/java/com/pilarestilo/user/domain/model/User.java \
        backend/src/main/java/com/pilarestilo/user/infrastructure/persistence/entities/UserEntity.java \
        backend/src/main/java/com/pilarestilo/user/infrastructure/persistence/repositories/UserRepositoryAdapter.java \
        backend/src/main/resources/db/migration/V35__worker_vigency.sql \
        backend/src/test/java/com/pilarestilo/user/domain/UserVigencyTest.java
git commit -m "feat(rbac): add worker roles and vigency fields to User"
```

---

### Task 2: RolePermission domain, DB migration, seeding

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/shared/rbac/domain/model/RolePermission.java`
- Create: `backend/src/main/java/com/pilarestilo/shared/rbac/domain/ports/RolePermissionRepository.java`
- Create: `backend/src/main/resources/db/migration/V36__role_permissions.sql`
- Create: `backend/src/main/resources/db/migration/V37__seed_role_permissions.sql`
- Create: `backend/src/test/java/com/pilarestilo/rbac/domain/RolePermissionTest.java`

- [ ] **Step 1: Write failing test for RolePermission**

Create `backend/src/test/java/com/pilarestilo/rbac/domain/RolePermissionTest.java`:

```java
package com.pilarestilo.rbac.domain;

import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RolePermissionTest {

    @Test
    void creates_with_role_and_view_key() {
        RolePermission rp = new RolePermission(UserRole.SELLER, "dashboard");
        assertEquals(UserRole.SELLER, rp.getRole());
        assertEquals("dashboard", rp.getViewKey());
    }

    @Test
    void equal_instances_have_same_role_and_view_key() {
        RolePermission a = new RolePermission(UserRole.SELLER, "caja");
        RolePermission b = new RolePermission(UserRole.SELLER, "caja");
        assertEquals(a, b);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl backend -Dtest=RolePermissionTest -q`
Expected: FAIL — class not found.

- [ ] **Step 3: Create RolePermission domain model**

Create `backend/src/main/java/com/pilarestilo/shared/rbac/domain/model/RolePermission.java`:

```java
package com.pilarestilo.shared.rbac.domain.model;

import com.pilarestilo.user.domain.enums.UserRole;

import java.util.Objects;

public class RolePermission {

    private final UserRole role;
    private final String viewKey;

    public RolePermission(UserRole role, String viewKey) {
        this.role = role;
        this.viewKey = viewKey;
    }

    public UserRole getRole() { return role; }
    public String getViewKey() { return viewKey; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermission other)) return false;
        return role == other.role && Objects.equals(viewKey, other.viewKey);
    }

    @Override
    public int hashCode() { return Objects.hash(role, viewKey); }
}
```

- [ ] **Step 4: Create RolePermissionRepository port**

Create `backend/src/main/java/com/pilarestilo/shared/rbac/domain/ports/RolePermissionRepository.java`:

```java
package com.pilarestilo.shared.rbac.domain.ports;

import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.user.domain.enums.UserRole;

import java.util.List;

public interface RolePermissionRepository {
    List<String> findViewKeysByRole(UserRole role);
    List<RolePermission> findAll();
    void replaceAll(List<RolePermission> permissions);
}
```

- [ ] **Step 5: Create Flyway V36 and V37**

Create `backend/src/main/resources/db/migration/V36__role_permissions.sql`:

```sql
CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    view_key VARCHAR(100) NOT NULL,
    UNIQUE (role, view_key)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role);
```

Create `backend/src/main/resources/db/migration/V37__seed_role_permissions.sql`:

```sql
INSERT INTO role_permissions (role, view_key) VALUES
    ('ADMIN',          'dashboard'),
    ('ADMIN',          'productos'),
    ('ADMIN',          'usuarios'),
    ('ADMIN',          'caja'),
    ('ADMIN',          'despachos'),
    ('ADMIN',          'configuracion'),
    ('ADMIN',          'roles_permisos'),
    ('SUPERVISOR',     'dashboard'),
    ('SUPERVISOR',     'caja'),
    ('ADMINISTRACION', 'dashboard'),
    ('ADMINISTRACION', 'usuarios'),
    ('ADMINISTRACION', 'roles_permisos'),
    ('DESPACHADOR',    'dashboard'),
    ('DESPACHADOR',    'despachos'),
    ('SELLER',         'dashboard'),
    ('SELLER',         'productos'),
    ('SELLER',         'caja')
ON CONFLICT (role, view_key) DO NOTHING;
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw test -pl backend -Dtest=RolePermissionTest -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/rbac/ \
        backend/src/main/resources/db/migration/V36__role_permissions.sql \
        backend/src/main/resources/db/migration/V37__seed_role_permissions.sql \
        backend/src/test/java/com/pilarestilo/rbac/domain/RolePermissionTest.java
git commit -m "feat(rbac): add RolePermission domain + DB migrations V36-V37"
```

---

### Task 3: RolePermission JPA infrastructure

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/persistence/entities/RolePermissionEntity.java`
- Create: `backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/persistence/repositories/RolePermissionJpaRepository.java`
- Create: `backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/persistence/repositories/RolePermissionRepositoryAdapter.java`

- [ ] **Step 1: Write failing test for adapter**

Create `backend/src/test/java/com/pilarestilo/rbac/infrastructure/RolePermissionRepositoryAdapterTest.java`:

```java
package com.pilarestilo.rbac.infrastructure;

import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.shared.rbac.infrastructure.persistence.repositories.RolePermissionRepositoryAdapter;
import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class RolePermissionRepositoryAdapterTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RolePermissionRepository repo;

    @Test
    void seller_has_expected_view_keys_from_seed() {
        List<String> keys = repo.findViewKeysByRole(UserRole.SELLER);
        assertTrue(keys.contains("dashboard"));
        assertTrue(keys.contains("caja"));
        assertTrue(keys.contains("productos"));
    }

    @Test
    void customer_has_no_permissions() {
        List<String> keys = repo.findViewKeysByRole(UserRole.CUSTOMER);
        assertTrue(keys.isEmpty());
    }

    @Test
    void admin_has_all_permissions() {
        List<String> keys = repo.findViewKeysByRole(UserRole.ADMIN);
        assertTrue(keys.contains("configuracion"));
        assertTrue(keys.contains("roles_permisos"));
        assertEquals(7, keys.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl backend -Dtest=RolePermissionRepositoryAdapterTest -q`
Expected: FAIL — bean not found.

- [ ] **Step 3: Create RolePermissionEntity**

Create `backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/persistence/entities/RolePermissionEntity.java`:

```java
package com.pilarestilo.shared.rbac.infrastructure.persistence.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "role_permissions")
public class RolePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "view_key", nullable = false, length = 100)
    private String viewKey;

    public Long getId() { return id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getViewKey() { return viewKey; }
    public void setViewKey(String viewKey) { this.viewKey = viewKey; }
}
```

- [ ] **Step 4: Create RolePermissionJpaRepository**

Create `backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/persistence/repositories/RolePermissionJpaRepository.java`:

```java
package com.pilarestilo.shared.rbac.infrastructure.persistence.repositories;

import com.pilarestilo.shared.rbac.infrastructure.persistence.entities.RolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RolePermissionJpaRepository extends JpaRepository<RolePermissionEntity, Long> {

    @Query("SELECT e.viewKey FROM RolePermissionEntity e WHERE e.role = :role")
    List<String> findViewKeysByRole(String role);

    void deleteAllByRole(String role);
}
```

- [ ] **Step 5: Create RolePermissionRepositoryAdapter**

Create `backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/persistence/repositories/RolePermissionRepositoryAdapter.java`:

```java
package com.pilarestilo.shared.rbac.infrastructure.persistence.repositories;

import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.shared.rbac.infrastructure.persistence.entities.RolePermissionEntity;
import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RolePermissionRepositoryAdapter implements RolePermissionRepository {

    private final RolePermissionJpaRepository jpaRepository;

    public RolePermissionRepositoryAdapter(RolePermissionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<String> findViewKeysByRole(UserRole role) {
        return jpaRepository.findViewKeysByRole(role.name());
    }

    @Override
    public List<RolePermission> findAll() {
        return jpaRepository.findAll().stream()
                .map(e -> new RolePermission(UserRole.valueOf(e.getRole()), e.getViewKey()))
                .toList();
    }

    @Override
    @Transactional
    public void replaceAll(List<RolePermission> permissions) {
        // Delete only non-ADMIN rows (ADMIN always has all permissions)
        List<UserRole> editableRoles = List.of(
                UserRole.SUPERVISOR, UserRole.ADMINISTRACION, UserRole.DESPACHADOR, UserRole.SELLER);
        for (UserRole role : editableRoles) {
            jpaRepository.deleteAllByRole(role.name());
        }
        List<RolePermissionEntity> entities = permissions.stream()
                .filter(p -> editableRoles.contains(p.getRole()))
                .map(p -> {
                    RolePermissionEntity e = new RolePermissionEntity();
                    e.setRole(p.getRole().name());
                    e.setViewKey(p.getViewKey());
                    return e;
                }).toList();
        jpaRepository.saveAll(entities);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw test -pl backend -Dtest=RolePermissionRepositoryAdapterTest -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/ \
        backend/src/test/java/com/pilarestilo/rbac/infrastructure/
git commit -m "feat(rbac): RolePermission JPA infrastructure"
```

---

### Task 4: Embed permissions in JWT — backend

**Files:**
- Modify: `backend/.../shared/auth/infrastructure/JwtTokenProvider.java`
- Modify: `backend/.../shared/auth/application/dto/AuthTokenDto.java`
- Modify: `backend/.../shared/auth/domain/AuthenticatedUser.java`
- Modify: `backend/.../shared/auth/infrastructure/JwtAuthenticationFilter.java`
- Modify: all 4 login/refresh use cases

- [ ] **Step 1: Write failing test for LoginUseCase embedding permissions**

Create `backend/src/test/java/com/pilarestilo/rbac/application/LoginWithPermissionsTest.java`:

```java
package com.pilarestilo.rbac.application;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.application.usecases.LoginUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class LoginWithPermissionsTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    LoginUseCase loginUseCase;

    @Test
    void admin_login_returns_permissions_in_token_dto() {
        AuthTokenDto dto = loginUseCase.execute("admin@pilarestilo.com", "admin2026");
        assertNotNull(dto.permissions());
        assertTrue(dto.permissions().contains("dashboard"));
        assertTrue(dto.permissions().contains("configuracion"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl backend -Dtest=LoginWithPermissionsTest -q`
Expected: FAIL — `permissions()` method not found on `AuthTokenDto`.

- [ ] **Step 3: Update AuthTokenDto to include permissions**

Replace `backend/src/main/java/com/pilarestilo/shared/auth/application/dto/AuthTokenDto.java`:

```java
package com.pilarestilo.shared.auth.application.dto;

import java.util.List;
import java.util.UUID;

public record AuthTokenDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        UUID userId,
        String email,
        String role,
        String fullName,
        String avatarUrl,
        boolean accountMerged,
        List<String> permissions
) {
    public static AuthTokenDto of(String accessToken, String refreshToken,
                                   UUID userId, String email, String role,
                                   String fullName, String avatarUrl,
                                   List<String> permissions) {
        return new AuthTokenDto(accessToken, refreshToken, "Bearer",
                userId, email, role, fullName, avatarUrl, false, permissions);
    }

    public static AuthTokenDto ofMerged(String accessToken, String refreshToken,
                                        UUID userId, String email, String role,
                                        String fullName, String avatarUrl,
                                        boolean accountMerged, List<String> permissions) {
        return new AuthTokenDto(accessToken, refreshToken, "Bearer",
                userId, email, role, fullName, avatarUrl, accountMerged, permissions);
    }
}
```

- [ ] **Step 4: Update JwtTokenProvider to embed permissions**

Replace `generateAccessToken` in `JwtTokenProvider.java`:

```java
public String generateAccessToken(UUID userId, String email, UserRole role, List<String> permissions) {
    return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role.name())
            .claim("permissions", permissions)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRY_MS))
            .signWith(key)
            .compact();
}
```

Add import at top: `import java.util.List;`

- [ ] **Step 5: Update AuthenticatedUser record to include permissions**

Replace `backend/src/main/java/com/pilarestilo/shared/auth/domain/AuthenticatedUser.java`:

```java
package com.pilarestilo.shared.auth.domain;

import com.pilarestilo.user.domain.enums.UserRole;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, UserRole role, List<String> permissions) {}
```

- [ ] **Step 6: Update JwtAuthenticationFilter to read permissions from JWT**

In `JwtAuthenticationFilter.doFilterInternal`, replace the block that builds `AuthenticatedUser`:

```java
String email = claims.get("email", String.class);
UserRole role = UserRole.valueOf(claims.get("role", String.class));
@SuppressWarnings("unchecked")
List<String> permissions = claims.get("permissions", List.class);
if (permissions == null) permissions = List.of();

List<SimpleGrantedAuthority> authorities = List.of(
        new SimpleGrantedAuthority("ROLE_" + role.name()));
UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        new AuthenticatedUser(userId, email, role, permissions), null, authorities);
```

Add import: `import java.util.List;`

- [ ] **Step 7: Update LoginUseCase**

Replace `LoginUseCase.java`:

```java
package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RolePermissionRepository rolePermissionRepository;

    public LoginUseCase(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider,
                        RolePermissionRepository rolePermissionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public AuthTokenDto execute(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DomainException("Invalid credentials"));
        if (!user.isActive()) throw new DomainException("This account is blocked");
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new DomainException("Invalid credentials");
        }
        List<String> permissions = rolePermissionRepository.findViewKeysByRole(user.getRole());
        String access  = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), permissions);
        String refresh = jwtTokenProvider.generateRefreshToken(user.getId());
        return AuthTokenDto.of(access, refresh, user.getId(), user.getEmail(),
                user.getRole().name(), user.getFullName(), user.getAvatarUrl(), permissions);
    }
}
```

- [ ] **Step 8: Update RegisterUseCase**

Replace `RegisterUseCase.java`:

```java
package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.domain.ports.PasswordEncoder;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegisterUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RolePermissionRepository rolePermissionRepository;

    public RegisterUseCase(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           RolePermissionRepository rolePermissionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public AuthTokenDto execute(String email, String rawPassword, String fullName) {
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("Email already registered: " + email);
        }
        String hash = passwordEncoder.encode(rawPassword);
        User user = User.create(email, fullName, UserRole.CUSTOMER, hash);
        User saved = userRepository.save(user);
        // CUSTOMER has no worker permissions — empty list
        List<String> permissions = List.of();
        String access  = jwtTokenProvider.generateAccessToken(saved.getId(), saved.getEmail(), saved.getRole(), permissions);
        String refresh = jwtTokenProvider.generateRefreshToken(saved.getId());
        return AuthTokenDto.of(access, refresh, saved.getId(), saved.getEmail(),
                saved.getRole().name(), saved.getFullName(), saved.getAvatarUrl(), permissions);
    }
}
```

- [ ] **Step 9: Update RefreshTokenUseCase (add permissions + vigency check)**

Replace `RefreshTokenUseCase.java`:

```java
package com.pilarestilo.shared.auth.application.usecases;

import com.pilarestilo.shared.auth.application.dto.AuthTokenDto;
import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenUseCase {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public RefreshTokenUseCase(JwtTokenProvider jwtTokenProvider,
                               UserRepository userRepository,
                               RolePermissionRepository rolePermissionRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public AuthTokenDto execute(String refreshToken) {
        if (!jwtTokenProvider.isValid(refreshToken)) {
            throw new DomainException("Invalid or expired refresh token");
        }
        Claims claims = jwtTokenProvider.parseToken(refreshToken);
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new DomainException("Token is not a refresh token");
        }
        UUID userId = UUID.fromString(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found"));
        if (!user.isActive()) throw new DomainException("This account is blocked");

        // Vigency check for worker roles
        UserRole role = user.getRole();
        if (role != UserRole.ADMIN && role != UserRole.CUSTOMER) {
            LocalDate today = LocalDate.now();
            if (user.getWorkerVigencyEnd() != null && today.isAfter(user.getWorkerVigencyEnd())) {
                throw new DomainException("Worker vigency has expired");
            }
        }

        List<String> permissions = rolePermissionRepository.findViewKeysByRole(role);
        String access  = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), role, permissions);
        String newRefresh = jwtTokenProvider.generateRefreshToken(user.getId());
        return AuthTokenDto.of(access, newRefresh, user.getId(), user.getEmail(),
                role.name(), user.getFullName(), user.getAvatarUrl(), permissions);
    }
}
```

- [ ] **Step 10: Update GoogleLoginUseCase**

In `GoogleLoginUseCase.java`, inject `RolePermissionRepository`:

Add field: `private final RolePermissionRepository rolePermissionRepository;`

Add to constructor params: `RolePermissionRepository rolePermissionRepository`

Assign: `this.rolePermissionRepository = rolePermissionRepository;`

Replace the two token generation lines in `execute()`:
```java
List<String> permissions = rolePermissionRepository.findViewKeysByRole(user.getRole());
String access = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), permissions);
String refresh = jwtTokenProvider.generateRefreshToken(user.getId());
return AuthTokenDto.ofMerged(access, refresh, user.getId(), user.getEmail(),
        user.getRole().name(), user.getFullName(), user.getAvatarUrl(), accountMerged, permissions);
```

Add import: `import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;` and `import java.util.List;`

- [ ] **Step 11: Run test to verify it passes**

Run: `./mvnw test -pl backend -Dtest=LoginWithPermissionsTest -q`
Expected: PASS

- [ ] **Step 12: Run full test suite**

Run: `./mvnw test -pl backend -q`
Expected: All tests pass. If `AuthorizationGuardsIT` fails because `AuthenticatedUser` constructor signature changed, check that it doesn't construct `AuthenticatedUser` directly (it only uses MockMvc).

- [ ] **Step 13: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/auth/ \
        backend/src/test/java/com/pilarestilo/rbac/application/
git commit -m "feat(rbac): embed permissions in JWT for all login/refresh flows"
```

---

### Task 5: Worker assignment and permission matrix use cases + controllers

**Files:**
- Create: `backend/.../shared/rbac/application/dto/WorkerDto.java`
- Create: `backend/.../shared/rbac/application/dto/PermissionMatrixDto.java`
- Create: `backend/.../shared/rbac/application/usecases/AssignWorkerUseCase.java`
- Create: `backend/.../shared/rbac/application/usecases/RevokeWorkerUseCase.java`
- Create: `backend/.../shared/rbac/application/usecases/ListWorkersUseCase.java`
- Create: `backend/.../shared/rbac/application/usecases/GetPermissionMatrixUseCase.java`
- Create: `backend/.../shared/rbac/application/usecases/UpdatePermissionMatrixUseCase.java`
- Create: `backend/.../shared/rbac/infrastructure/web/WorkerController.java`
- Create: `backend/.../shared/rbac/infrastructure/web/PermissionController.java`
- Create: `backend/.../shared/rbac/infrastructure/web/requests/AssignWorkerRequest.java`
- Create: `backend/.../shared/rbac/infrastructure/web/requests/UpdatePermissionMatrixRequest.java`

- [ ] **Step 1: Write failing integration test**

Create `backend/src/test/java/com/pilarestilo/rbac/infrastructure/web/WorkerPermissionsIT.java`:

```java
package com.pilarestilo.rbac.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class WorkerPermissionsIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void admin_can_list_workers() throws Exception {
        String token = loginAdmin();
        mvc.perform(get("/api/admin/workers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void admin_can_assign_worker_role() throws Exception {
        String adminToken = loginAdmin();
        String userId = registerAndGetId("worker_" + System.currentTimeMillis() + "@test.com");

        String body = om.writeValueAsString(Map.of(
                "role", "SELLER",
                "vigencyStart", "2026-05-01"
        ));
        mvc.perform(post("/api/admin/workers/" + userId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    void non_admin_cannot_assign_workers() throws Exception {
        String customerToken = registerAndGetToken("cust_" + System.currentTimeMillis() + "@test.com");
        String targetId = registerAndGetId("target_" + System.currentTimeMillis() + "@test.com");

        String body = om.writeValueAsString(Map.of("role", "SELLER", "vigencyStart", "2026-05-01"));
        mvc.perform(post("/api/admin/workers/" + targetId + "/assign")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_can_get_and_update_permission_matrix() throws Exception {
        String token = loginAdmin();
        mvc.perform(get("/api/admin/permissions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isArray());
    }

    private String loginAdmin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", "admin@pilarestilo.com", "password", "admin2026"))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", "pass1234", "fullName", "Test"))))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String registerAndGetId(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", "pass1234", "fullName", "Test"))))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("userId").asText();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl backend -Dtest=WorkerPermissionsIT -q`
Expected: FAIL — 404 on `/api/admin/workers`.

- [ ] **Step 3: Create DTOs**

Create `backend/src/main/java/com/pilarestilo/shared/rbac/application/dto/WorkerDto.java`:

```java
package com.pilarestilo.shared.rbac.application.dto;

import com.pilarestilo.user.domain.model.User;

import java.time.LocalDate;
import java.util.UUID;

public record WorkerDto(
        UUID id,
        String email,
        String fullName,
        String role,
        LocalDate vigencyStart,
        LocalDate vigencyEnd,
        boolean active
) {
    public static WorkerDto from(User user) {
        return new WorkerDto(
                user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(),
                user.getWorkerVigencyStart(), user.getWorkerVigencyEnd(),
                user.isActive());
    }
}
```

Create `backend/src/main/java/com/pilarestilo/shared/rbac/application/dto/PermissionMatrixDto.java`:

```java
package com.pilarestilo.shared.rbac.application.dto;

import com.pilarestilo.shared.rbac.domain.model.RolePermission;

import java.util.List;

public record PermissionMatrixDto(List<PermissionEntryDto> permissions) {

    public record PermissionEntryDto(String role, String viewKey) {}

    public static PermissionMatrixDto from(List<RolePermission> perms) {
        return new PermissionMatrixDto(
                perms.stream().map(p -> new PermissionEntryDto(p.getRole().name(), p.getViewKey())).toList());
    }
}
```

- [ ] **Step 4: Create use cases**

Create `backend/src/main/java/com/pilarestilo/shared/rbac/application/usecases/AssignWorkerUseCase.java`:

```java
package com.pilarestilo.shared.rbac.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.rbac.application.dto.WorkerDto;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class AssignWorkerUseCase {

    private final UserRepository userRepository;

    public AssignWorkerUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public WorkerDto execute(UUID userId, UserRole role, LocalDate vigencyStart, LocalDate vigencyEnd) {
        if (role == UserRole.ADMIN || role == UserRole.CUSTOMER) {
            throw new DomainException("Cannot assign ADMIN or CUSTOMER as worker role via this endpoint");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found"));
        user.changeRole(role);
        user.setWorkerVigencyStart(vigencyStart);
        user.setWorkerVigencyEnd(vigencyEnd);
        return WorkerDto.from(userRepository.save(user));
    }
}
```

Create `backend/src/main/java/com/pilarestilo/shared/rbac/application/usecases/RevokeWorkerUseCase.java`:

```java
package com.pilarestilo.shared.rbac.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.model.User;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RevokeWorkerUseCase {

    private final UserRepository userRepository;

    public RevokeWorkerUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found"));
        user.changeRole(UserRole.CUSTOMER);
        user.setWorkerVigencyStart(null);
        user.setWorkerVigencyEnd(null);
        userRepository.save(user);
    }
}
```

Create `backend/src/main/java/com/pilarestilo/shared/rbac/application/usecases/GetPermissionMatrixUseCase.java`:

```java
package com.pilarestilo.shared.rbac.application.usecases;

import com.pilarestilo.shared.rbac.application.dto.PermissionMatrixDto;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import org.springframework.stereotype.Service;

@Service
public class GetPermissionMatrixUseCase {

    private final RolePermissionRepository repo;

    public GetPermissionMatrixUseCase(RolePermissionRepository repo) { this.repo = repo; }

    public PermissionMatrixDto execute() {
        return PermissionMatrixDto.from(repo.findAll());
    }
}
```

Create `backend/src/main/java/com/pilarestilo/shared/rbac/application/usecases/UpdatePermissionMatrixUseCase.java`:

```java
package com.pilarestilo.shared.rbac.application.usecases;

import com.pilarestilo.shared.rbac.application.dto.PermissionMatrixDto;
import com.pilarestilo.shared.rbac.domain.model.RolePermission;
import com.pilarestilo.shared.rbac.domain.ports.RolePermissionRepository;
import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UpdatePermissionMatrixUseCase {

    private final RolePermissionRepository repo;

    public UpdatePermissionMatrixUseCase(RolePermissionRepository repo) { this.repo = repo; }

    public PermissionMatrixDto execute(List<PermissionMatrixDto.PermissionEntryDto> entries) {
        List<RolePermission> perms = entries.stream()
                .map(e -> new RolePermission(UserRole.valueOf(e.role()), e.viewKey()))
                .toList();
        repo.replaceAll(perms);
        return PermissionMatrixDto.from(repo.findAll());
    }
}
```

- [ ] **Step 5: Create request objects and controllers**

Create `backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/web/requests/AssignWorkerRequest.java`:

```java
package com.pilarestilo.shared.rbac.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AssignWorkerRequest(
        @NotNull String role,
        LocalDate vigencyStart,
        LocalDate vigencyEnd
) {}
```

Create `backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/web/requests/UpdatePermissionMatrixRequest.java`:

```java
package com.pilarestilo.shared.rbac.infrastructure.web.requests;

import com.pilarestilo.shared.rbac.application.dto.PermissionMatrixDto;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdatePermissionMatrixRequest(@NotNull List<PermissionMatrixDto.PermissionEntryDto> permissions) {}
```

Create `backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/web/WorkerController.java`:

```java
package com.pilarestilo.shared.rbac.infrastructure.web;

import com.pilarestilo.shared.rbac.application.dto.WorkerDto;
import com.pilarestilo.shared.rbac.application.usecases.AssignWorkerUseCase;
import com.pilarestilo.shared.rbac.application.usecases.ListWorkersUseCase;
import com.pilarestilo.shared.rbac.application.usecases.RevokeWorkerUseCase;
import com.pilarestilo.shared.rbac.infrastructure.web.requests.AssignWorkerRequest;
import com.pilarestilo.user.domain.enums.UserRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/workers")
public class WorkerController {

    private final AssignWorkerUseCase assignWorkerUseCase;
    private final RevokeWorkerUseCase revokeWorkerUseCase;
    private final ListWorkersUseCase listWorkersUseCase;

    public WorkerController(AssignWorkerUseCase assignWorkerUseCase,
                            RevokeWorkerUseCase revokeWorkerUseCase,
                            ListWorkersUseCase listWorkersUseCase) {
        this.assignWorkerUseCase = assignWorkerUseCase;
        this.revokeWorkerUseCase = revokeWorkerUseCase;
        this.listWorkersUseCase = listWorkersUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<WorkerDto> list(Pageable pageable) {
        return listWorkersUseCase.execute(pageable);
    }

    @PostMapping("/{userId}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public WorkerDto assign(@PathVariable UUID userId,
                            @RequestBody @Valid AssignWorkerRequest req) {
        return assignWorkerUseCase.execute(userId, UserRole.valueOf(req.role()),
                req.vigencyStart(), req.vigencyEnd());
    }

    @DeleteMapping("/{userId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void revoke(@PathVariable UUID userId) {
        revokeWorkerUseCase.execute(userId);
    }
}
```

Create `backend/src/main/java/com/pilarestilo/shared/rbac/application/usecases/ListWorkersUseCase.java`:

```java
package com.pilarestilo.shared.rbac.application.usecases;

import com.pilarestilo.shared.rbac.application.dto.WorkerDto;
import com.pilarestilo.user.domain.enums.UserRole;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListWorkersUseCase {

    private final UserRepository userRepository;

    public ListWorkersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<WorkerDto> execute(Pageable pageable) {
        // Return all non-CUSTOMER, non-ADMIN users (worker roles)
        List<UserRole> workerRoles = List.of(
                UserRole.SUPERVISOR, UserRole.ADMINISTRACION,
                UserRole.DESPACHADOR, UserRole.SELLER);
        return userRepository.findByRoleIn(workerRoles, pageable).map(WorkerDto::from);
    }
}
```

Note: `UserRepository.findByRoleIn()` does not exist yet. Add it to the port and adapter:

In `UserRepository.java` (port), add:
```java
Page<com.pilarestilo.user.domain.model.User> findByRoleIn(List<UserRole> roles, Pageable pageable);
```

In `UserJpaRepository.java`, add:
```java
Page<UserEntity> findByRoleIn(List<UserRole> roles, Pageable pageable);
```

In `UserRepositoryAdapter.java`, add:
```java
@Override
public Page<User> findByRoleIn(List<UserRole> roles, Pageable pageable) {
    return jpaRepository.findByRoleIn(roles, pageable).map(this::toDomain);
}
```

Create `backend/src/main/java/com/pilarestilo/shared/rbac/infrastructure/web/PermissionController.java`:

```java
package com.pilarestilo.shared.rbac.infrastructure.web;

import com.pilarestilo.shared.rbac.application.dto.PermissionMatrixDto;
import com.pilarestilo.shared.rbac.application.usecases.GetPermissionMatrixUseCase;
import com.pilarestilo.shared.rbac.application.usecases.UpdatePermissionMatrixUseCase;
import com.pilarestilo.shared.rbac.infrastructure.web.requests.UpdatePermissionMatrixRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/permissions")
public class PermissionController {

    private final GetPermissionMatrixUseCase getMatrixUseCase;
    private final UpdatePermissionMatrixUseCase updateMatrixUseCase;

    public PermissionController(GetPermissionMatrixUseCase getMatrixUseCase,
                                UpdatePermissionMatrixUseCase updateMatrixUseCase) {
        this.getMatrixUseCase = getMatrixUseCase;
        this.updateMatrixUseCase = updateMatrixUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PermissionMatrixDto get() {
        return getMatrixUseCase.execute();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PermissionMatrixDto update(@RequestBody @Valid UpdatePermissionMatrixRequest req) {
        return updateMatrixUseCase.execute(req.permissions());
    }
}
```

- [ ] **Step 6: Run integration test to verify it passes**

Run: `./mvnw test -pl backend -Dtest=WorkerPermissionsIT -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/shared/rbac/ \
        backend/src/main/java/com/pilarestilo/user/domain/ports/UserRepository.java \
        backend/src/main/java/com/pilarestilo/user/infrastructure/persistence/repositories/ \
        backend/src/test/java/com/pilarestilo/rbac/
git commit -m "feat(rbac): worker assignment and permission matrix endpoints"
```

---

### Task 6: Frontend — permissions in auth store and sidebar filtering

**Files:**
- Modify: `frontend/src/lib/authStore.ts`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/islands/auth/LoginForm.tsx`
- Modify: `frontend/src/islands/auth/RegisterForm.tsx`
- Modify: `frontend/src/islands/admin/AdminSidebar.tsx`

- [ ] **Step 1: Update StoredUser and AuthTokenResponse to include permissions**

In `frontend/src/lib/authStore.ts`, update `StoredUser`:

```typescript
export interface StoredUser {
  id: string;
  email: string;
  role: string;
  fullName?: string;
  avatarUrl?: string;
  permissions: string[];
}
```

In `frontend/src/lib/api.ts`, update `AuthTokenResponse`:

```typescript
export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  accountMerged?: boolean;
  userId: string;
  email: string;
  role: string;
  fullName?: string;
  avatarUrl?: string;
  permissions: string[];
}
```

Also update the `AdminUserDto` role union to include new roles:
```typescript
export interface AdminUserDto {
  id: string;
  email: string;
  fullName: string;
  role: 'ADMIN' | 'SUPERVISOR' | 'ADMINISTRACION' | 'DESPACHADOR' | 'SELLER' | 'CUSTOMER';
  active: boolean;
  createdAt: string;
}
```

- [ ] **Step 2: Update LoginForm to pass permissions in setAuth**

In `LoginForm.tsx`, find every `setAuth(data.accessToken, { ... })` call and add `permissions: data.permissions ?? []`:

```typescript
// Email/password login success block:
setAuth(data.accessToken, {
  id: data.userId,
  email: data.email,
  role: data.role,
  fullName: data.fullName,
  avatarUrl: data.avatarUrl,
  permissions: data.permissions ?? [],
});

// Google login success block:
setAuth(data.accessToken, {
  id: data.userId,
  email: data.email,
  role: data.role,
  fullName: data.fullName,
  avatarUrl: data.avatarUrl,
  permissions: data.permissions ?? [],
});
```

Also update the `isStaff` redirect check to include new worker roles:

```typescript
const isStaff = ['ADMIN', 'SUPERVISOR', 'ADMINISTRACION', 'DESPACHADOR', 'SELLER'].includes(data.role);
```

- [ ] **Step 3: Update RegisterForm to pass permissions in setAuth**

Apply the same `permissions: data.permissions ?? []` to the `setAuth` call in `RegisterForm.tsx`. CUSTOMER will get an empty array from the backend.

- [ ] **Step 4: Update AdminSidebar to filter nav items by permissions**

In `AdminSidebar.tsx`, add a `viewKey` field to each nav item and filter by user permissions:

Replace the `navItems` array:

```typescript
const navItems = [
  { href: '/admin/', icon: LayoutDashboard, label: 'Dashboard',  viewKey: 'dashboard'  },
  { href: '/admin/products',  icon: Package, label: 'Productos',  viewKey: 'productos'  },
  { href: '/admin/categories', icon: Tag,    label: 'Categorias', viewKey: 'productos'  },
  { href: '/admin/reviews',   icon: Star,    label: 'Resenas',    viewKey: 'productos'  },
  { href: '/admin/payments',  icon: CreditCard, label: 'Pagos',   viewKey: 'caja'       },
  { href: '/admin/discounts', icon: Ticket,  label: 'Descuentos', viewKey: 'productos'  },
  { href: '/admin/users',     icon: Users,   label: 'Usuarios',   viewKey: 'usuarios'   },
];
```

In the `return` block, before rendering `navItems.map(...)`, filter them:

```typescript
const { user, clearAuth } = useAuthStore();
const permissions = user?.permissions ?? [];
// ADMIN always has all — if role is ADMIN, don't filter
const visibleNavItems = user?.role === 'ADMIN'
  ? navItems
  : navItems.filter(item => permissions.includes(item.viewKey));
```

Replace `navItems.map(...)` with `visibleNavItems.map(...)`.

Also update `settingsSubmenuItems` to only show if user has `configuracion` permission:

```typescript
const showSettings = user?.role === 'ADMIN' || permissions.includes('configuracion');
```

Wrap the settings `<li>` block with `{showSettings && (...)}`.

- [ ] **Step 5: Verify build**

Run: `cd frontend && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/authStore.ts \
        frontend/src/lib/api.ts \
        frontend/src/islands/auth/LoginForm.tsx \
        frontend/src/islands/auth/RegisterForm.tsx \
        frontend/src/islands/admin/AdminSidebar.tsx
git commit -m "feat(rbac): store permissions in auth state, filter sidebar by permissions"
```

---

### Task 7: Frontend — Worker Waiting page, Roles/Permisos view, Worker Assignment Modal

**Files:**
- Create: `frontend/src/pages/admin/worker-waiting.astro`
- Create: `frontend/src/pages/admin/roles-permisos.astro`
- Create: `frontend/src/islands/admin/RolesPermisosView.tsx`
- Create: `frontend/src/islands/admin/WorkerAssignmentModal.tsx`
- Modify: `frontend/src/islands/admin/UserManagement.tsx`

- [ ] **Step 1: Create Worker Waiting page**

Create `frontend/src/pages/admin/worker-waiting.astro`:

```astro
---
import AdminLayout from '../../layouts/AdminLayout.astro';
---

<AdminLayout title="Acceso pendiente">
  <div class="flex flex-col items-center justify-center min-h-[60vh] text-center px-4">
    <div class="max-w-md">
      <h1 class="font-display text-pe-black text-3xl font-light mb-4">
        Tu acceso aún no está activo
      </h1>
      <p class="text-pe-charcoal/60 text-sm mb-8" id="vigency-message">
        Tu acceso de trabajador comenzará pronto.
      </p>
      <button
        id="logout-btn"
        class="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors"
      >
        Cerrar sesión
      </button>
    </div>
  </div>
</AdminLayout>

<script>
  import { useAuthStore } from '../../lib/authStore';

  const user = useAuthStore.getState().user;
  if (user?.vigencyStart) {
    const msg = document.getElementById('vigency-message');
    if (msg) {
      const date = new Date(user.vigencyStart + 'T00:00:00').toLocaleDateString('es-CL', {
        day: 'numeric', month: 'long', year: 'numeric'
      });
      msg.textContent = `Tu acceso de trabajador comienza el ${date}.`;
    }
  }

  document.getElementById('logout-btn')?.addEventListener('click', () => {
    useAuthStore.getState().clearAuth();
    document.cookie = 'pe_token=; path=/; max-age=0; SameSite=Lax';
    window.location.href = '/admin/login';
  });
</script>
```

Note: `StoredUser` needs `vigencyStart?: string` added to the interface in `authStore.ts` and the API response mapping. Add `vigencyStart` and `vigencyEnd` to `AuthTokenDto` (Java record) and the frontend `AuthTokenResponse` interface, then persist them in `StoredUser`.

Add to `AuthTokenDto.java` record: `String vigencyStart, String vigencyEnd` (formatted as ISO date string via `user.getWorkerVigencyStart() != null ? user.getWorkerVigencyStart().toString() : null`).

Add to `StoredUser` in `authStore.ts`: `vigencyStart?: string; vigencyEnd?: string;`

Add to `AuthTokenResponse` in `api.ts`: `vigencyStart?: string; vigencyEnd?: string;`

Pass in each `setAuth(...)` call: `vigencyStart: data.vigencyStart, vigencyEnd: data.vigencyEnd`.

Update all use cases that build `AuthTokenDto` to pass vigency dates from the `User` domain object.

- [ ] **Step 2: Create RolesPermisosView island**

Create `frontend/src/islands/admin/RolesPermisosView.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { useAuthStore } from '../../lib/authStore';

const VIEW_KEYS = ['dashboard', 'productos', 'usuarios', 'caja', 'despachos', 'configuracion', 'roles_permisos'];
const VIEW_LABELS: Record<string, string> = {
  dashboard: 'Dashboard', productos: 'Productos', usuarios: 'Usuarios',
  caja: 'Caja', despachos: 'Despachos', configuracion: 'Configuración',
  roles_permisos: 'Roles/Permisos',
};
const EDITABLE_ROLES = ['SUPERVISOR', 'ADMINISTRACION', 'DESPACHADOR', 'SELLER'];

interface PermissionEntry { role: string; viewKey: string; }
interface Matrix { [role: string]: Set<string>; }

export default function RolesPermisosView() {
  const { token } = useAuthStore();
  const [matrix, setMatrix] = useState<Matrix>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    fetch('/api/admin/permissions', { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.json())
      .then((data: { permissions: PermissionEntry[] }) => {
        const m: Matrix = {};
        EDITABLE_ROLES.forEach(r => { m[r] = new Set(); });
        data.permissions.forEach(e => { if (m[e.role]) m[e.role].add(e.viewKey); });
        setMatrix(m);
      })
      .finally(() => setLoading(false));
  }, [token]);

  function toggle(role: string, viewKey: string) {
    setMatrix(prev => {
      const next = { ...prev, [role]: new Set(prev[role]) };
      if (next[role].has(viewKey)) next[role].delete(viewKey);
      else next[role].add(viewKey);
      return next;
    });
  }

  async function save() {
    setSaving(true); setError(''); setSaved(false);
    const permissions: PermissionEntry[] = [];
    EDITABLE_ROLES.forEach(role => {
      matrix[role]?.forEach(viewKey => permissions.push({ role, viewKey }));
    });
    try {
      const r = await fetch('/api/admin/permissions', {
        method: 'PUT',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ permissions }),
      });
      if (!r.ok) throw new Error('Error al guardar');
      setSaved(true);
    } catch { setError('Error al guardar los permisos.'); }
    finally { setSaving(false); }
  }

  if (loading) return <p className="text-pe-charcoal/50 text-sm">Cargando...</p>;

  return (
    <div className="space-y-6">
      <div className="overflow-x-auto">
        <table className="text-sm w-full border-collapse">
          <thead>
            <tr>
              <th className="text-left py-2 pr-4 font-sans text-[0.65rem] tracking-widest uppercase text-pe-charcoal/40">
                Vista
              </th>
              {EDITABLE_ROLES.map(role => (
                <th key={role} className="text-center py-2 px-3 font-sans text-[0.65rem] tracking-widest uppercase text-pe-charcoal/40">
                  {role}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {VIEW_KEYS.map(viewKey => (
              <tr key={viewKey} className="border-t border-pe-sand/30">
                <td className="py-2 pr-4 text-pe-charcoal/70">{VIEW_LABELS[viewKey]}</td>
                {EDITABLE_ROLES.map(role => (
                  <td key={role} className="py-2 px-3 text-center">
                    <input
                      type="checkbox"
                      checked={matrix[role]?.has(viewKey) ?? false}
                      onChange={() => toggle(role, viewKey)}
                      className="accent-[#B76E79] w-4 h-4"
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {error && <p className="text-red-500 text-sm">{error}</p>}
      {saved && <p className="text-green-600 text-sm">Permisos guardados. Los cambios aplican en el próximo inicio de sesión.</p>}

      <button
        onClick={save}
        disabled={saving}
        className="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50"
      >
        {saving ? 'Guardando...' : 'Guardar cambios'}
      </button>
    </div>
  );
}
```

- [ ] **Step 3: Create roles-permisos Astro page**

Create `frontend/src/pages/admin/roles-permisos.astro`:

```astro
---
import AdminLayout from '../../layouts/AdminLayout.astro';
import RolesPermisosView from '../../islands/admin/RolesPermisosView';
---

<AdminLayout title="Roles y Permisos">
  <div class="mb-5 sm:mb-6">
    <p class="font-sans text-[0.65rem] tracking-[0.25em] uppercase text-pe-charcoal/35 mb-1">
      Admin
    </p>
    <h1 class="font-display text-pe-black text-2xl sm:text-3xl font-light">Roles y Permisos</h1>
    <p class="text-pe-charcoal/50 text-sm mt-1">
      Los cambios aplican en el próximo inicio de sesión del trabajador.
    </p>
  </div>
  <RolesPermisosView client:load />
</AdminLayout>
```

- [ ] **Step 4: Create WorkerAssignmentModal and add to UserManagement**

Create `frontend/src/islands/admin/WorkerAssignmentModal.tsx`:

```tsx
import { useState } from 'react';
import { X } from 'lucide-react';
import { useAuthStore } from '../../lib/authStore';

const WORKER_ROLES = ['SUPERVISOR', 'ADMINISTRACION', 'DESPACHADOR', 'SELLER'];

interface Props {
  userId: string;
  userFullName: string;
  currentRole: string;
  onClose: () => void;
  onSaved: () => void;
}

export default function WorkerAssignmentModal({ userId, userFullName, currentRole, onClose, onSaved }: Props) {
  const { token } = useAuthStore();
  const [role, setRole] = useState(WORKER_ROLES.includes(currentRole) ? currentRole : WORKER_ROLES[0]);
  const [vigencyStart, setVigencyStart] = useState('');
  const [vigencyEnd, setVigencyEnd] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function assign() {
    setSaving(true); setError('');
    try {
      const r = await fetch(`/api/admin/workers/${userId}/assign`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          role,
          vigencyStart: vigencyStart || null,
          vigencyEnd: vigencyEnd || null,
        }),
      });
      if (!r.ok) throw new Error('Error al asignar');
      onSaved();
      onClose();
    } catch { setError('No se pudo asignar el rol.'); }
    finally { setSaving(false); }
  }

  async function revoke() {
    setSaving(true); setError('');
    try {
      const r = await fetch(`/api/admin/workers/${userId}/revoke`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!r.ok) throw new Error('Error al revocar');
      onSaved();
      onClose();
    } catch { setError('No se pudo revocar el rol.'); }
    finally { setSaving(false); }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white w-full max-w-md mx-4 p-6 shadow-xl">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-display text-xl text-pe-black">Asignar rol trabajador</h2>
          <button onClick={onClose} className="text-pe-charcoal/40 hover:text-pe-black">
            <X size={20} />
          </button>
        </div>

        <p className="text-pe-charcoal/60 text-sm mb-5">{userFullName}</p>

        <div className="space-y-4">
          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">Rol</label>
            <select
              value={role}
              onChange={e => setRole(e.target.value)}
              className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm text-pe-black focus:outline-none focus:border-[#B76E79]"
            >
              {WORKER_ROLES.map(r => <option key={r} value={r}>{r}</option>)}
            </select>
          </div>

          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">
              Inicio vigencia
            </label>
            <input
              type="date"
              value={vigencyStart}
              onChange={e => setVigencyStart(e.target.value)}
              className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm text-pe-black focus:outline-none focus:border-[#B76E79]"
            />
          </div>

          <div>
            <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">
              Fin vigencia (opcional)
            </label>
            <input
              type="date"
              value={vigencyEnd}
              onChange={e => setVigencyEnd(e.target.value)}
              className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm text-pe-black focus:outline-none focus:border-[#B76E79]"
            />
          </div>
        </div>

        {error && <p className="text-red-500 text-sm mt-3">{error}</p>}

        <div className="flex gap-3 mt-6">
          <button
            onClick={assign}
            disabled={saving}
            className="flex-1 bg-[#1A1A1A] text-[#F8F4EF] py-2.5 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50"
          >
            {saving ? 'Guardando...' : 'Asignar rol'}
          </button>
          {WORKER_ROLES.includes(currentRole) && (
            <button
              onClick={revoke}
              disabled={saving}
              className="px-4 border border-red-300 text-red-500 text-xs tracking-widest uppercase hover:bg-red-50 transition-colors disabled:opacity-50"
            >
              Revocar
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
```

In `UserManagement.tsx`, import `WorkerAssignmentModal`, add an "Asignar rol" button to each user row, and wire up the modal open/close state. The exact lines depend on the current `UserManagement.tsx` implementation — find the actions column in the user table and add a button:

```tsx
<button
  onClick={() => setAssigningWorker({ userId: user.id, fullName: user.fullName, role: user.role })}
  className="text-[10px] tracking-widest uppercase text-pe-charcoal/50 hover:text-pe-rose transition-colors"
>
  Rol
</button>
```

Add state:
```tsx
const [assigningWorker, setAssigningWorker] = useState<{ userId: string; fullName: string; role: string } | null>(null);
```

Render modal:
```tsx
{assigningWorker && (
  <WorkerAssignmentModal
    userId={assigningWorker.userId}
    userFullName={assigningWorker.fullName}
    currentRole={assigningWorker.role}
    onClose={() => setAssigningWorker(null)}
    onSaved={() => { setAssigningWorker(null); refetch(); }}
  />
)}
```

(Where `refetch` is whatever function `UserManagement` uses to reload its user list.)

- [ ] **Step 5: Add roles-permisos nav item to AdminSidebar**

In `AdminSidebar.tsx`, add to `navItems`:
```typescript
{ href: '/admin/roles-permisos', icon: ShieldCheck, label: 'Roles/Permisos', viewKey: 'roles_permisos' },
```

Import `ShieldCheck` from `lucide-react`.

- [ ] **Step 6: Verify build**

Run: `cd frontend && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/admin/ \
        frontend/src/islands/admin/RolesPermisosView.tsx \
        frontend/src/islands/admin/WorkerAssignmentModal.tsx \
        frontend/src/islands/admin/UserManagement.tsx \
        frontend/src/islands/admin/AdminSidebar.tsx
git commit -m "feat(rbac): worker waiting page, roles/permisos view, assignment modal"
```

---

### Task 8: Run full test suite and final verification

- [ ] **Step 1: Run all backend tests**

Run: `./mvnw test -pl backend`
Expected: All tests pass, including `AuthorizationGuardsIT` and `WorkerPermissionsIT`.

- [ ] **Step 2: Run frontend build**

Run: `cd frontend && npm run build`
Expected: Clean build, no errors.

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat(rbac): complete worker roles, vigency, and permissions system"
```
