# Cash Register (Caja) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a cash register workflow where a SELLER opens a personal till, records movements (sales auto-created from paid orders, manual IN/OUT entries), and closes the till with reconciliation. ADMIN/SUPERVISOR can view all cajas.

**Architecture:** New `cashregister` bounded context following the same hexagonal pattern as `order` and `payment`. `CashRegister` is the aggregate root. `CashMovement` is a child entity. An event listener on `OrderStatusChanged` auto-creates SALE movements. Two separate controllers: `CajaController` (SELLER actions) and `AdminCajaController` (read-only for ADMIN/SUPERVISOR). Frontend has a `CajaPage` island for the seller view.

**Prerequisite:** Plan 1 (worker-roles-vigency-permissions) must be complete — SELLER role must exist.

**Tech Stack:** Spring Boot 3, JPA + PostgreSQL, Flyway V38, `OrderStatusChanged` domain event, React + Zustand (frontend).

---

## File Map

**Create (backend):**
- `backend/.../cashregister/domain/model/CashRegister.java`
- `backend/.../cashregister/domain/model/CashMovement.java`
- `backend/.../cashregister/domain/enums/CashRegisterStatus.java`
- `backend/.../cashregister/domain/enums/CashMovementType.java`
- `backend/.../cashregister/domain/ports/CashRegisterRepository.java`
- `backend/.../cashregister/domain/ports/CashMovementRepository.java`
- `backend/.../cashregister/application/dto/CashRegisterDto.java`
- `backend/.../cashregister/application/dto/CashMovementDto.java`
- `backend/.../cashregister/application/usecases/OpenCashRegisterUseCase.java`
- `backend/.../cashregister/application/usecases/CloseCashRegisterUseCase.java`
- `backend/.../cashregister/application/usecases/GetCurrentCashRegisterUseCase.java`
- `backend/.../cashregister/application/usecases/AddCashMovementUseCase.java`
- `backend/.../cashregister/application/usecases/ListCashRegistersUseCase.java`
- `backend/.../cashregister/infrastructure/persistence/entities/CashRegisterEntity.java`
- `backend/.../cashregister/infrastructure/persistence/entities/CashMovementEntity.java`
- `backend/.../cashregister/infrastructure/persistence/repositories/CashRegisterJpaRepository.java`
- `backend/.../cashregister/infrastructure/persistence/repositories/CashMovementJpaRepository.java`
- `backend/.../cashregister/infrastructure/persistence/repositories/CashRegisterRepositoryAdapter.java`
- `backend/.../cashregister/infrastructure/persistence/repositories/CashMovementRepositoryAdapter.java`
- `backend/.../cashregister/infrastructure/listeners/OrderPaidCashRegisterListener.java`
- `backend/.../cashregister/infrastructure/web/CajaController.java`
- `backend/.../cashregister/infrastructure/web/AdminCajaController.java`
- `backend/.../cashregister/infrastructure/web/requests/OpenCashRegisterRequest.java`
- `backend/.../cashregister/infrastructure/web/requests/CloseCashRegisterRequest.java`
- `backend/.../cashregister/infrastructure/web/requests/AddMovementRequest.java`
- `backend/src/main/resources/db/migration/V38__cash_register.sql`
- `backend/src/test/.../cashregister/domain/CashRegisterTest.java`
- `backend/src/test/.../cashregister/infrastructure/web/CajaIT.java`

**Create (frontend):**
- `frontend/src/pages/admin/caja.astro`
- `frontend/src/islands/admin/CajaPage.tsx`

---

### Task 1: Cash Register domain model

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/cashregister/domain/enums/CashRegisterStatus.java`
- Create: `backend/src/main/java/com/pilarestilo/cashregister/domain/enums/CashMovementType.java`
- Create: `backend/src/main/java/com/pilarestilo/cashregister/domain/model/CashMovement.java`
- Create: `backend/src/main/java/com/pilarestilo/cashregister/domain/model/CashRegister.java`
- Create: `backend/src/main/resources/db/migration/V38__cash_register.sql`

- [ ] **Step 1: Write failing domain test**

Create `backend/src/test/java/com/pilarestilo/cashregister/domain/CashRegisterTest.java`:

```java
package com.pilarestilo.cashregister.domain;

import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CashRegisterTest {

    private CashRegister buildOpen() {
        return CashRegister.open(UUID.randomUUID(), new BigDecimal("50000"));
    }

    @Test
    void new_register_is_open_with_opening_balance() {
        CashRegister cr = buildOpen();
        assertEquals(CashRegisterStatus.OPEN, cr.getStatus());
        assertEquals(new BigDecimal("50000"), cr.getOpeningBalance());
    }

    @Test
    void expected_balance_equals_opening_plus_sales_minus_refunds() {
        CashRegister cr = buildOpen();
        cr.addMovement(CashMovementType.SALE, new BigDecimal("10000"), "Venta #1", null, UUID.randomUUID());
        cr.addMovement(CashMovementType.IN,   new BigDecimal("5000"),  "Ingreso extra", null, UUID.randomUUID());
        cr.addMovement(CashMovementType.OUT,  new BigDecimal("2000"),  "Retiro", null, UUID.randomUUID());
        // expected = 50000 + 10000 + 5000 - 2000 = 63000
        assertEquals(new BigDecimal("63000"), cr.getExpectedBalance());
    }

    @Test
    void can_close_with_declared_amount() {
        CashRegister cr = buildOpen();
        cr.addMovement(CashMovementType.SALE, new BigDecimal("10000"), "Venta", null, UUID.randomUUID());
        cr.close(new BigDecimal("59000"), null);
        assertEquals(CashRegisterStatus.CLOSED, cr.getStatus());
        assertEquals(new BigDecimal("59000"), cr.getClosingBalance());
        // expected = 60000, difference = 59000 - 60000 = -1000
        assertEquals(new BigDecimal("-1000"), cr.getDifference());
    }

    @Test
    void cannot_add_movement_to_closed_register() {
        CashRegister cr = buildOpen();
        cr.close(new BigDecimal("50000"), null);
        assertThrows(DomainException.class,
                () -> cr.addMovement(CashMovementType.SALE, new BigDecimal("1000"), "X", null, UUID.randomUUID()));
    }

    @Test
    void cannot_close_already_closed_register() {
        CashRegister cr = buildOpen();
        cr.close(new BigDecimal("50000"), null);
        assertThrows(DomainException.class, () -> cr.close(new BigDecimal("50000"), null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl backend -Dtest=CashRegisterTest -q`
Expected: FAIL — classes not found.

- [ ] **Step 3: Create enums**

Create `backend/src/main/java/com/pilarestilo/cashregister/domain/enums/CashRegisterStatus.java`:

```java
package com.pilarestilo.cashregister.domain.enums;

public enum CashRegisterStatus { OPEN, CLOSED }
```

Create `backend/src/main/java/com/pilarestilo/cashregister/domain/enums/CashMovementType.java`:

```java
package com.pilarestilo.cashregister.domain.enums;

public enum CashMovementType { SALE, REFUND, IN, OUT }
```

- [ ] **Step 4: Create CashMovement**

Create `backend/src/main/java/com/pilarestilo/cashregister/domain/model/CashMovement.java`:

```java
package com.pilarestilo.cashregister.domain.model;

import com.pilarestilo.cashregister.domain.enums.CashMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class CashMovement {

    private UUID id;
    private UUID cashRegisterId;
    private CashMovementType type;
    private BigDecimal amount;
    private String description;
    private UUID orderId;
    private LocalDateTime recordedAt;
    private UUID recordedBy;

    private CashMovement() {}

    public static CashMovement create(UUID cashRegisterId, CashMovementType type,
                                       BigDecimal amount, String description,
                                       UUID orderId, UUID recordedBy) {
        CashMovement m = new CashMovement();
        m.id = UUID.randomUUID();
        m.cashRegisterId = cashRegisterId;
        m.type = type;
        m.amount = amount;
        m.description = description;
        m.orderId = orderId;
        m.recordedAt = LocalDateTime.now();
        m.recordedBy = recordedBy;
        return m;
    }

    public static CashMovement reconstruct(UUID id, UUID cashRegisterId, CashMovementType type,
                                            BigDecimal amount, String description, UUID orderId,
                                            LocalDateTime recordedAt, UUID recordedBy) {
        CashMovement m = new CashMovement();
        m.id = id; m.cashRegisterId = cashRegisterId; m.type = type;
        m.amount = amount; m.description = description; m.orderId = orderId;
        m.recordedAt = recordedAt; m.recordedBy = recordedBy;
        return m;
    }

    public UUID getId() { return id; }
    public UUID getCashRegisterId() { return cashRegisterId; }
    public CashMovementType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public UUID getOrderId() { return orderId; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public UUID getRecordedBy() { return recordedBy; }
}
```

- [ ] **Step 5: Create CashRegister aggregate**

Create `backend/src/main/java/com/pilarestilo/cashregister/domain/model/CashRegister.java`:

```java
package com.pilarestilo.cashregister.domain.model;

import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.shared.domain.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CashRegister {

    private UUID id;
    private UUID sellerId;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal expectedBalance;
    private BigDecimal difference;
    private CashRegisterStatus status;
    private String notes;
    private List<CashMovement> movements = new ArrayList<>();

    private CashRegister() {}

    public static CashRegister open(UUID sellerId, BigDecimal openingBalance) {
        CashRegister cr = new CashRegister();
        cr.id = UUID.randomUUID();
        cr.sellerId = sellerId;
        cr.openedAt = LocalDateTime.now();
        cr.openingBalance = openingBalance;
        cr.status = CashRegisterStatus.OPEN;
        return cr;
    }

    public static CashRegister reconstruct(UUID id, UUID sellerId, LocalDateTime openedAt,
                                            LocalDateTime closedAt, BigDecimal openingBalance,
                                            BigDecimal closingBalance, BigDecimal expectedBalance,
                                            BigDecimal difference, CashRegisterStatus status,
                                            String notes, List<CashMovement> movements) {
        CashRegister cr = new CashRegister();
        cr.id = id; cr.sellerId = sellerId; cr.openedAt = openedAt; cr.closedAt = closedAt;
        cr.openingBalance = openingBalance; cr.closingBalance = closingBalance;
        cr.expectedBalance = expectedBalance; cr.difference = difference;
        cr.status = status; cr.notes = notes;
        cr.movements = new ArrayList<>(movements);
        return cr;
    }

    public void addMovement(CashMovementType type, BigDecimal amount,
                             String description, UUID orderId, UUID recordedBy) {
        if (status == CashRegisterStatus.CLOSED) {
            throw new DomainException("Cannot add movement to a closed cash register");
        }
        movements.add(CashMovement.create(id, type, amount, description, orderId, recordedBy));
    }

    public void close(BigDecimal declaredAmount, String notes) {
        if (status == CashRegisterStatus.CLOSED) {
            throw new DomainException("Cash register is already closed");
        }
        BigDecimal expected = computeExpectedBalance();
        this.closingBalance = declaredAmount;
        this.expectedBalance = expected;
        this.difference = declaredAmount.subtract(expected);
        this.closedAt = LocalDateTime.now();
        this.notes = notes;
        this.status = CashRegisterStatus.CLOSED;
    }

    public BigDecimal getExpectedBalance() {
        if (status == CashRegisterStatus.CLOSED) return expectedBalance;
        return computeExpectedBalance();
    }

    private BigDecimal computeExpectedBalance() {
        BigDecimal balance = openingBalance;
        for (CashMovement m : movements) {
            if (m.getType() == CashMovementType.SALE || m.getType() == CashMovementType.IN) {
                balance = balance.add(m.getAmount());
            } else {
                balance = balance.subtract(m.getAmount());
            }
        }
        return balance;
    }

    public UUID getId() { return id; }
    public UUID getSellerId() { return sellerId; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public BigDecimal getDifference() { return difference; }
    public CashRegisterStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public List<CashMovement> getMovements() { return Collections.unmodifiableList(movements); }
}
```

- [ ] **Step 6: Create Flyway V38**

Create `backend/src/main/resources/db/migration/V38__cash_register.sql`:

```sql
CREATE TABLE cash_registers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id UUID NOT NULL REFERENCES users(id),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    opening_balance NUMERIC(12,2) NOT NULL,
    closing_balance NUMERIC(12,2),
    expected_balance NUMERIC(12,2),
    difference NUMERIC(12,2),
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    notes TEXT
);

CREATE TABLE cash_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cash_register_id UUID NOT NULL REFERENCES cash_registers(id),
    type VARCHAR(10) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    order_id UUID REFERENCES orders(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX ON cash_registers(seller_id);
CREATE INDEX ON cash_registers(status);
CREATE INDEX ON cash_movements(cash_register_id);
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./mvnw test -pl backend -Dtest=CashRegisterTest -q`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/cashregister/domain/ \
        backend/src/main/resources/db/migration/V38__cash_register.sql \
        backend/src/test/java/com/pilarestilo/cashregister/domain/
git commit -m "feat(caja): cash register domain model"
```

---

### Task 2: Cash Register JPA infrastructure + repositories

**Files:**
- Create: `backend/.../cashregister/domain/ports/CashRegisterRepository.java`
- Create: `backend/.../cashregister/domain/ports/CashMovementRepository.java`
- Create: `backend/.../cashregister/infrastructure/persistence/entities/CashRegisterEntity.java`
- Create: `backend/.../cashregister/infrastructure/persistence/entities/CashMovementEntity.java`
- Create: `backend/.../cashregister/infrastructure/persistence/repositories/CashRegisterJpaRepository.java`
- Create: `backend/.../cashregister/infrastructure/persistence/repositories/CashMovementJpaRepository.java`
- Create: `backend/.../cashregister/infrastructure/persistence/repositories/CashRegisterRepositoryAdapter.java`
- Create: `backend/.../cashregister/infrastructure/persistence/repositories/CashMovementRepositoryAdapter.java`

- [ ] **Step 1: Create repository ports**

Create `backend/src/main/java/com/pilarestilo/cashregister/domain/ports/CashRegisterRepository.java`:

```java
package com.pilarestilo.cashregister.domain.ports;

import com.pilarestilo.cashregister.domain.model.CashRegister;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CashRegisterRepository {
    CashRegister save(CashRegister cashRegister);
    Optional<CashRegister> findById(UUID id);
    Optional<CashRegister> findOpenBySellerId(UUID sellerId);
    Page<CashRegister> findAll(Pageable pageable);
}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/domain/ports/CashMovementRepository.java`:

```java
package com.pilarestilo.cashregister.domain.ports;

import com.pilarestilo.cashregister.domain.model.CashMovement;

import java.util.List;
import java.util.UUID;

public interface CashMovementRepository {
    CashMovement save(CashMovement movement);
    List<CashMovement> findByCashRegisterId(UUID cashRegisterId);
}
```

- [ ] **Step 2: Create JPA entities**

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/persistence/entities/CashRegisterEntity.java`:

```java
package com.pilarestilo.cashregister.infrastructure.persistence.entities;

import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_registers")
public class CashRegisterEntity {

    @Id private UUID id;
    @Column(name = "seller_id", nullable = false) private UUID sellerId;
    @Column(name = "opened_at", nullable = false) private LocalDateTime openedAt;
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(name = "opening_balance", nullable = false) private BigDecimal openingBalance;
    @Column(name = "closing_balance") private BigDecimal closingBalance;
    @Column(name = "expected_balance") private BigDecimal expectedBalance;
    @Column private BigDecimal difference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private CashRegisterStatus status;
    @Column private String notes;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSellerId() { return sellerId; }
    public void setSellerId(UUID sellerId) { this.sellerId = sellerId; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public void setClosingBalance(BigDecimal closingBalance) { this.closingBalance = closingBalance; }
    public BigDecimal getExpectedBalance() { return expectedBalance; }
    public void setExpectedBalance(BigDecimal expectedBalance) { this.expectedBalance = expectedBalance; }
    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }
    public CashRegisterStatus getStatus() { return status; }
    public void setStatus(CashRegisterStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/persistence/entities/CashMovementEntity.java`:

```java
package com.pilarestilo.cashregister.infrastructure.persistence.entities;

import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cash_movements")
public class CashMovementEntity {

    @Id private UUID id;
    @Column(name = "cash_register_id", nullable = false) private UUID cashRegisterId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private CashMovementType type;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private String description;
    @Column(name = "order_id") private UUID orderId;
    @Column(name = "recorded_at", nullable = false) private LocalDateTime recordedAt;
    @Column(name = "recorded_by", nullable = false) private UUID recordedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCashRegisterId() { return cashRegisterId; }
    public void setCashRegisterId(UUID cashRegisterId) { this.cashRegisterId = cashRegisterId; }
    public CashMovementType getType() { return type; }
    public void setType(CashMovementType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
    public UUID getRecordedBy() { return recordedBy; }
    public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }
}
```

- [ ] **Step 3: Create JPA repositories**

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/persistence/repositories/CashRegisterJpaRepository.java`:

```java
package com.pilarestilo.cashregister.infrastructure.persistence.repositories;

import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.cashregister.infrastructure.persistence.entities.CashRegisterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CashRegisterJpaRepository extends JpaRepository<CashRegisterEntity, UUID> {
    Optional<CashRegisterEntity> findBySellerIdAndStatus(UUID sellerId, CashRegisterStatus status);
}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/persistence/repositories/CashMovementJpaRepository.java`:

```java
package com.pilarestilo.cashregister.infrastructure.persistence.repositories;

import com.pilarestilo.cashregister.infrastructure.persistence.entities.CashMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CashMovementJpaRepository extends JpaRepository<CashMovementEntity, UUID> {
    List<CashMovementEntity> findByCashRegisterId(UUID cashRegisterId);
}
```

- [ ] **Step 4: Create repository adapters**

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/persistence/repositories/CashRegisterRepositoryAdapter.java`:

```java
package com.pilarestilo.cashregister.infrastructure.persistence.repositories;

import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.cashregister.domain.model.CashMovement;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashMovementRepository;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.cashregister.infrastructure.persistence.entities.CashRegisterEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CashRegisterRepositoryAdapter implements CashRegisterRepository {

    private final CashRegisterJpaRepository jpaRepository;
    private final CashMovementRepository movementRepository;

    public CashRegisterRepositoryAdapter(CashRegisterJpaRepository jpaRepository,
                                          CashMovementRepository movementRepository) {
        this.jpaRepository = jpaRepository;
        this.movementRepository = movementRepository;
    }

    @Override
    public CashRegister save(CashRegister cr) {
        CashRegisterEntity entity = toEntity(cr);
        jpaRepository.save(entity);
        for (CashMovement m : cr.getMovements()) {
            movementRepository.save(m);
        }
        return findById(cr.getId()).orElseThrow();
    }

    @Override
    public Optional<CashRegister> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<CashRegister> findOpenBySellerId(UUID sellerId) {
        return jpaRepository.findBySellerIdAndStatus(sellerId, CashRegisterStatus.OPEN)
                .map(this::toDomain);
    }

    @Override
    public Page<CashRegister> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    private CashRegisterEntity toEntity(CashRegister cr) {
        CashRegisterEntity e = new CashRegisterEntity();
        e.setId(cr.getId());
        e.setSellerId(cr.getSellerId());
        e.setOpenedAt(cr.getOpenedAt());
        e.setClosedAt(cr.getClosedAt());
        e.setOpeningBalance(cr.getOpeningBalance());
        e.setClosingBalance(cr.getClosingBalance());
        if (cr.getStatus() == CashRegisterStatus.CLOSED) {
            e.setExpectedBalance(cr.getExpectedBalance());
            e.setDifference(cr.getDifference());
        }
        e.setStatus(cr.getStatus());
        e.setNotes(cr.getNotes());
        return e;
    }

    private CashRegister toDomain(CashRegisterEntity e) {
        List<CashMovement> movements = movementRepository.findByCashRegisterId(e.getId());
        return CashRegister.reconstruct(
                e.getId(), e.getSellerId(), e.getOpenedAt(), e.getClosedAt(),
                e.getOpeningBalance(), e.getClosingBalance(), e.getExpectedBalance(),
                e.getDifference(), e.getStatus(), e.getNotes(), movements);
    }
}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/persistence/repositories/CashMovementRepositoryAdapter.java`:

```java
package com.pilarestilo.cashregister.infrastructure.persistence.repositories;

import com.pilarestilo.cashregister.domain.model.CashMovement;
import com.pilarestilo.cashregister.domain.ports.CashMovementRepository;
import com.pilarestilo.cashregister.infrastructure.persistence.entities.CashMovementEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CashMovementRepositoryAdapter implements CashMovementRepository {

    private final CashMovementJpaRepository jpaRepository;

    public CashMovementRepositoryAdapter(CashMovementJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CashMovement save(CashMovement m) {
        CashMovementEntity e = toEntity(m);
        return toDomain(jpaRepository.save(e));
    }

    @Override
    public List<CashMovement> findByCashRegisterId(UUID cashRegisterId) {
        return jpaRepository.findByCashRegisterId(cashRegisterId)
                .stream().map(this::toDomain).toList();
    }

    private CashMovementEntity toEntity(CashMovement m) {
        CashMovementEntity e = new CashMovementEntity();
        e.setId(m.getId());
        e.setCashRegisterId(m.getCashRegisterId());
        e.setType(m.getType());
        e.setAmount(m.getAmount());
        e.setDescription(m.getDescription());
        e.setOrderId(m.getOrderId());
        e.setRecordedAt(m.getRecordedAt());
        e.setRecordedBy(m.getRecordedBy());
        return e;
    }

    private CashMovement toDomain(CashMovementEntity e) {
        return CashMovement.reconstruct(e.getId(), e.getCashRegisterId(), e.getType(),
                e.getAmount(), e.getDescription(), e.getOrderId(),
                e.getRecordedAt(), e.getRecordedBy());
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/cashregister/
git commit -m "feat(caja): cash register JPA infrastructure"
```

---

### Task 3: Cash Register use cases and controllers

**Files:**
- Create all use cases, DTOs, request objects, controllers

- [ ] **Step 1: Write failing integration test**

Create `backend/src/test/java/com/pilarestilo/cashregister/infrastructure/web/CajaIT.java`:

```java
package com.pilarestilo.cashregister.infrastructure.web;

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
class CajaIT {

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
    void seller_can_open_close_and_view_caja() throws Exception {
        String adminToken = loginAdmin();
        // Promote user to SELLER
        String sellerEmail = "seller_caja_" + UUID.randomUUID() + "@test.com";
        String sellerId = registerAndGetId(sellerEmail);
        promoteToSeller(adminToken, sellerId);
        String sellerToken = loginUser(sellerEmail, "pass1234");

        // Open caja
        String openBody = om.writeValueAsString(Map.of("openingBalance", 50000));
        MvcResult opened = mvc.perform(post("/api/caja/open")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(openBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();

        // Get current
        mvc.perform(get("/api/caja/current").header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        // Add IN movement
        String movBody = om.writeValueAsString(Map.of("type", "IN", "amount", 5000, "description", "Ingreso extra"));
        mvc.perform(post("/api/caja/movements")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(movBody))
                .andExpect(status().isCreated());

        // Close caja
        String closeBody = om.writeValueAsString(Map.of("closingBalance", 55000));
        mvc.perform(post("/api/caja/close")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(closeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void customer_cannot_open_caja() throws Exception {
        String token = registerAndGetToken("cust_" + UUID.randomUUID() + "@test.com");
        String body = om.writeValueAsString(Map.of("openingBalance", 10000));
        mvc.perform(post("/api/caja/open")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
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

    private void promoteToSeller(String adminToken, String userId) throws Exception {
        mvc.perform(post("/api/admin/workers/" + userId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("role", "SELLER", "vigencyStart", "2020-01-01"))))
                .andExpect(status().isOk());
    }

    private String loginUser(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -pl backend -Dtest=CajaIT -q`
Expected: FAIL — 404 on `/api/caja/open`.

- [ ] **Step 3: Create DTOs**

Create `backend/src/main/java/com/pilarestilo/cashregister/application/dto/CashMovementDto.java`:

```java
package com.pilarestilo.cashregister.application.dto;

import com.pilarestilo.cashregister.domain.model.CashMovement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CashMovementDto(
        UUID id, String type, BigDecimal amount, String description,
        UUID orderId, LocalDateTime recordedAt, UUID recordedBy
) {
    public static CashMovementDto from(CashMovement m) {
        return new CashMovementDto(m.getId(), m.getType().name(), m.getAmount(),
                m.getDescription(), m.getOrderId(), m.getRecordedAt(), m.getRecordedBy());
    }
}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/application/dto/CashRegisterDto.java`:

```java
package com.pilarestilo.cashregister.application.dto;

import com.pilarestilo.cashregister.domain.model.CashRegister;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CashRegisterDto(
        UUID id, UUID sellerId, String status,
        LocalDateTime openedAt, LocalDateTime closedAt,
        BigDecimal openingBalance, BigDecimal closingBalance,
        BigDecimal expectedBalance, BigDecimal difference, String notes,
        List<CashMovementDto> movements
) {
    public static CashRegisterDto from(CashRegister cr) {
        return new CashRegisterDto(
                cr.getId(), cr.getSellerId(), cr.getStatus().name(),
                cr.getOpenedAt(), cr.getClosedAt(),
                cr.getOpeningBalance(), cr.getClosingBalance(),
                cr.getExpectedBalance(), cr.getDifference(), cr.getNotes(),
                cr.getMovements().stream().map(CashMovementDto::from).toList());
    }
}
```

- [ ] **Step 4: Create use cases**

Create `backend/src/main/java/com/pilarestilo/cashregister/application/usecases/OpenCashRegisterUseCase.java`:

```java
package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OpenCashRegisterUseCase {

    private final CashRegisterRepository cashRegisterRepository;

    public OpenCashRegisterUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public CashRegisterDto execute(UUID sellerId, BigDecimal openingBalance) {
        if (cashRegisterRepository.findOpenBySellerId(sellerId).isPresent()) {
            throw new DomainException("Seller already has an open cash register");
        }
        CashRegister cr = CashRegister.open(sellerId, openingBalance);
        return CashRegisterDto.from(cashRegisterRepository.save(cr));
    }
}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/application/usecases/CloseCashRegisterUseCase.java`:

```java
package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CloseCashRegisterUseCase {

    private final CashRegisterRepository cashRegisterRepository;

    public CloseCashRegisterUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public CashRegisterDto execute(UUID sellerId, BigDecimal closingBalance, String notes) {
        CashRegister cr = cashRegisterRepository.findOpenBySellerId(sellerId)
                .orElseThrow(() -> new DomainException("No open cash register found"));
        cr.close(closingBalance, notes);
        return CashRegisterDto.from(cashRegisterRepository.save(cr));
    }
}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/application/usecases/GetCurrentCashRegisterUseCase.java`:

```java
package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetCurrentCashRegisterUseCase {

    private final CashRegisterRepository cashRegisterRepository;

    public GetCurrentCashRegisterUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public CashRegisterDto execute(UUID sellerId) {
        return cashRegisterRepository.findOpenBySellerId(sellerId)
                .map(CashRegisterDto::from)
                .orElseThrow(() -> new DomainException("No open cash register"));
    }
}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/application/usecases/AddCashMovementUseCase.java`:

```java
package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashMovementDto;
import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AddCashMovementUseCase {

    private final CashRegisterRepository cashRegisterRepository;

    public AddCashMovementUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public CashMovementDto execute(UUID sellerId, CashMovementType type,
                                    BigDecimal amount, String description) {
        if (type == CashMovementType.SALE || type == CashMovementType.REFUND) {
            throw new DomainException("SALE and REFUND movements are created automatically");
        }
        CashRegister cr = cashRegisterRepository.findOpenBySellerId(sellerId)
                .orElseThrow(() -> new DomainException("No open cash register"));
        cr.addMovement(type, amount, description, null, sellerId);
        CashRegister saved = cashRegisterRepository.save(cr);
        return saved.getMovements().get(saved.getMovements().size() - 1)
                .describeConstable().map(x -> null).orElse(
                        saved.getMovements().stream()
                                .filter(m -> m.getDescription().equals(description))
                                .reduce((a, b) -> b)
                                .map(CashMovementDto::from)
                                .orElseThrow());
    }
}
```

Note: The `AddCashMovementUseCase` above has an awkward way to retrieve the last movement. Simplify: add a method to `CashRegisterRepository` to save and return the movement, or just reload the register after save:

```java
public CashMovementDto execute(UUID sellerId, CashMovementType type,
                                BigDecimal amount, String description) {
    if (type == CashMovementType.SALE || type == CashMovementType.REFUND) {
        throw new DomainException("SALE and REFUND movements are created automatically");
    }
    CashRegister cr = cashRegisterRepository.findOpenBySellerId(sellerId)
            .orElseThrow(() -> new DomainException("No open cash register"));
    int sizeBefore = cr.getMovements().size();
    cr.addMovement(type, amount, description, null, sellerId);
    CashRegister saved = cashRegisterRepository.save(cr);
    return CashMovementDto.from(saved.getMovements().get(sizeBefore));
}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/application/usecases/ListCashRegistersUseCase.java`:

```java
package com.pilarestilo.cashregister.application.usecases;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ListCashRegistersUseCase {

    private final CashRegisterRepository cashRegisterRepository;

    public ListCashRegistersUseCase(CashRegisterRepository cashRegisterRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
    }

    public Page<CashRegisterDto> execute(Pageable pageable) {
        return cashRegisterRepository.findAll(pageable).map(CashRegisterDto::from);
    }
}
```

- [ ] **Step 5: Create request objects**

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/web/requests/OpenCashRegisterRequest.java`:

```java
package com.pilarestilo.cashregister.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OpenCashRegisterRequest(@NotNull @Positive BigDecimal openingBalance) {}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/web/requests/CloseCashRegisterRequest.java`:

```java
package com.pilarestilo.cashregister.infrastructure.web.requests;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CloseCashRegisterRequest(@NotNull BigDecimal closingBalance, String notes) {}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/web/requests/AddMovementRequest.java`:

```java
package com.pilarestilo.cashregister.infrastructure.web.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AddMovementRequest(
        @NotNull String type,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String description
) {}
```

- [ ] **Step 6: Create controllers**

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/web/CajaController.java`:

```java
package com.pilarestilo.cashregister.infrastructure.web;

import com.pilarestilo.cashregister.application.dto.CashMovementDto;
import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.application.usecases.*;
import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.infrastructure.web.requests.AddMovementRequest;
import com.pilarestilo.cashregister.infrastructure.web.requests.CloseCashRegisterRequest;
import com.pilarestilo.cashregister.infrastructure.web.requests.OpenCashRegisterRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final OpenCashRegisterUseCase openUseCase;
    private final CloseCashRegisterUseCase closeUseCase;
    private final GetCurrentCashRegisterUseCase getCurrentUseCase;
    private final AddCashMovementUseCase addMovementUseCase;

    public CajaController(OpenCashRegisterUseCase openUseCase,
                           CloseCashRegisterUseCase closeUseCase,
                           GetCurrentCashRegisterUseCase getCurrentUseCase,
                           AddCashMovementUseCase addMovementUseCase) {
        this.openUseCase = openUseCase;
        this.closeUseCase = closeUseCase;
        this.getCurrentUseCase = getCurrentUseCase;
        this.addMovementUseCase = addMovementUseCase;
    }

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public CashRegisterDto open(@RequestBody @Valid OpenCashRegisterRequest req,
                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return openUseCase.execute(currentUser.id(), req.openingBalance());
    }

    @PostMapping("/close")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public CashRegisterDto close(@RequestBody @Valid CloseCashRegisterRequest req,
                                  @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return closeUseCase.execute(currentUser.id(), req.closingBalance(), req.notes());
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public CashRegisterDto current(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return getCurrentUseCase.execute(currentUser.id());
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public CashMovementDto addMovement(@RequestBody @Valid AddMovementRequest req,
                                        @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return addMovementUseCase.execute(currentUser.id(),
                CashMovementType.valueOf(req.type()), req.amount(), req.description());
    }
}
```

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/web/AdminCajaController.java`:

```java
package com.pilarestilo.cashregister.infrastructure.web;

import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.application.usecases.ListCashRegistersUseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/caja")
public class AdminCajaController {

    private final ListCashRegistersUseCase listUseCase;

    public AdminCajaController(ListCashRegistersUseCase listUseCase) {
        this.listUseCase = listUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public Page<CashRegisterDto> list(Pageable pageable) {
        return listUseCase.execute(pageable);
    }
}
```

- [ ] **Step 7: Run integration test to verify it passes**

Run: `./mvnw test -pl backend -Dtest=CajaIT -q`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/cashregister/ \
        backend/src/test/java/com/pilarestilo/cashregister/
git commit -m "feat(caja): cash register use cases and REST controllers"
```

---

### Task 4: Order paid event → auto SALE movement

**Files:**
- Create: `backend/.../cashregister/infrastructure/listeners/OrderPaidCashRegisterListener.java`

- [ ] **Step 1: Check existing OrderStatusChanged event**

Read `backend/src/main/java/com/pilarestilo/order/domain/events/OrderStatusChanged.java` to confirm its fields. It should contain `orderId`, `newStatus`, and ideally `sellerId` or `customerId`. If it doesn't have `sellerId`, we'll look up the order to find the assigned seller (or skip SALE creation if no open caja).

- [ ] **Step 2: Create the listener**

Create `backend/src/main/java/com/pilarestilo/cashregister/infrastructure/listeners/OrderPaidCashRegisterListener.java`:

```java
package com.pilarestilo.cashregister.infrastructure.listeners;

import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.model.CashRegister;
import com.pilarestilo.cashregister.domain.ports.CashRegisterRepository;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OrderPaidCashRegisterListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidCashRegisterListener.class);

    private final CashRegisterRepository cashRegisterRepository;
    private final OrderRepository orderRepository;

    public OrderPaidCashRegisterListener(CashRegisterRepository cashRegisterRepository,
                                          OrderRepository orderRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
        this.orderRepository = orderRepository;
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (!"PAID".equals(event.newStatus())) return;

        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) return;

        // Find any open caja — use customer's seller if tracked, else skip
        // For now: find any open caja belonging to a seller and record the sale
        // This is a best-effort: if no caja is open, the SALE is not recorded
        Optional<CashRegister> openCaja = cashRegisterRepository.findAnyOpenRegister();
        if (openCaja.isEmpty()) {
            log.warn("Order {} paid but no open cash register found — SALE movement not recorded", event.orderId());
            return;
        }

        CashRegister cr = openCaja.get();
        cr.addMovement(CashMovementType.SALE,
                order.getTotalAmount().getAmount(),
                "Venta #" + order.getId().toString().substring(0, 8),
                order.getId(), cr.getSellerId());
        cashRegisterRepository.save(cr);
    }
}
```

Note: `findAnyOpenRegister()` needs to be added to `CashRegisterRepository` and its adapter:

In `CashRegisterRepository.java` port, add:
```java
Optional<CashRegister> findAnyOpenRegister();
```

In `CashRegisterJpaRepository.java`, add:
```java
@Query("SELECT e FROM CashRegisterEntity e WHERE e.status = 'OPEN' ORDER BY e.openedAt DESC LIMIT 1")
Optional<CashRegisterEntity> findFirstOpen();
```

In `CashRegisterRepositoryAdapter.java`, add:
```java
@Override
public Optional<CashRegister> findAnyOpenRegister() {
    return jpaRepository.findFirstOpen().map(this::toDomain);
}
```

Also check what fields `OrderStatusChanged` exposes. If the event record is `record OrderStatusChanged(UUID orderId, String newStatus)`, that's fine. If it uses different field names, adjust the listener accordingly.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/cashregister/infrastructure/listeners/ \
        backend/src/main/java/com/pilarestilo/cashregister/domain/ports/CashRegisterRepository.java \
        backend/src/main/java/com/pilarestilo/cashregister/infrastructure/persistence/repositories/
git commit -m "feat(caja): auto-create SALE movement on OrderPaid event"
```

---

### Task 5: Frontend — Caja seller view

**Files:**
- Create: `frontend/src/pages/admin/caja.astro`
- Create: `frontend/src/islands/admin/CajaPage.tsx`

- [ ] **Step 1: Create CajaPage island**

Create `frontend/src/islands/admin/CajaPage.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { useAuthStore } from '../../lib/authStore';

interface Movement {
  id: string;
  type: string;
  amount: number;
  description: string;
  recordedAt: string;
}

interface CajaDto {
  id: string;
  status: 'OPEN' | 'CLOSED';
  openedAt: string;
  openingBalance: number;
  closingBalance?: number;
  expectedBalance: number;
  difference?: number;
  notes?: string;
  movements: Movement[];
}

type View = 'loading' | 'no_caja' | 'open' | 'closed' | 'open_form';

const CLP = (n: number) =>
  new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 }).format(n);

export default function CajaPage() {
  const { token } = useAuthStore();
  const [view, setView] = useState<View>('loading');
  const [caja, setCaja] = useState<CajaDto | null>(null);
  const [openBalance, setOpenBalance] = useState('');
  const [closeBalance, setCloseBalance] = useState('');
  const [movType, setMovType] = useState('IN');
  const [movAmount, setMovAmount] = useState('');
  const [movDesc, setMovDesc] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function loadCurrent() {
    setView('loading');
    const r = await fetch('/api/caja/current', {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (r.status === 404 || r.status === 400) { setView('no_caja'); return; }
    if (!r.ok) { setView('no_caja'); return; }
    const data: CajaDto = await r.json();
    setCaja(data);
    setView(data.status === 'OPEN' ? 'open' : 'closed');
  }

  useEffect(() => { loadCurrent(); }, [token]);

  async function openCaja() {
    setBusy(true); setError('');
    const r = await fetch('/api/caja/open', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ openingBalance: parseFloat(openBalance) }),
    });
    if (!r.ok) { setError('No se pudo abrir la caja.'); setBusy(false); return; }
    await loadCurrent();
    setBusy(false);
  }

  async function closeCaja() {
    setBusy(true); setError('');
    const r = await fetch('/api/caja/close', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ closingBalance: parseFloat(closeBalance) }),
    });
    if (!r.ok) { setError('No se pudo cerrar la caja.'); setBusy(false); return; }
    await loadCurrent();
    setBusy(false);
  }

  async function addMovement() {
    if (!movAmount || !movDesc.trim()) { setError('Completa todos los campos.'); return; }
    setBusy(true); setError('');
    const r = await fetch('/api/caja/movements', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ type: movType, amount: parseFloat(movAmount), description: movDesc }),
    });
    if (!r.ok) { setError('No se pudo agregar el movimiento.'); setBusy(false); return; }
    setMovAmount(''); setMovDesc('');
    await loadCurrent();
    setBusy(false);
  }

  if (view === 'loading') return <p className="text-pe-charcoal/50 text-sm">Cargando...</p>;

  if (view === 'no_caja' || view === 'open_form') return (
    <div className="max-w-sm space-y-4">
      <p className="text-pe-charcoal/60 text-sm">No tienes caja abierta.</p>
      <div>
        <label className="block text-[10px] tracking-widest uppercase text-pe-charcoal/60 mb-1">
          Balance inicial (CLP)
        </label>
        <input type="number" value={openBalance} onChange={e => setOpenBalance(e.target.value)}
          className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
      </div>
      {error && <p className="text-red-500 text-sm">{error}</p>}
      <button onClick={openCaja} disabled={busy || !openBalance}
        className="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50">
        {busy ? 'Abriendo...' : 'Abrir caja'}
      </button>
    </div>
  );

  if (!caja) return null;

  if (view === 'closed') return (
    <div className="max-w-lg space-y-4">
      <div className="border border-[#EDE3D8] p-4 space-y-2">
        <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Resumen de cierre</p>
        <div className="grid grid-cols-2 gap-2 text-sm">
          <span className="text-pe-charcoal/60">Balance inicial</span>
          <span className="text-right">{CLP(caja.openingBalance)}</span>
          <span className="text-pe-charcoal/60">Balance esperado</span>
          <span className="text-right">{CLP(caja.expectedBalance)}</span>
          <span className="text-pe-charcoal/60">Balance declarado</span>
          <span className="text-right">{CLP(caja.closingBalance ?? 0)}</span>
          <span className="text-pe-charcoal/60">Diferencia</span>
          <span className={`text-right font-medium ${(caja.difference ?? 0) === 0 ? 'text-green-600' : 'text-red-500'}`}>
            {CLP(caja.difference ?? 0)}
          </span>
        </div>
      </div>
      <button onClick={() => setView('open_form')}
        className="bg-[#1A1A1A] text-[#F8F4EF] px-8 py-3 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors">
        Abrir nueva caja
      </button>
    </div>
  );

  // view === 'open'
  return (
    <div className="max-w-2xl space-y-6">
      <div className="border border-[#EDE3D8] p-4 flex justify-between items-center">
        <div>
          <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Caja abierta</p>
          <p className="text-2xl font-display text-pe-black mt-1">{CLP(caja.expectedBalance)}</p>
          <p className="text-xs text-pe-charcoal/50 mt-0.5">Balance esperado actual</p>
        </div>
        <span className="px-3 py-1 bg-green-100 text-green-700 text-xs tracking-widest uppercase">Abierta</span>
      </div>

      {/* Movements */}
      <div>
        <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40 mb-3">Movimientos</p>
        {caja.movements.length === 0 && (
          <p className="text-pe-charcoal/40 text-sm">Sin movimientos aún.</p>
        )}
        <ul className="divide-y divide-[#EDE3D8]">
          {caja.movements.map(m => (
            <li key={m.id} className="flex items-center justify-between py-2.5 text-sm">
              <div>
                <span className={`text-[10px] tracking-widest uppercase mr-2 ${
                  m.type === 'SALE' || m.type === 'IN' ? 'text-green-600' : 'text-red-500'
                }`}>{m.type}</span>
                <span className="text-pe-charcoal/70">{m.description}</span>
              </div>
              <span className={`font-medium ${m.type === 'SALE' || m.type === 'IN' ? 'text-green-600' : 'text-red-500'}`}>
                {m.type === 'OUT' || m.type === 'REFUND' ? '-' : '+'}{CLP(m.amount)}
              </span>
            </li>
          ))}
        </ul>
      </div>

      {/* Add manual movement */}
      <div className="border border-[#EDE3D8] p-4 space-y-3">
        <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Agregar movimiento</p>
        <div className="flex gap-3">
          <select value={movType} onChange={e => setMovType(e.target.value)}
            className="border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]">
            <option value="IN">Entrada</option>
            <option value="OUT">Salida</option>
          </select>
          <input type="number" value={movAmount} onChange={e => setMovAmount(e.target.value)}
            placeholder="Monto" className="flex-1 border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
        </div>
        <input type="text" value={movDesc} onChange={e => setMovDesc(e.target.value)}
          placeholder="Descripción" className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
        <button onClick={addMovement} disabled={busy}
          className="bg-[#1A1A1A] text-[#F8F4EF] px-6 py-2.5 text-xs tracking-widest uppercase hover:bg-[#B76E79] transition-colors disabled:opacity-50">
          Agregar
        </button>
      </div>

      {/* Close caja */}
      <div className="border border-[#EDE3D8] p-4 space-y-3">
        <p className="text-[10px] tracking-widest uppercase text-pe-charcoal/40">Cerrar caja</p>
        <p className="text-xs text-pe-charcoal/50">Balance esperado: {CLP(caja.expectedBalance)}</p>
        <input type="number" value={closeBalance} onChange={e => setCloseBalance(e.target.value)}
          placeholder="Balance declarado"
          className="w-full border border-[#EDE3D8] bg-transparent px-3 py-2 text-sm focus:outline-none focus:border-[#B76E79]" />
        <button onClick={closeCaja} disabled={busy || !closeBalance}
          className="border border-[#1A1A1A] text-[#1A1A1A] px-6 py-2.5 text-xs tracking-widest uppercase hover:bg-red-50 hover:border-red-400 hover:text-red-600 transition-colors disabled:opacity-50">
          {busy ? 'Cerrando...' : 'Cerrar caja'}
        </button>
      </div>

      {error && <p className="text-red-500 text-sm">{error}</p>}
    </div>
  );
}
```

- [ ] **Step 2: Create Astro page**

Create `frontend/src/pages/admin/caja.astro`:

```astro
---
import AdminLayout from '../../layouts/AdminLayout.astro';
import CajaPage from '../../islands/admin/CajaPage';
---

<AdminLayout title="Caja">
  <div class="mb-5 sm:mb-6">
    <p class="font-sans text-[0.65rem] tracking-[0.25em] uppercase text-pe-charcoal/35 mb-1">Pilar Estilo</p>
    <h1 class="font-display text-pe-black text-2xl sm:text-3xl font-light">Caja</h1>
  </div>
  <CajaPage client:load />
</AdminLayout>
```

- [ ] **Step 3: Add caja nav item to AdminSidebar**

In `AdminSidebar.tsx`, add:
```typescript
import { ..., DollarSign } from 'lucide-react';
// In navItems:
{ href: '/admin/caja', icon: DollarSign, label: 'Caja', viewKey: 'caja' },
```

- [ ] **Step 4: Verify build**

Run: `cd frontend && npm run build`
Expected: no TypeScript errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/admin/caja.astro \
        frontend/src/islands/admin/CajaPage.tsx \
        frontend/src/islands/admin/AdminSidebar.tsx
git commit -m "feat(caja): seller caja page island and nav item"
```

---

### Task 6: Run full test suite

- [ ] **Step 1: Run all backend tests**

Run: `./mvnw test -pl backend`
Expected: All pass.

- [ ] **Step 2: Frontend build**

Run: `cd frontend && npm run build`
Expected: Clean build.

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat(caja): complete cash register system"
```
