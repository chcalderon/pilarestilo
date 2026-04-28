# Dashboard Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Role-aware dashboard endpoint + frontend stat cards + 7-day Recharts bar chart, one API call, no new tables.

**Architecture:** Single `GET /api/dashboard/stats` endpoint returns a role-shaped JSON payload; backend computes via JPQL/native queries on existing `orders`, `cash_registers`, `dispatches`, `users` tables. Frontend `DashboardPage.tsx` island reads the JWT role claim to decide layout, renders skeleton → cards → optional chart.

**Tech Stack:** Spring Boot (hexagonal), JPQL native queries, Spring Security `@AuthenticationPrincipal`, React 18 + Zustand, Recharts `BarChart`, Tailwind CSS.

---

## File Map

### Backend

| File | Action |
|---|---|
| `backend/src/main/java/com/pilarestilo/dashboard/domain/model/DashboardStats.java` | Create — sealed interface with role-specific record implementations |
| `backend/src/main/java/com/pilarestilo/dashboard/application/port/out/DashboardStatsRepository.java` | Create — port with query methods |
| `backend/src/main/java/com/pilarestilo/dashboard/application/usecase/GetDashboardStatsUseCase.java` | Create — dispatches to correct stats builder by role |
| `backend/src/main/java/com/pilarestilo/dashboard/infrastructure/persistence/DashboardStatsRepositoryAdapter.java` | Create — native query implementations |
| `backend/src/main/java/com/pilarestilo/dashboard/infrastructure/web/DashboardController.java` | Create — `GET /api/dashboard/stats` |
| `backend/src/main/java/com/pilarestilo/dashboard/infrastructure/web/dto/DashboardStatsResponse.java` | Create — Jackson-serializable DTO |
| `backend/src/test/java/com/pilarestilo/dashboard/infrastructure/web/DashboardControllerIT.java` | Create — Testcontainers + MockMvc integration tests |

### Frontend

| File | Action |
|---|---|
| `frontend/src/islands/admin/DashboardPage.tsx` | Modify (replace existing stub or create) — role-aware stat cards + chart |
| `frontend/src/pages/[locale]/dashboard.astro` | Modify — mount `DashboardPage` island |

---

### Task 1: Domain model for dashboard stats

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/dashboard/domain/model/DashboardStats.java`

- [ ] **Step 1: Create the sealed domain model**

```java
// backend/src/main/java/com/pilarestilo/dashboard/domain/model/DashboardStats.java
package com.pilarestilo.dashboard.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public sealed interface DashboardStats
        permits DashboardStats.AdminStats, DashboardStats.SellerStats,
                DashboardStats.DespachadorStats, DashboardStats.AdministracionStats {

    record SalesTotal(BigDecimal amount, int orderCount) {}

    record TopProduct(String productId, String name, int unitsSold) {}

    record DailyRevenue(LocalDate date, BigDecimal amount) {}

    record CajaSnapshot(
            String status,
            LocalDateTime openedAt,
            BigDecimal expectedBalance,
            int saleCount,
            BigDecimal saleTotal
    ) {}

    record LastSale(BigDecimal amount, LocalDateTime recordedAt) {}

    record ExpiringWorker(String userId, String fullName, LocalDate vigencyEnd) {}

    record AdminStats(
            SalesTotal dailySales,
            SalesTotal weeklySales,
            int openCashRegisters,
            int pendingDispatches,
            int inProgressDispatches,
            List<TopProduct> topProducts,
            List<DailyRevenue> dailyRevenueSeries
    ) implements DashboardStats {}

    record SellerStats(
            CajaSnapshot currentCaja,
            LastSale lastSale
    ) implements DashboardStats {}

    record DespachadorStats(
            int pendingDispatches,
            int myDispatchedToday,
            int myInProgress
    ) implements DashboardStats {}

    record AdministracionStats(
            int activeWorkers,
            List<ExpiringWorker> expiringWorkers
    ) implements DashboardStats {}
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/dashboard/domain/model/DashboardStats.java
git commit -m "feat(dashboard): add DashboardStats sealed domain model"
```

---

### Task 2: Repository port + native query adapter

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/dashboard/application/port/out/DashboardStatsRepository.java`
- Create: `backend/src/main/java/com/pilarestilo/dashboard/infrastructure/persistence/DashboardStatsRepositoryAdapter.java`

- [ ] **Step 1: Write failing tests**

```java
// backend/src/test/java/com/pilarestilo/dashboard/infrastructure/persistence/DashboardStatsRepositoryAdapterIT.java
package com.pilarestilo.dashboard.infrastructure.persistence;

import com.pilarestilo.dashboard.domain.model.DashboardStats;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class DashboardStatsRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("pilarestilo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    DashboardStatsRepository repo;

    @Test
    void adminStats_returnsNonNullSalesTotals() {
        DashboardStats.AdminStats stats = repo.getAdminStats();
        assertThat(stats.dailySales()).isNotNull();
        assertThat(stats.dailySales().amount()).isNotNegative();
        assertThat(stats.dailyRevenueSeries()).hasSizeLessThanOrEqualTo(7);
        assertThat(stats.topProducts()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void sellerStats_returnsSnapshot() {
        UUID sellerId = UUID.randomUUID(); // no open caja — snapshot should handle null
        DashboardStats.SellerStats stats = repo.getSellerStats(sellerId);
        assertThat(stats).isNotNull();
        // currentCaja may be null when no caja open
    }

    @Test
    void despachadorStats_returnsQueues() {
        UUID despachadorId = UUID.randomUUID();
        DashboardStats.DespachadorStats stats = repo.getDespachadorStats(despachadorId);
        assertThat(stats.pendingDispatches()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void administracionStats_returnsWorkerCounts() {
        DashboardStats.AdministracionStats stats = repo.getAdministracionStats();
        assertThat(stats.activeWorkers()).isGreaterThanOrEqualTo(0);
        assertThat(stats.expiringWorkers()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./mvnw test -pl backend -Dtest=DashboardStatsRepositoryAdapterIT -q
```
Expected: FAIL — `DashboardStatsRepository` not found.

- [ ] **Step 3: Create the port interface**

```java
// backend/src/main/java/com/pilarestilo/dashboard/application/port/out/DashboardStatsRepository.java
package com.pilarestilo.dashboard.application.port.out;

import com.pilarestilo.dashboard.domain.model.DashboardStats;
import java.util.UUID;

public interface DashboardStatsRepository {
    DashboardStats.AdminStats getAdminStats();
    DashboardStats.SellerStats getSellerStats(UUID sellerId);
    DashboardStats.DespachadorStats getDespachadorStats(UUID despachadorId);
    DashboardStats.AdministracionStats getAdministracionStats();
}
```

- [ ] **Step 4: Create the adapter with native queries**

```java
// backend/src/main/java/com/pilarestilo/dashboard/infrastructure/persistence/DashboardStatsRepositoryAdapter.java
package com.pilarestilo.dashboard.infrastructure.persistence;

import com.pilarestilo.dashboard.application.port.out.DashboardStatsRepository;
import com.pilarestilo.dashboard.domain.model.DashboardStats;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
class DashboardStatsRepositoryAdapter implements DashboardStatsRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public DashboardStats.AdminStats getAdminStats() {
        // Daily sales
        Object[] daily = (Object[]) em.createNativeQuery(
                "SELECT COALESCE(SUM(total_amount), 0), COUNT(*) FROM orders WHERE status = 'PAID' AND DATE(paid_at) = CURRENT_DATE"
        ).getSingleResult();
        var dailySales = new DashboardStats.SalesTotal(
                toBigDecimal(daily[0]), ((Number) daily[1]).intValue());

        // Weekly sales
        Object[] weekly = (Object[]) em.createNativeQuery(
                "SELECT COALESCE(SUM(total_amount), 0), COUNT(*) FROM orders WHERE status = 'PAID' AND paid_at >= DATE_TRUNC('week', CURRENT_DATE)"
        ).getSingleResult();
        var weeklySales = new DashboardStats.SalesTotal(
                toBigDecimal(weekly[0]), ((Number) weekly[1]).intValue());

        // Open cash registers
        int openCashRegisters = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM cash_registers WHERE status = 'OPEN'"
        ).getSingleResult()).intValue();

        // Pending / in-progress dispatches
        int pendingDispatches = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM dispatches WHERE status = 'PENDING'"
        ).getSingleResult()).intValue();
        int inProgressDispatches = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM dispatches WHERE status = 'IN_PROGRESS'"
        ).getSingleResult()).intValue();

        // Top 5 products by units sold this week
        @SuppressWarnings("unchecked")
        List<Object[]> topRows = em.createNativeQuery(
                """
                SELECT oi.product_id, p.name, SUM(oi.quantity) as units
                FROM order_items oi
                JOIN products p ON p.id = oi.product_id
                JOIN orders o ON o.id = oi.order_id
                WHERE o.status = 'PAID'
                  AND o.paid_at >= DATE_TRUNC('week', CURRENT_DATE)
                GROUP BY oi.product_id, p.name
                ORDER BY units DESC
                LIMIT 5
                """
        ).getResultList();
        List<DashboardStats.TopProduct> topProducts = topRows.stream()
                .map(r -> new DashboardStats.TopProduct(
                        r[0].toString(), r[1].toString(), ((Number) r[2]).intValue()))
                .toList();

        // 7-day daily revenue series
        @SuppressWarnings("unchecked")
        List<Object[]> seriesRows = em.createNativeQuery(
                """
                SELECT DATE(paid_at) as day, COALESCE(SUM(total_amount), 0) as revenue
                FROM orders
                WHERE status = 'PAID'
                  AND paid_at >= CURRENT_DATE - INTERVAL '6 days'
                GROUP BY day
                ORDER BY day
                """
        ).getResultList();
        List<DashboardStats.DailyRevenue> dailyRevenueSeries = seriesRows.stream()
                .map(r -> new DashboardStats.DailyRevenue(
                        ((java.sql.Date) r[0]).toLocalDate(), toBigDecimal(r[1])))
                .toList();

        return new DashboardStats.AdminStats(
                dailySales, weeklySales, openCashRegisters,
                pendingDispatches, inProgressDispatches, topProducts, dailyRevenueSeries);
    }

    @Override
    public DashboardStats.SellerStats getSellerStats(UUID sellerId) {
        // Find open caja for this seller
        @SuppressWarnings("unchecked")
        List<Object[]> cajaRows = em.createNativeQuery(
                """
                SELECT cr.status, cr.opened_at, cr.opening_balance,
                       COALESCE(SUM(CASE WHEN cm.type = 'SALE' THEN cm.amount ELSE 0 END), 0) as sale_total,
                       COUNT(CASE WHEN cm.type = 'SALE' THEN 1 END) as sale_count
                FROM cash_registers cr
                LEFT JOIN cash_movements cm ON cm.cash_register_id = cr.id
                WHERE cr.seller_id = :sellerId AND cr.status = 'OPEN'
                GROUP BY cr.id, cr.status, cr.opened_at, cr.opening_balance
                LIMIT 1
                """
        ).setParameter("sellerId", sellerId).getResultList();

        DashboardStats.CajaSnapshot snapshot = cajaRows.isEmpty() ? null :
                buildSnapshot(cajaRows.get(0));

        // Last sale movement across all cajas for this seller today
        @SuppressWarnings("unchecked")
        List<Object[]> lastSaleRows = em.createNativeQuery(
                """
                SELECT cm.amount, cm.recorded_at
                FROM cash_movements cm
                JOIN cash_registers cr ON cr.id = cm.cash_register_id
                WHERE cr.seller_id = :sellerId
                  AND cm.type = 'SALE'
                  AND DATE(cm.recorded_at) = CURRENT_DATE
                ORDER BY cm.recorded_at DESC
                LIMIT 1
                """
        ).setParameter("sellerId", sellerId).getResultList();

        DashboardStats.LastSale lastSale = lastSaleRows.isEmpty() ? null :
                new DashboardStats.LastSale(
                        toBigDecimal(lastSaleRows.get(0)[0]),
                        ((java.sql.Timestamp) lastSaleRows.get(0)[1]).toLocalDateTime());

        return new DashboardStats.SellerStats(snapshot, lastSale);
    }

    @Override
    public DashboardStats.DespachadorStats getDespachadorStats(UUID despachadorId) {
        int pending = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM dispatches WHERE status = 'PENDING'"
        ).getSingleResult()).intValue();

        int myToday = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM dispatches WHERE claimed_by = :id AND status = 'DISPATCHED' AND DATE(dispatched_at) = CURRENT_DATE"
        ).setParameter("id", despachadorId).getSingleResult()).intValue();

        int myInProgress = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM dispatches WHERE claimed_by = :id AND status = 'IN_PROGRESS'"
        ).setParameter("id", despachadorId).getSingleResult()).intValue();

        return new DashboardStats.DespachadorStats(pending, myToday, myInProgress);
    }

    @Override
    public DashboardStats.AdministracionStats getAdministracionStats() {
        int active = ((Number) em.createNativeQuery(
                """
                SELECT COUNT(*) FROM users
                WHERE role IN ('SUPERVISOR','ADMINISTRACION','DESPACHADOR','SELLER')
                  AND (worker_vigency_start IS NULL OR worker_vigency_start <= CURRENT_DATE)
                  AND (worker_vigency_end IS NULL OR worker_vigency_end >= CURRENT_DATE)
                """
        ).getSingleResult()).intValue();

        @SuppressWarnings("unchecked")
        List<Object[]> expiring = em.createNativeQuery(
                """
                SELECT id, full_name, worker_vigency_end
                FROM users
                WHERE role IN ('SUPERVISOR','ADMINISTRACION','DESPACHADOR','SELLER')
                  AND worker_vigency_end BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '7 days'
                ORDER BY worker_vigency_end
                """
        ).getResultList();

        List<DashboardStats.ExpiringWorker> expiringWorkers = expiring.stream()
                .map(r -> new DashboardStats.ExpiringWorker(
                        r[0].toString(),
                        r[1] != null ? r[1].toString() : "",
                        ((java.sql.Date) r[2]).toLocalDate()))
                .toList();

        return new DashboardStats.AdministracionStats(active, expiringWorkers);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private DashboardStats.CajaSnapshot buildSnapshot(Object[] row) {
        BigDecimal openingBalance = toBigDecimal(row[2]);
        BigDecimal saleTotal = toBigDecimal(row[3]);
        int saleCount = ((Number) row[4]).intValue();
        BigDecimal expectedBalance = openingBalance.add(saleTotal);
        return new DashboardStats.CajaSnapshot(
                row[0].toString(),
                ((java.sql.Timestamp) row[1]).toLocalDateTime(),
                expectedBalance,
                saleCount,
                saleTotal);
    }
}
```

- [ ] **Step 5: Run tests**

```
./mvnw test -pl backend -Dtest=DashboardStatsRepositoryAdapterIT -q
```
Expected: 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/dashboard/
git add backend/src/test/java/com/pilarestilo/dashboard/infrastructure/persistence/DashboardStatsRepositoryAdapterIT.java
git commit -m "feat(dashboard): add DashboardStatsRepository port + native query adapter"
```

---

### Task 3: Use case + controller

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/dashboard/application/usecase/GetDashboardStatsUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/dashboard/infrastructure/web/dto/DashboardStatsResponse.java`
- Create: `backend/src/main/java/com/pilarestilo/dashboard/infrastructure/web/DashboardController.java`

- [ ] **Step 1: Write failing controller test**

```java
// backend/src/test/java/com/pilarestilo/dashboard/infrastructure/web/DashboardControllerIT.java
package com.pilarestilo.dashboard.infrastructure.web;

import com.pilarestilo.shared.auth.infrastructure.JwtTokenProvider;
import com.pilarestilo.user.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DashboardControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("pilarestilo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private String token(UserRole role) {
        UUID id = UUID.randomUUID();
        return "Bearer " + jwtTokenProvider.generateAccessToken(id, "test@test.com", role, List.of());
    }

    @Test
    void adminGetsAdminStats() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", token(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.dailySales").exists())
                .andExpect(jsonPath("$.dailyRevenueSeries").isArray());
    }

    @Test
    void sellerGetsSellerStats() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", token(UserRole.SELLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    void despachadorGetsDespachadorStats() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", token(UserRole.DESPACHADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DESPACHADOR"))
                .andExpect(jsonPath("$.pendingDispatches").isNumber());
    }

    @Test
    void administracionGetsAdministracionStats() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats").header("Authorization", token(UserRole.ADMINISTRACION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMINISTRACION"))
                .andExpect(jsonPath("$.activeWorkers").isNumber());
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./mvnw test -pl backend -Dtest=DashboardControllerIT -q
```
Expected: FAIL — `DashboardController` not found / 404.

- [ ] **Step 3: Create the use case**

```java
// backend/src/main/java/com/pilarestilo/dashboard/application/usecase/GetDashboardStatsUseCase.java
package com.pilarestilo.dashboard.application.usecase;

import com.pilarestilo.dashboard.application.port.out.DashboardStatsRepository;
import com.pilarestilo.dashboard.domain.model.DashboardStats;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.user.domain.enums.UserRole;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetDashboardStatsUseCase {

    private final DashboardStatsRepository repo;

    public GetDashboardStatsUseCase(DashboardStatsRepository repo) {
        this.repo = repo;
    }

    public DashboardStats execute(AuthenticatedUser caller) {
        return switch (caller.role()) {
            case ADMIN, SUPERVISOR -> repo.getAdminStats();
            case SELLER -> repo.getSellerStats(caller.id());
            case DESPACHADOR -> repo.getDespachadorStats(caller.id());
            case ADMINISTRACION -> repo.getAdministracionStats();
            case CUSTOMER -> throw new IllegalStateException("CUSTOMER has no dashboard");
        };
    }
}
```

- [ ] **Step 4: Create the response DTO**

```java
// backend/src/main/java/com/pilarestilo/dashboard/infrastructure/web/dto/DashboardStatsResponse.java
package com.pilarestilo.dashboard.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pilarestilo.dashboard.domain.model.DashboardStats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardStatsResponse(
        String role,
        // ADMIN / SUPERVISOR fields
        SalesTotal dailySales,
        SalesTotal weeklySales,
        Integer openCashRegisters,
        Integer pendingDispatches,
        Integer inProgressDispatches,
        List<TopProduct> topProducts,
        List<DailyRevenue> dailyRevenueSeries,
        // SELLER fields
        CajaSnapshot currentCaja,
        LastSale lastSale,
        // DESPACHADOR fields (pendingDispatches already above)
        Integer myDispatchedToday,
        Integer myInProgress,
        // ADMINISTRACION fields
        Integer activeWorkers,
        List<ExpiringWorker> expiringWorkers
) {
    public record SalesTotal(BigDecimal amount, int orderCount) {}
    public record TopProduct(String productId, String name, int unitsSold) {}
    public record DailyRevenue(LocalDate date, BigDecimal amount) {}
    public record CajaSnapshot(String status, LocalDateTime openedAt, BigDecimal expectedBalance, int saleCount, BigDecimal saleTotal) {}
    public record LastSale(BigDecimal amount, LocalDateTime recordedAt) {}
    public record ExpiringWorker(String userId, String fullName, LocalDate vigencyEnd) {}

    public static DashboardStatsResponse from(DashboardStats stats) {
        return switch (stats) {
            case DashboardStats.AdminStats a -> new DashboardStatsResponse(
                    "ADMIN",
                    new SalesTotal(a.dailySales().amount(), a.dailySales().orderCount()),
                    new SalesTotal(a.weeklySales().amount(), a.weeklySales().orderCount()),
                    a.openCashRegisters(), a.pendingDispatches(), a.inProgressDispatches(),
                    a.topProducts().stream().map(p -> new TopProduct(p.productId(), p.name(), p.unitsSold())).toList(),
                    a.dailyRevenueSeries().stream().map(d -> new DailyRevenue(d.date(), d.amount())).toList(),
                    null, null, null, null, null, null
            );
            case DashboardStats.SellerStats s -> new DashboardStatsResponse(
                    "SELLER", null, null, null, null, null, null, null,
                    s.currentCaja() == null ? null : new CajaSnapshot(
                            s.currentCaja().status(), s.currentCaja().openedAt(),
                            s.currentCaja().expectedBalance(), s.currentCaja().saleCount(), s.currentCaja().saleTotal()),
                    s.lastSale() == null ? null : new LastSale(s.lastSale().amount(), s.lastSale().recordedAt()),
                    null, null, null, null
            );
            case DashboardStats.DespachadorStats d -> new DashboardStatsResponse(
                    "DESPACHADOR", null, null, null,
                    d.pendingDispatches(), null, null, null, null, null,
                    d.myDispatchedToday(), d.myInProgress(), null, null
            );
            case DashboardStats.AdministracionStats adm -> new DashboardStatsResponse(
                    "ADMINISTRACION", null, null, null, null, null, null, null, null, null, null, null,
                    adm.activeWorkers(),
                    adm.expiringWorkers().stream().map(w -> new ExpiringWorker(w.userId(), w.fullName(), w.vigencyEnd())).toList()
            );
        };
    }
}
```

- [ ] **Step 5: Create the controller**

```java
// backend/src/main/java/com/pilarestilo/dashboard/infrastructure/web/DashboardController.java
package com.pilarestilo.dashboard.infrastructure.web;

import com.pilarestilo.dashboard.application.usecase.GetDashboardStatsUseCase;
import com.pilarestilo.dashboard.infrastructure.web.dto.DashboardStatsResponse;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final GetDashboardStatsUseCase useCase;

    public DashboardController(GetDashboardStatsUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','SELLER','DESPACHADOR','ADMINISTRACION')")
    public ResponseEntity<DashboardStatsResponse> getStats(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        var stats = useCase.execute(caller);
        return ResponseEntity.ok(DashboardStatsResponse.from(stats));
    }
}
```

- [ ] **Step 6: Run tests**

```
./mvnw test -pl backend -Dtest=DashboardControllerIT -q
```
Expected: 5 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/dashboard/
git add backend/src/test/java/com/pilarestilo/dashboard/
git commit -m "feat(dashboard): add GetDashboardStatsUseCase + DashboardController"
```

---

### Task 4: Frontend DashboardPage island

**Files:**
- Modify: `frontend/src/islands/admin/DashboardPage.tsx`
- Modify: `frontend/src/pages/[locale]/dashboard.astro`

Note: Install Recharts if not already present — `npm install recharts` in the `frontend/` directory.

- [ ] **Step 1: Check if Recharts is installed**

```bash
cd frontend && grep '"recharts"' package.json
```

If not found:
```bash
npm install recharts
```

- [ ] **Step 2: Create/replace `DashboardPage.tsx`**

```tsx
// frontend/src/islands/admin/DashboardPage.tsx
import { useEffect, useState } from "react";
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from "recharts";
import { useAuthStore } from "@/lib/authStore";
import { apiFetch } from "@/lib/api";

interface SalesTotal { amount: number; orderCount: number }
interface TopProduct { productId: string; name: string; unitsSold: number }
interface DailyRevenue { date: string; amount: number }
interface CajaSnapshot { status: string; openedAt: string; expectedBalance: number; saleCount: number; saleTotal: number }
interface LastSale { amount: number; recordedAt: string }
interface ExpiringWorker { userId: string; fullName: string; vigencyEnd: string }

interface AdminData {
  role: "ADMIN" | "SUPERVISOR";
  dailySales: SalesTotal;
  weeklySales: SalesTotal;
  openCashRegisters: number;
  pendingDispatches: number;
  inProgressDispatches: number;
  topProducts: TopProduct[];
  dailyRevenueSeries: DailyRevenue[];
}

interface SellerData {
  role: "SELLER";
  currentCaja: CajaSnapshot | null;
  lastSale: LastSale | null;
}

interface DespachadorData {
  role: "DESPACHADOR";
  pendingDispatches: number;
  myDispatchedToday: number;
  myInProgress: number;
}

interface AdministracionData {
  role: "ADMINISTRACION";
  activeWorkers: number;
  expiringWorkers: ExpiringWorker[];
}

type StatsData = AdminData | SellerData | DespachadorData | AdministracionData;

function formatCLP(amount: number) {
  return new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP" }).format(amount);
}

function StatCard({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="border border-[var(--pe-border)] p-4 flex flex-col gap-1">
      <span className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)]">{label}</span>
      <span className="text-2xl font-bold font-[Cormorant_Garamond,serif]">{value}</span>
      {sub && <span className="text-xs text-[var(--pe-muted)]">{sub}</span>}
    </div>
  );
}

function SkeletonCard() {
  return (
    <div className="border border-[var(--pe-border)] p-4 animate-pulse">
      <div className="h-3 w-24 bg-[var(--pe-border)] rounded mb-2" />
      <div className="h-7 w-32 bg-[var(--pe-border)] rounded" />
    </div>
  );
}

function AdminDashboard({ data }: { data: AdminData }) {
  const chartData = data.dailyRevenueSeries.map(d => ({
    date: d.date.slice(5),
    amount: d.amount / 1000,
  }));

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard label="Ventas hoy" value={formatCLP(data.dailySales.amount)} sub={`${data.dailySales.orderCount} órdenes`} />
        <StatCard label="Ventas semana" value={formatCLP(data.weeklySales.amount)} sub={`${data.weeklySales.orderCount} órdenes`} />
        <StatCard label="Cajas abiertas" value={String(data.openCashRegisters)} />
        <StatCard label="Despachos pendientes" value={String(data.pendingDispatches)} sub={`${data.inProgressDispatches} en progreso`} />
      </div>

      <div className="border border-[var(--pe-border)] p-4">
        <p className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)] mb-4">Ingresos últimos 7 días</p>
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--pe-border)" />
            <XAxis dataKey="date" tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={v => `$${v}K`} tick={{ fontSize: 11 }} />
            <Tooltip formatter={(v: number) => [`$${v}K`, "Ingresos"]} />
            <Bar dataKey="amount" fill="var(--pe-rose, #B76E79)" radius={[2, 2, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {data.topProducts.length > 0 && (
        <div className="border border-[var(--pe-border)] p-4">
          <p className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)] mb-3">Top productos (semana)</p>
          <ul className="space-y-2">
            {data.topProducts.map(p => (
              <li key={p.productId} className="flex justify-between text-sm">
                <span>{p.name}</span>
                <span className="font-medium">{p.unitsSold} uds.</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function SellerDashboard({ data }: { data: SellerData }) {
  const caja = data.currentCaja;
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
      <StatCard
        label="Mi caja hoy"
        value={caja ? caja.status : "Sin caja"}
        sub={caja ? `Saldo esperado: ${formatCLP(caja.expectedBalance)}` : "Abre una caja para comenzar"}
      />
      {caja && (
        <StatCard
          label="Ventas en mi caja"
          value={formatCLP(caja.saleTotal)}
          sub={`${caja.saleCount} ventas`}
        />
      )}
      {data.lastSale && (
        <StatCard
          label="Última venta"
          value={formatCLP(data.lastSale.amount)}
          sub={new Date(data.lastSale.recordedAt).toLocaleTimeString("es-CL", { hour: "2-digit", minute: "2-digit" })}
        />
      )}
    </div>
  );
}

function DespachadorDashboard({ data }: { data: DespachadorData }) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
      <StatCard label="Pendientes" value={String(data.pendingDispatches)} />
      <StatCard label="En progreso (míos)" value={String(data.myInProgress)} />
      <StatCard label="Mis despachos hoy" value={String(data.myDispatchedToday)} />
    </div>
  );
}

function AdministracionDashboard({ data }: { data: AdministracionData }) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <StatCard label="Trabajadores activos" value={String(data.activeWorkers)} />
        <StatCard label="Vencimientos próximos" value={String(data.expiringWorkers.length)} sub="en los próximos 7 días" />
      </div>
      {data.expiringWorkers.length > 0 && (
        <div className="border border-[var(--pe-border)] p-4">
          <p className="text-[10px] tracking-widest uppercase text-[var(--pe-muted)] mb-3">Vigencias por vencer</p>
          <ul className="space-y-2">
            {data.expiringWorkers.map(w => (
              <li key={w.userId} className="flex justify-between text-sm">
                <span>{w.fullName}</span>
                <span className="text-[var(--pe-rose)]">{w.vigencyEnd}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export default function DashboardPage() {
  const [data, setData] = useState<StatsData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const user = useAuthStore(s => s.user);

  useEffect(() => {
    apiFetch<StatsData>("/api/dashboard/stats")
      .then(setData)
      .catch(() => setError("No se pudo cargar el dashboard."));
  }, []);

  if (error) {
    return <p className="text-sm text-red-500 p-4">{error}</p>;
  }

  if (!data) {
    return (
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 p-6">
        {Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} />)}
      </div>
    );
  }

  return (
    <div className="p-6 max-w-5xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold font-[Cormorant_Garamond,serif]">Dashboard</h1>
      {(data.role === "ADMIN" || data.role === "SUPERVISOR") && <AdminDashboard data={data as AdminData} />}
      {data.role === "SELLER" && <SellerDashboard data={data as SellerData} />}
      {data.role === "DESPACHADOR" && <DespachadorDashboard data={data as DespachadorData} />}
      {data.role === "ADMINISTRACION" && <AdministracionDashboard data={data as AdministracionData} />}
    </div>
  );
}
```

- [ ] **Step 3: Update `dashboard.astro` to mount the island**

Find the existing `dashboard.astro` page and replace its body content with:

```astro
---
// frontend/src/pages/[locale]/dashboard.astro
import WorkerLayout from "@/layouts/WorkerLayout.astro";
import DashboardPage from "@/islands/admin/DashboardPage";
---
<WorkerLayout title="Dashboard">
  <DashboardPage client:load />
</WorkerLayout>
```

(If the layout import name differs, match the existing convention in adjacent admin pages.)

- [ ] **Step 4: TypeScript check**

```bash
cd frontend && npx tsc --noEmit
```
Expected: 0 errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/islands/admin/DashboardPage.tsx
git add frontend/src/pages/
git add frontend/package.json frontend/package-lock.json
git commit -m "feat(dashboard): add role-aware DashboardPage island with Recharts bar chart"
```

---

### Task 5: Full test run + verification

- [ ] **Step 1: Run all backend tests**

```
./mvnw test -pl backend -q
```
Expected: all existing tests + new dashboard tests PASS. No regressions.

- [ ] **Step 2: Frontend build**

```bash
cd frontend && npm run build
```
Expected: build completes with 0 errors.

- [ ] **Step 3: Commit if any fixes were needed**

```bash
git add -p
git commit -m "fix(dashboard): address test/build issues"
```
