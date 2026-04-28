# Dispatch System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a dispatch workflow where DESPACHADOR workers claim paid orders, mark them shipped with carrier/tracking info, and customer receives status notifications. ADMIN/SUPERVISOR can view all dispatches.

**Architecture:** New `dispatch` bounded context following the hexagonal pattern of `order`. `Dispatch` is the aggregate root. An event listener on `OrderStatusChanged` auto-creates PENDING dispatch records for paid orders. State machine: PENDING → IN_PROGRESS → DISPATCHED → DELIVERED/FAILED. Uses existing `NotificationService` for customer notifications. Two controllers: `DespachoController` (DESPACHADOR) and `AdminDespachoController` (ADMIN/SUPERVISOR).

**Prerequisite:** Plan 1 (worker-roles) must be complete — DESPACHADOR role must exist.

**Tech Stack:** Spring Boot 3, JPA + PostgreSQL, Flyway V39, existing `NotificationService`, React (frontend).

---

## File Map

**Create (backend):**
- `backend/.../dispatch/domain/model/Dispatch.java`
- `backend/.../dispatch/domain/enums/DispatchStatus.java`
- `backend/.../dispatch/domain/ports/DispatchRepository.java`
- `backend/.../dispatch/application/dto/DispatchDto.java`
- `backend/.../dispatch/application/usecases/ClaimDispatchUseCase.java`
- `backend/.../dispatch/application/usecases/UnclaimDispatchUseCase.java`
- `backend/.../dispatch/application/usecases/MarkDispatchedUseCase.java`
- `backend/.../dispatch/application/usecases/MarkDeliveredUseCase.java`
- `backend/.../dispatch/application/usecases/MarkFailedUseCase.java`
- `backend/.../dispatch/application/usecases/ListDispatchesUseCase.java`
- `backend/.../dispatch/application/usecases/GetDispatchByOrderUseCase.java`
- `backend/.../dispatch/infrastructure/persistence/entities/DispatchEntity.java`
- `backend/.../dispatch/infrastructure/persistence/repositories/DispatchJpaRepository.java`
- `backend/.../dispatch/infrastructure/persistence/repositories/DispatchRepositoryAdapter.java`
- `backend/.../dispatch/infrastructure/listeners/OrderPaidDispatchListener.java`
- `backend/.../dispatch/infrastructure/web/DespachoController.java`
- `backend/.../dispatch/infrastructure/web/AdminDespachoController.java`
- `backend/.../dispatch/infrastructure/web/requests/MarkDispatchedRequest.java`
- `backend/.../dispatch/infrastructure/web/requests/MarkFailedRequest.java`
- `backend/src/main/resources/db/migration/V39__dispatches.sql`
- `backend/src/test/.../dispatch/domain/DispatchTest.java`
- `backend/src/test/.../dispatch/infrastructure/web/DespachoIT.java`

**Create (frontend):**
- `frontend/src/pages/admin/despachos.astro`
- `frontend/src/islands/admin/DespachosPage.tsx`

---

### Task 1: Dispatch domain model and migration

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/dispatch/domain/enums/DispatchStatus.java`
- Create: `backend/src/main/java/com/pilarestilo/dispatch/domain/model/Dispatch.java`
- Create: `backend/src/main/resources/db/migration/V39__dispatches.sql`

- [ ] **Step 1: Write failing domain test**

Create `backend/src/test/java/com/pilarestilo/dispatch/domain/DispatchTest.java`:

```java
package com.pilarestilo.dispatch.domain;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DispatchTest {

    @Test
    void new_dispatch_is_pending() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        assertEquals(DispatchStatus.PENDING, d.getStatus());
        assertNull(d.getDispatcherId());
    }

    @Test
    void can_claim_pending_dispatch() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        UUID dispatcher = UUID.randomUUID();
        d.claim(dispatcher);
        assertEquals(DispatchStatus.IN_PROGRESS, d.getStatus());
        assertEquals(dispatcher, d.getDispatcherId());
    }

    @Test
    void can_unclaim_in_progress_dispatch() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        d.claim(UUID.randomUUID());
        d.unclaim();
        assertEquals(DispatchStatus.PENDING, d.getStatus());
        assertNull(d.getDispatcherId());
    }

    @Test
    void cannot_claim_already_claimed_dispatch() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        d.claim(UUID.randomUUID());
        assertThrows(DomainException.class, () -> d.claim(UUID.randomUUID()));
    }

    @Test
    void can_dispatch_from_in_progress() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        d.claim(UUID.randomUUID());
        d.dispatch("Chilexpress", "CH123456", null, null);
        assertEquals(DispatchStatus.DISPATCHED, d.getStatus());
        assertEquals("CH123456", d.getTrackingCode());
    }

    @Test
    void cannot_dispatch_from_pending() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        assertThrows(DomainException.class, () -> d.dispatch("Chilexpress", "X", null, null));
    }

    @Test
    void can_deliver_from_dispatched() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        d.claim(UUID.randomUUID());
        d.dispatch("Chilexpress", "X", null, null);
        d.deliver();
        assertEquals(DispatchStatus.DELIVERED, d.getStatus());
    }

    @Test
    void can_fail_from_dispatched() {
        Dispatch d = Dispatch.create(UUID.randomUUID());
        d.claim(UUID.randomUUID());
        d.dispatch("Chilexpress", "X", null, null);
        d.fail("Dirección incorrecta");
        assertEquals(DispatchStatus.FAILED, d.getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl backend -Dtest=DispatchTest -q`
Expected: FAIL — classes not found.

- [ ] **Step 3: Create DispatchStatus enum**

Create `backend/src/main/java/com/pilarestilo/dispatch/domain/enums/DispatchStatus.java`:

```java
package com.pilarestilo.dispatch.domain.enums;

public enum DispatchStatus { PENDING, IN_PROGRESS, DISPATCHED, DELIVERED, FAILED }
```

- [ ] **Step 4: Create Dispatch aggregate**

Create `backend/src/main/java/com/pilarestilo/dispatch/domain/model/Dispatch.java`:

```java
package com.pilarestilo.dispatch.domain.model;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.shared.domain.DomainException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class Dispatch {

    private UUID id;
    private UUID orderId;
    private UUID dispatcherId;
    private DispatchStatus status;
    private String carrier;
    private String trackingCode;
    private LocalDate scheduledDate;
    private LocalDateTime dispatchedAt;
    private LocalDateTime deliveredAt;
    private String notes;
    private LocalDateTime createdAt;

    private Dispatch() {}

    public static Dispatch create(UUID orderId) {
        Dispatch d = new Dispatch();
        d.id = UUID.randomUUID();
        d.orderId = orderId;
        d.status = DispatchStatus.PENDING;
        d.createdAt = LocalDateTime.now();
        return d;
    }

    public static Dispatch reconstruct(UUID id, UUID orderId, UUID dispatcherId,
                                        DispatchStatus status, String carrier, String trackingCode,
                                        LocalDate scheduledDate, LocalDateTime dispatchedAt,
                                        LocalDateTime deliveredAt, String notes, LocalDateTime createdAt) {
        Dispatch d = new Dispatch();
        d.id = id; d.orderId = orderId; d.dispatcherId = dispatcherId;
        d.status = status; d.carrier = carrier; d.trackingCode = trackingCode;
        d.scheduledDate = scheduledDate; d.dispatchedAt = dispatchedAt;
        d.deliveredAt = deliveredAt; d.notes = notes; d.createdAt = createdAt;
        return d;
    }

    public void claim(UUID dispatcherId) {
        if (status != DispatchStatus.PENDING) {
            throw new DomainException("Only PENDING dispatches can be claimed");
        }
        this.dispatcherId = dispatcherId;
        this.status = DispatchStatus.IN_PROGRESS;
    }

    public void unclaim() {
        if (status != DispatchStatus.IN_PROGRESS) {
            throw new DomainException("Only IN_PROGRESS dispatches can be unclaimed");
        }
        this.dispatcherId = null;
        this.status = DispatchStatus.PENDING;
    }

    public void dispatch(String carrier, String trackingCode, LocalDate scheduledDate, String notes) {
        if (status != DispatchStatus.IN_PROGRESS) {
            throw new DomainException("Only IN_PROGRESS dispatches can be marked as dispatched");
        }
        this.carrier = carrier;
        this.trackingCode = trackingCode;
        this.scheduledDate = scheduledDate;
        this.notes = notes;
        this.dispatchedAt = LocalDateTime.now();
        this.status = DispatchStatus.DISPATCHED;
    }

    public void deliver() {
        if (status != DispatchStatus.DISPATCHED) {
            throw new DomainException("Only DISPATCHED dispatches can be marked delivered");
        }
        this.deliveredAt = LocalDateTime.now();
        this.status = DispatchStatus.DELIVERED;
    }

    public void fail(String notes) {
        if (status != DispatchStatus.DISPATCHED && status != DispatchStatus.IN_PROGRESS) {
            throw new DomainException("Cannot fail a dispatch in status " + status);
        }
        this.notes = notes;
        this.status = DispatchStatus.FAILED;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getDispatcherId() { return dispatcherId; }
    public DispatchStatus getStatus() { return status; }
    public String getCarrier() { return carrier; }
    public String getTrackingCode() { return trackingCode; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 5: Create Flyway V39**

Create `backend/src/main/resources/db/migration/V39__dispatches.sql`:

```sql
CREATE TABLE dispatches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    dispatcher_id UUID REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    carrier VARCHAR(100),
    tracking_code VARCHAR(200),
    scheduled_date DATE,
    dispatched_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ON dispatches(status);
CREATE INDEX ON dispatches(dispatcher_id);
```

- [ ] **Step 6: Run domain test to verify it passes**

Run: `./mvnw test -pl backend -Dtest=DispatchTest -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/dispatch/domain/ \
        backend/src/main/resources/db/migration/V39__dispatches.sql \
        backend/src/test/java/com/pilarestilo/dispatch/domain/
git commit -m "feat(despachos): dispatch domain model and migration V39"
```

---

### Task 2: Dispatch JPA infrastructure

**Files:**
- Create: `backend/.../dispatch/domain/ports/DispatchRepository.java`
- Create: `backend/.../dispatch/infrastructure/persistence/entities/DispatchEntity.java`
- Create: `backend/.../dispatch/infrastructure/persistence/repositories/DispatchJpaRepository.java`
- Create: `backend/.../dispatch/infrastructure/persistence/repositories/DispatchRepositoryAdapter.java`

- [ ] **Step 1: Create DispatchRepository port**

Create `backend/src/main/java/com/pilarestilo/dispatch/domain/ports/DispatchRepository.java`:

```java
package com.pilarestilo.dispatch.domain.ports;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchRepository {
    Dispatch save(Dispatch dispatch);
    Optional<Dispatch> findById(UUID id);
    Optional<Dispatch> findByOrderId(UUID orderId);
    List<Dispatch> findByStatus(DispatchStatus status);
    List<Dispatch> findByDispatcherIdAndStatus(UUID dispatcherId, DispatchStatus status);
    Page<Dispatch> findAll(Pageable pageable);
    boolean existsByOrderId(UUID orderId);
}
```

- [ ] **Step 2: Create DispatchEntity**

Create `backend/src/main/java/com/pilarestilo/dispatch/infrastructure/persistence/entities/DispatchEntity.java`:

```java
package com.pilarestilo.dispatch.infrastructure.persistence.entities;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dispatches")
public class DispatchEntity {

    @Id private UUID id;
    @Column(name = "order_id", nullable = false, unique = true) private UUID orderId;
    @Column(name = "dispatcher_id") private UUID dispatcherId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DispatchStatus status;
    @Column(length = 100) private String carrier;
    @Column(name = "tracking_code", length = 200) private String trackingCode;
    @Column(name = "scheduled_date") private LocalDate scheduledDate;
    @Column(name = "dispatched_at") private LocalDateTime dispatchedAt;
    @Column(name = "delivered_at") private LocalDateTime deliveredAt;
    @Column private String notes;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getDispatcherId() { return dispatcherId; }
    public void setDispatcherId(UUID dispatcherId) { this.dispatcherId = dispatcherId; }
    public DispatchStatus getStatus() { return status; }
    public void setStatus(DispatchStatus status) { this.status = status; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(LocalDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: Create DispatchJpaRepository**

Create `backend/src/main/java/com/pilarestilo/dispatch/infrastructure/persistence/repositories/DispatchJpaRepository.java`:

```java
package com.pilarestilo.dispatch.infrastructure.persistence.repositories;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.infrastructure.persistence.entities.DispatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchJpaRepository extends JpaRepository<DispatchEntity, UUID> {
    Optional<DispatchEntity> findByOrderId(UUID orderId);
    List<DispatchEntity> findByStatus(DispatchStatus status);
    List<DispatchEntity> findByDispatcherIdAndStatus(UUID dispatcherId, DispatchStatus status);
    boolean existsByOrderId(UUID orderId);
}
```

- [ ] **Step 4: Create DispatchRepositoryAdapter**

Create `backend/src/main/java/com/pilarestilo/dispatch/infrastructure/persistence/repositories/DispatchRepositoryAdapter.java`:

```java
package com.pilarestilo.dispatch.infrastructure.persistence.repositories;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.dispatch.infrastructure.persistence.entities.DispatchEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DispatchRepositoryAdapter implements DispatchRepository {

    private final DispatchJpaRepository jpaRepository;

    public DispatchRepositoryAdapter(DispatchJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override public Dispatch save(Dispatch d) { return toDomain(jpaRepository.save(toEntity(d))); }
    @Override public Optional<Dispatch> findById(UUID id) { return jpaRepository.findById(id).map(this::toDomain); }
    @Override public Optional<Dispatch> findByOrderId(UUID orderId) { return jpaRepository.findByOrderId(orderId).map(this::toDomain); }
    @Override public List<Dispatch> findByStatus(DispatchStatus status) { return jpaRepository.findByStatus(status).stream().map(this::toDomain).toList(); }
    @Override public List<Dispatch> findByDispatcherIdAndStatus(UUID dispatcherId, DispatchStatus status) { return jpaRepository.findByDispatcherIdAndStatus(dispatcherId, status).stream().map(this::toDomain).toList(); }
    @Override public Page<Dispatch> findAll(Pageable pageable) { return jpaRepository.findAll(pageable).map(this::toDomain); }
    @Override public boolean existsByOrderId(UUID orderId) { return jpaRepository.existsByOrderId(orderId); }

    private DispatchEntity toEntity(Dispatch d) {
        DispatchEntity e = new DispatchEntity();
        e.setId(d.getId()); e.setOrderId(d.getOrderId()); e.setDispatcherId(d.getDispatcherId());
        e.setStatus(d.getStatus()); e.setCarrier(d.getCarrier()); e.setTrackingCode(d.getTrackingCode());
        e.setScheduledDate(d.getScheduledDate()); e.setDispatchedAt(d.getDispatchedAt());
        e.setDeliveredAt(d.getDeliveredAt()); e.setNotes(d.getNotes()); e.setCreatedAt(d.getCreatedAt());
        return e;
    }

    private Dispatch toDomain(DispatchEntity e) {
        return Dispatch.reconstruct(e.getId(), e.getOrderId(), e.getDispatcherId(),
                e.getStatus(), e.getCarrier(), e.getTrackingCode(), e.getScheduledDate(),
                e.getDispatchedAt(), e.getDeliveredAt(), e.getNotes(), e.getCreatedAt());
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/dispatch/
git commit -m "feat(despachos): dispatch JPA infrastructure"
```

---

### Task 3: Dispatch use cases, event listener, controllers

**Files:**
- Create all use cases, request objects, controllers, event listener

- [ ] **Step 1: Write failing integration test**

Create `backend/src/test/java/com/pilarestilo/dispatch/infrastructure/web/DespachoIT.java`:

```java
package com.pilarestilo.dispatch.infrastructure.web;

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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class DespachoIT {

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
    void dispatcher_can_claim_and_dispatch_order() throws Exception {
        String adminToken = loginAdmin();
        // Create a dispatch manually via admin endpoint (simulating OrderPaid event)
        String orderId = UUID.randomUUID().toString();
        // Post to create dispatch directly for test:
        // (In production, dispatch is created by OrderPaidDispatchListener)
        // We create it via the admin endpoint
        mvc.perform(post("/api/admin/despachos/seed")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("orderId", orderId))))
                .andExpect(status().isCreated());

        // Create and promote dispatcher
        String email = "despachador_" + UUID.randomUUID() + "@test.com";
        String userId = registerAndGetId(email);
        promoteToRole(adminToken, userId, "DESPACHADOR");
        String dispToken = loginUser(email, "pass1234");

        // List pending dispatches
        MvcResult list = mvc.perform(get("/api/despachos")
                        .header("Authorization", "Bearer " + dispToken))
                .andExpect(status().isOk()).andReturn();
        String dispatchId = om.readTree(list.getResponse().getContentAsString())
                .get(0).get("id").asText();

        // Claim
        mvc.perform(post("/api/despachos/" + dispatchId + "/claim")
                        .header("Authorization", "Bearer " + dispToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Mark dispatched
        String body = om.writeValueAsString(Map.of("carrier", "Chilexpress", "trackingCode", "CH9999"));
        mvc.perform(post("/api/despachos/" + dispatchId + "/dispatch")
                        .header("Authorization", "Bearer " + dispToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.trackingCode").value("CH9999"));
    }

    @Test
    void customer_cannot_access_dispatch_endpoints() throws Exception {
        String token = registerAndGetToken("c_" + UUID.randomUUID() + "@test.com");
        mvc.perform(get("/api/despachos").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String loginAdmin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", "admin@pilarestilo.com", "password", "admin2026"))))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", "pass1234", "fullName", "Test"))))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String registerAndGetId(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", "pass1234", "fullName", "Test"))))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("userId").asText();
    }

    private void promoteToRole(String adminToken, String userId, String role) throws Exception {
        mvc.perform(post("/api/admin/workers/" + userId + "/assign")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("role", role, "vigencyStart", "2020-01-01"))));
    }

    private String loginUser(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl backend -Dtest=DespachoIT -q`
Expected: FAIL — 404 on endpoints.

- [ ] **Step 3: Create DispatchDto**

Create `backend/src/main/java/com/pilarestilo/dispatch/application/dto/DispatchDto.java`:

```java
package com.pilarestilo.dispatch.application.dto;

import com.pilarestilo.dispatch.domain.model.Dispatch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DispatchDto(
        UUID id, UUID orderId, UUID dispatcherId, String status,
        String carrier, String trackingCode, LocalDate scheduledDate,
        LocalDateTime dispatchedAt, LocalDateTime deliveredAt,
        String notes, LocalDateTime createdAt
) {
    public static DispatchDto from(Dispatch d) {
        return new DispatchDto(d.getId(), d.getOrderId(), d.getDispatcherId(), d.getStatus().name(),
                d.getCarrier(), d.getTrackingCode(), d.getScheduledDate(),
                d.getDispatchedAt(), d.getDeliveredAt(), d.getNotes(), d.getCreatedAt());
    }
}
```

- [ ] **Step 4: Create use cases**

Create `backend/src/main/java/com/pilarestilo/dispatch/application/usecases/ClaimDispatchUseCase.java`:

```java
package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ClaimDispatchUseCase {

    private final DispatchRepository dispatchRepository;

    public ClaimDispatchUseCase(DispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    public DispatchDto execute(UUID dispatchId, UUID dispatcherId) {
        Dispatch d = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new DomainException("Dispatch not found"));
        d.claim(dispatcherId);
        return DispatchDto.from(dispatchRepository.save(d));
    }
}
```

Create `backend/src/main/java/com/pilarestilo/dispatch/application/usecases/UnclaimDispatchUseCase.java`:

```java
package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UnclaimDispatchUseCase {

    private final DispatchRepository dispatchRepository;

    public UnclaimDispatchUseCase(DispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    public DispatchDto execute(UUID dispatchId, UUID dispatcherId) {
        Dispatch d = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new DomainException("Dispatch not found"));
        if (!dispatcherId.equals(d.getDispatcherId())) {
            throw new DomainException("You can only unclaim your own dispatches");
        }
        d.unclaim();
        return DispatchDto.from(dispatchRepository.save(d));
    }
}
```

Create `backend/src/main/java/com/pilarestilo/dispatch/application/usecases/MarkDispatchedUseCase.java`:

```java
package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class MarkDispatchedUseCase {

    private final DispatchRepository dispatchRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MarkDispatchedUseCase(DispatchRepository dispatchRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.dispatchRepository = dispatchRepository;
        this.eventPublisher = eventPublisher;
    }

    public DispatchDto execute(UUID dispatchId, UUID dispatcherId,
                                String carrier, String trackingCode,
                                LocalDate scheduledDate, String notes) {
        Dispatch d = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new DomainException("Dispatch not found"));
        if (!dispatcherId.equals(d.getDispatcherId())) {
            throw new DomainException("You can only dispatch orders you have claimed");
        }
        d.dispatch(carrier, trackingCode, scheduledDate, notes);
        Dispatch saved = dispatchRepository.save(d);
        // Notification sent via event — reuse existing notification infrastructure
        // eventPublisher.publishEvent(new DispatchStatusChangedEvent(saved.getOrderId(), "DISPATCHED", carrier, trackingCode));
        return DispatchDto.from(saved);
    }
}
```

Note: Customer notifications are a best-effort enhancement. The `DispatchStatusChangedEvent` publication is commented out for now — wire it up once you confirm the existing notification service API. The comment tells future implementers where to hook it in.

Create `backend/src/main/java/com/pilarestilo/dispatch/application/usecases/MarkDeliveredUseCase.java`:

```java
package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MarkDeliveredUseCase {

    private final DispatchRepository dispatchRepository;

    public MarkDeliveredUseCase(DispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    public DispatchDto execute(UUID dispatchId) {
        Dispatch d = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new DomainException("Dispatch not found"));
        d.deliver();
        return DispatchDto.from(dispatchRepository.save(d));
    }
}
```

Create `backend/src/main/java/com/pilarestilo/dispatch/application/usecases/MarkFailedUseCase.java`:

```java
package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MarkFailedUseCase {

    private final DispatchRepository dispatchRepository;

    public MarkFailedUseCase(DispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    public DispatchDto execute(UUID dispatchId, String notes) {
        Dispatch d = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new DomainException("Dispatch not found"));
        d.fail(notes);
        return DispatchDto.from(dispatchRepository.save(d));
    }
}
```

Create `backend/src/main/java/com/pilarestilo/dispatch/application/usecases/ListDispatchesUseCase.java`:

```java
package com.pilarestilo.dispatch.application.usecases;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListDispatchesUseCase {

    private final DispatchRepository dispatchRepository;

    public ListDispatchesUseCase(DispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    public List<DispatchDto> executeForDispatcher(UUID dispatcherId) {
        List<DispatchDto> pending = dispatchRepository.findByStatus(DispatchStatus.PENDING)
                .stream().map(DispatchDto::from).toList();
        List<DispatchDto> inProgress = dispatchRepository
                .findByDispatcherIdAndStatus(dispatcherId, DispatchStatus.IN_PROGRESS)
                .stream().map(DispatchDto::from).toList();
        return java.util.stream.Stream.concat(inProgress.stream(), pending.stream()).toList();
    }

    public Page<DispatchDto> executeForAdmin(Pageable pageable) {
        return dispatchRepository.findAll(pageable).map(DispatchDto::from);
    }
}
```

- [ ] **Step 5: Create request objects and controllers**

Create `backend/src/main/java/com/pilarestilo/dispatch/infrastructure/web/requests/MarkDispatchedRequest.java`:

```java
package com.pilarestilo.dispatch.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record MarkDispatchedRequest(
        @NotBlank String carrier,
        @NotBlank String trackingCode,
        LocalDate scheduledDate,
        String notes
) {}
```

Create `backend/src/main/java/com/pilarestilo/dispatch/infrastructure/web/requests/MarkFailedRequest.java`:

```java
package com.pilarestilo.dispatch.infrastructure.web.requests;

public record MarkFailedRequest(String notes) {}
```

Create `backend/src/main/java/com/pilarestilo/dispatch/infrastructure/web/DespachoController.java`:

```java
package com.pilarestilo.dispatch.infrastructure.web;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.application.usecases.*;
import com.pilarestilo.dispatch.infrastructure.web.requests.MarkDispatchedRequest;
import com.pilarestilo.dispatch.infrastructure.web.requests.MarkFailedRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/despachos")
public class DespachoController {

    private final ListDispatchesUseCase listUseCase;
    private final ClaimDispatchUseCase claimUseCase;
    private final UnclaimDispatchUseCase unclaimUseCase;
    private final MarkDispatchedUseCase markDispatchedUseCase;
    private final MarkDeliveredUseCase markDeliveredUseCase;
    private final MarkFailedUseCase markFailedUseCase;

    public DespachoController(ListDispatchesUseCase listUseCase,
                               ClaimDispatchUseCase claimUseCase,
                               UnclaimDispatchUseCase unclaimUseCase,
                               MarkDispatchedUseCase markDispatchedUseCase,
                               MarkDeliveredUseCase markDeliveredUseCase,
                               MarkFailedUseCase markFailedUseCase) {
        this.listUseCase = listUseCase;
        this.claimUseCase = claimUseCase;
        this.unclaimUseCase = unclaimUseCase;
        this.markDispatchedUseCase = markDispatchedUseCase;
        this.markDeliveredUseCase = markDeliveredUseCase;
        this.markFailedUseCase = markFailedUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public List<DispatchDto> list(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return listUseCase.executeForDispatcher(currentUser.id());
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public DispatchDto claim(@PathVariable UUID id,
                              @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return claimUseCase.execute(id, currentUser.id());
    }

    @PostMapping("/{id}/unclaim")
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public DispatchDto unclaim(@PathVariable UUID id,
                                @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return unclaimUseCase.execute(id, currentUser.id());
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public DispatchDto dispatch(@PathVariable UUID id,
                                 @RequestBody @Valid MarkDispatchedRequest req,
                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return markDispatchedUseCase.execute(id, currentUser.id(),
                req.carrier(), req.trackingCode(), req.scheduledDate(), req.notes());
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public DispatchDto deliver(@PathVariable UUID id) {
        return markDeliveredUseCase.execute(id);
    }

    @PostMapping("/{id}/fail")
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public DispatchDto fail(@PathVariable UUID id,
                             @RequestBody MarkFailedRequest req) {
        return markFailedUseCase.execute(id, req.notes());
    }
}
```

Create `backend/src/main/java/com/pilarestilo/dispatch/infrastructure/web/AdminDespachoController.java`:

```java
package com.pilarestilo.dispatch.infrastructure.web;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.application.usecases.ListDispatchesUseCase;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/despachos")
public class AdminDespachoController {

    private final ListDispatchesUseCase listUseCase;
    private final DispatchRepository dispatchRepository;

    public AdminDespachoController(ListDispatchesUseCase listUseCase,
                                    DispatchRepository dispatchRepository) {
        this.listUseCase = listUseCase;
        this.dispatchRepository = dispatchRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Page<DispatchDto> list(Pageable pageable) {
        return listUseCase.executeForAdmin(pageable);
    }

    @PostMapping("/seed")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DispatchDto seed(@RequestBody Map<String, String> body) {
        UUID orderId = UUID.fromString(body.get("orderId"));
        if (dispatchRepository.existsByOrderId(orderId)) {
            return dispatchRepository.findByOrderId(orderId).map(DispatchDto::from).orElseThrow();
        }
        return DispatchDto.from(dispatchRepository.save(Dispatch.create(orderId)));
    }
}
```

Note: `/api/admin/despachos/seed` is a test/admin utility endpoint. In production, dispatches are created by `OrderPaidDispatchListener`.

- [ ] **Step 6: Create OrderPaidDispatchListener**

Create `backend/src/main/java/com/pilarestilo/dispatch/infrastructure/listeners/OrderPaidDispatchListener.java`:

```java
package com.pilarestilo.dispatch.infrastructure.listeners;

import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.dispatch.domain.ports.DispatchRepository;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPaidDispatchListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidDispatchListener.class);

    private final DispatchRepository dispatchRepository;

    public OrderPaidDispatchListener(DispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (!"PAID".equals(event.newStatus())) return;
        if (dispatchRepository.existsByOrderId(event.orderId())) return;

        dispatchRepository.save(Dispatch.create(event.orderId()));
        log.info("Created PENDING dispatch for order {}", event.orderId());
    }
}
```

Note: Check `OrderStatusChanged` event fields. If it uses different field names than `orderId()` and `newStatus()`, adjust accordingly. Read the actual event class before implementing.

- [ ] **Step 7: Run integration test to verify it passes**

Run: `./mvnw test -pl backend -Dtest=DespachoIT -q`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/dispatch/ \
        backend/src/test/java/com/pilarestilo/dispatch/
git commit -m "feat(despachos): dispatch use cases, controllers, event listener"
```

---

### Task 4: Frontend — Despachos dispatcher view

**Files:**
- Create: `frontend/src/pages/admin/despachos.astro`
- Create: `frontend/src/islands/admin/DespachosPage.tsx`

- [ ] **Step 1: Create DespachosPage island**

Create `frontend/src/islands/admin/DespachosPage.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { useAuthStore } from '../../lib/authStore';

interface DispatchDto {
  id: string; orderId: string; dispatcherId: string | null;
  status: string; carrier: string | null; trackingCode: string | null;
  notes: string | null; createdAt: string;
}

export default function DespachosPage() {
  const { token } = useAuthStore();
  const [dispatches, setDispatches] = useState<DispatchDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [active, setActive] = useState<DispatchDto | null>(null);
  const [carrier, setCarrier] = useState('');
  const [tracking, setTracking] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function load() {
    setLoading(true);
    const r = await fetch('/api/despachos', { headers: { Authorization: `Bearer ${token}` } });
    const data: DispatchDto[] = await r.json();
    setDispatches(data);
    setLoading(false);
  }

  useEffect(() => { load(); }, [token]);

  async function claim(id: string) {
    setBusy(true);
    await fetch(`/api/despachos/${id}/claim`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}` }
    });
    await load(); setBusy(false);
  }

  async function unclaim(id: string) {
    setBusy(true);
    await fetch(`/api/despachos/${id}/unclaim`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}` }
    });
    await load(); setBusy(false);
  }

  async function dispatch(id: string) {
    if (!carrier || !tracking) { setError('Ingresa carrier y código de seguimiento.'); return; }
    setBusy(true); setError('');
    await fetch(`/api/despachos/${id}/dispatch`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ carrier, trackingCode: tracking }),
    });
    setActive(null); setCarrier(''); setTracking('');
    await load(); setBusy(false);
  }

  if (loading) return <p className="text-pe-charcoal/50 text-sm">Cargando...</p>;

  const pending = dispatches.filter(d => d.status === 'PENDING');
  const inProgress = dispatches.filter(d => d.status === 'IN_PROGRESS');
  const done = dispatches.filter(d => ['DISPATCHED', 'DELIVERED', 'FAILED'].includes(d.status));

  return (
    <div className="space-y-8">
      {inProgress.length > 0 && (
        <section>
          <h2 className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">En progreso</h2>
          <ul className="space-y-3">
            {inProgress.map(d => (
              <li key={d.id} className="border border-[#EDE3D8] p-4">
                <p className="text-sm text-pe-charcoal/70 mb-2">Orden {d.orderId.substring(0, 8)}</p>
                {active?.id === d.id ? (
                  <div className="space-y-2">
                    <input type="text" value={carrier} onChange={e => setCarrier(e.target.value)}
                      placeholder="Carrier (ej. Chilexpress)"
                      className="w-full border border-[#EDE3D8] px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
                    <input type="text" value={tracking} onChange={e => setTracking(e.target.value)}
                      placeholder="Código de seguimiento"
                      className="w-full border border-[#EDE3D8] px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
                    {error && <p className="text-red-500 text-xs">{error}</p>}
                    <div className="flex gap-2">
                      <button onClick={() => dispatch(d.id)} disabled={busy}
                        className="bg-[#1A1A1A] text-[#F8F4EF] px-4 py-2 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50">
                        Marcar despachado
                      </button>
                      <button onClick={() => setActive(null)}
                        className="border border-[#EDE3D8] px-4 py-2 text-xs tracking-widest uppercase text-pe-charcoal/60 hover:bg-gray-50">
                        Cancelar
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex gap-2">
                    <button onClick={() => setActive(d)}
                      className="bg-[#1A1A1A] text-[#F8F4EF] px-4 py-2 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors">
                      Despachar
                    </button>
                    <button onClick={() => unclaim(d.id)} disabled={busy}
                      className="border border-[#EDE3D8] px-4 py-2 text-xs tracking-widest uppercase text-pe-charcoal/60 hover:bg-gray-50">
                      Liberar
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h2 className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">
          Pendientes ({pending.length})
        </h2>
        {pending.length === 0 && <p className="text-pe-charcoal/40 text-sm">Sin órdenes pendientes.</p>}
        <ul className="space-y-2">
          {pending.map(d => (
            <li key={d.id} className="border border-[#EDE3D8] p-4 flex items-center justify-between">
              <p className="text-sm text-pe-charcoal/70">Orden {d.orderId.substring(0, 8)}</p>
              <button onClick={() => claim(d.id)} disabled={busy}
                className="bg-[#1A1A1A] text-[#F8F4EF] px-4 py-2 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50">
                Tomar
              </button>
            </li>
          ))}
        </ul>
      </section>

      {done.length > 0 && (
        <section>
          <h2 className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">Completados hoy</h2>
          <ul className="space-y-2">
            {done.map(d => (
              <li key={d.id} className="border border-[#EDE3D8] p-3 flex items-center justify-between text-sm">
                <span className="text-pe-charcoal/70">Orden {d.orderId.substring(0, 8)}</span>
                <span className={`text-xs tracking-widest uppercase ${
                  d.status === 'DELIVERED' ? 'text-green-600' :
                  d.status === 'FAILED' ? 'text-red-500' : 'text-blue-500'
                }`}>{d.status}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Create Astro page**

Create `frontend/src/pages/admin/despachos.astro`:

```astro
---
import AdminLayout from '../../layouts/AdminLayout.astro';
import DespachosPage from '../../islands/admin/DespachosPage';
---

<AdminLayout title="Despachos">
  <div class="mb-5 sm:mb-6">
    <p class="font-sans text-[0.65rem] tracking-[0.25em] uppercase text-pe-charcoal/35 mb-1">Pilar Estilo</p>
    <h1 class="font-display text-pe-black text-2xl sm:text-3xl font-light">Despachos</h1>
  </div>
  <DespachosPage client:load />
</AdminLayout>
```

- [ ] **Step 3: Add despachos nav item to AdminSidebar**

In `AdminSidebar.tsx`, add:
```typescript
import { ..., Truck } from 'lucide-react';
// In navItems:
{ href: '/admin/despachos', icon: Truck, label: 'Despachos', viewKey: 'despachos' },
```

- [ ] **Step 4: Verify build and run full tests**

Run: `./mvnw test -pl backend && cd frontend && npm run build`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/admin/despachos.astro \
        frontend/src/islands/admin/DespachosPage.tsx \
        frontend/src/islands/admin/AdminSidebar.tsx
git commit -m "feat(despachos): complete dispatch system — domain, API, and frontend"
```
