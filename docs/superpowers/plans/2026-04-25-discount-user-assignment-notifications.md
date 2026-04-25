# Discount User Assignment + In-App Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-user discount code assignment with in-app notification history, a always-visible bell icon, and a discount codes section in how-we-sell.

**Architecture:** Notifications are stored in a new `notifications` table and served via REST. An `InAppNotificationPort` adapter writes to DB alongside the existing external senders. Discount codes gain a nullable `assigned_user_id` FK; assignment fires a domain event that triggers both in-app and external notifications.

**Tech Stack:** Java 17, Spring Boot 3, JPA/Hibernate 6, Flyway, Astro + React islands, TypeScript

**Spec:** `docs/superpowers/specs/2026-04-25-discount-user-assignment-notifications-design.md`

---

## File Map

**New backend:**
- `backend/src/main/resources/db/migration/V31__discount_user_assignment_and_notifications.sql`
- `backend/src/main/java/com/pilarestilo/notification/domain/enums/NotificationType.java`
- `backend/src/main/java/com/pilarestilo/notification/domain/model/InAppNotification.java`
- `backend/src/main/java/com/pilarestilo/notification/domain/ports/InAppNotificationRepository.java`
- `backend/src/main/java/com/pilarestilo/notification/domain/ports/InAppNotificationPort.java`
- `backend/src/main/java/com/pilarestilo/notification/application/dto/InAppNotificationDto.java`
- `backend/src/main/java/com/pilarestilo/notification/application/usecases/GetNotificationsUseCase.java`
- `backend/src/main/java/com/pilarestilo/notification/application/usecases/GetUnreadCountUseCase.java`
- `backend/src/main/java/com/pilarestilo/notification/application/usecases/MarkNotificationReadUseCase.java`
- `backend/src/main/java/com/pilarestilo/notification/application/usecases/MarkAllNotificationsReadUseCase.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/adapters/InAppNotificationSender.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/persistence/entities/NotificationEntity.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/persistence/repositories/NotificationJpaRepository.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/persistence/repositories/NotificationRepositoryAdapter.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/web/controllers/NotificationController.java`
- `backend/src/main/java/com/pilarestilo/discount/domain/events/DiscountCodeAssigned.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/listeners/DiscountNotificationListener.java`
- `backend/src/main/java/com/pilarestilo/user/application/dto/UserSearchResultDto.java`
- `backend/src/main/java/com/pilarestilo/user/application/usecases/SearchUsersUseCase.java`

**Modified backend:**
- `backend/src/main/java/com/pilarestilo/notification/domain/ports/NotificationSender.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/adapters/LogNotificationSender.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/adapters/SimulatedWhatsAppNotificationSender.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/adapters/TwilioWhatsAppNotificationSender.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/adapters/SendGridEmailNotificationSender.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/adapters/SmtpEmailNotificationSender.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/adapters/N8nWebhookNotificationSender.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/adapters/SystemSettingsNotificationSender.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/listeners/OrderNotificationListener.java`
- `backend/src/main/java/com/pilarestilo/notification/infrastructure/listeners/PaymentNotificationListener.java`
- `backend/src/main/java/com/pilarestilo/discount/domain/model/Discount.java`
- `backend/src/main/java/com/pilarestilo/discount/infrastructure/persistence/entities/DiscountEntity.java`
- `backend/src/main/java/com/pilarestilo/discount/infrastructure/persistence/repositories/DiscountRepositoryAdapter.java`
- `backend/src/main/java/com/pilarestilo/discount/application/dto/DiscountDto.java`
- `backend/src/main/java/com/pilarestilo/discount/application/mappers/DiscountMapper.java`
- `backend/src/main/java/com/pilarestilo/discount/application/usecases/CreateDiscountUseCase.java`
- `backend/src/main/java/com/pilarestilo/discount/application/usecases/ValidateDiscountForUserUseCase.java`
- `backend/src/main/java/com/pilarestilo/discount/application/usecases/ListDiscountsUseCase.java`
- `backend/src/main/java/com/pilarestilo/discount/application/usecases/GetDiscountUseCase.java`
- `backend/src/main/java/com/pilarestilo/discount/infrastructure/web/requests/CreateDiscountRequest.java`
- `backend/src/main/java/com/pilarestilo/discount/infrastructure/web/controllers/DiscountController.java`
- `backend/src/main/java/com/pilarestilo/user/domain/ports/UserRepository.java`
- `backend/src/main/java/com/pilarestilo/user/infrastructure/persistence/repositories/UserJpaRepository.java`
- `backend/src/main/java/com/pilarestilo/user/infrastructure/persistence/repositories/UserRepositoryAdapter.java`
- `backend/src/main/java/com/pilarestilo/user/infrastructure/web/controllers/UserController.java`

**New frontend:**
- `frontend/src/islands/NotificationHistory.tsx`

**Modified frontend:**
- `frontend/src/lib/api.ts`
- `frontend/src/islands/NavNotificationBell.tsx`
- `frontend/src/islands/auth/AccountPage.tsx`
- `frontend/src/islands/admin/DiscountCodeManager.tsx`
- `frontend/src/pages/[locale]/how-we-sell.astro`

---

## Task 1: Database migration V31

**Files:**
- Create: `backend/src/main/resources/db/migration/V31__discount_user_assignment_and_notifications.sql`

- [ ] **Step 1: Create migration file**

```sql
ALTER TABLE discounts
    ADD COLUMN assigned_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_discounts_assigned_user ON discounts(assigned_user_id);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    metadata   JSONB,
    read_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id) WHERE read_at IS NULL;
```

- [ ] **Step 2: Start backend and verify Flyway applies migration with no errors**

Run backend. In logs look for:
```
Successfully applied 1 migration to schema "public" (V31)
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V31__discount_user_assignment_and_notifications.sql
git commit -m "feat(db): V31 - notifications table and discounts.assigned_user_id"
```

---

## Task 2: Notification domain layer

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/notification/domain/enums/NotificationType.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/domain/model/InAppNotification.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/domain/ports/InAppNotificationRepository.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/domain/ports/InAppNotificationPort.java`
- Test: `backend/src/test/java/com/pilarestilo/notification/domain/InAppNotificationTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.pilarestilo.notification.domain;

import com.pilarestilo.notification.domain.enums.NotificationType;
import com.pilarestilo.notification.domain.model.InAppNotification;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InAppNotificationTest {

    @Test
    void create_setsFieldsAndUnread() {
        UUID userId = UUID.randomUUID();
        InAppNotification n = InAppNotification.create(
            userId, NotificationType.ORDER_CONFIRMED,
            "Pedido confirmado", "Tu pedido fue creado.",
            Map.of("orderId", "abc")
        );
        assertEquals(userId, n.getUserId());
        assertEquals(NotificationType.ORDER_CONFIRMED, n.getType());
        assertEquals("Pedido confirmado", n.getTitle());
        assertFalse(n.isRead());
        assertNotNull(n.getCreatedAt());
        assertNull(n.getReadAt());
    }
}
```

- [ ] **Step 2: Run — expect FAIL (class not found)**

```bash
cd backend && mvn test -Dtest=InAppNotificationTest -q 2>&1 | tail -5
```

- [ ] **Step 3: Create `NotificationType.java`**

```java
package com.pilarestilo.notification.domain.enums;

public enum NotificationType {
    DISCOUNT_CODE_ASSIGNED,
    ORDER_CONFIRMED,
    PAYMENT_RECEIVED,
    ORDER_SHIPPED
}
```

- [ ] **Step 4: Create `InAppNotification.java`**

```java
package com.pilarestilo.notification.domain.model;

import com.pilarestilo.notification.domain.enums.NotificationType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class InAppNotification {

    private UUID id;
    private UUID userId;
    private NotificationType type;
    private String title;
    private String body;
    private Map<String, Object> metadata;
    private Instant readAt;
    private Instant createdAt;

    private InAppNotification() {}

    public static InAppNotification create(UUID userId, NotificationType type,
                                            String title, String body,
                                            Map<String, Object> metadata) {
        InAppNotification n = new InAppNotification();
        n.userId = userId;
        n.type = type;
        n.title = title;
        n.body = body;
        n.metadata = metadata;
        n.createdAt = Instant.now();
        return n;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRead() { return readAt != null; }

    public void setId(UUID id) { this.id = id; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 5: Create `InAppNotificationRepository.java`**

```java
package com.pilarestilo.notification.domain.ports;

import com.pilarestilo.notification.domain.model.InAppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;

public interface InAppNotificationRepository {
    InAppNotification save(InAppNotification notification);
    Page<InAppNotification> findByUserId(UUID userId, Pageable pageable);
    Page<InAppNotification> findRecentByUserId(UUID userId, Pageable pageable);
    long countUnreadByUserId(UUID userId);
    Optional<InAppNotification> findByIdAndUserId(UUID id, UUID userId);
    void markAsRead(UUID id, UUID userId);
    void markAllAsRead(UUID userId);
}
```

- [ ] **Step 6: Create `InAppNotificationPort.java`**

```java
package com.pilarestilo.notification.domain.ports;

import java.util.UUID;

public interface InAppNotificationPort {
    void notifyDiscountCodeAssigned(UUID userId, String code);
    void notifyOrderConfirmed(UUID userId, UUID orderId);
    void notifyPaymentReceived(UUID userId, UUID paymentId);
    void notifyOrderShipped(UUID userId, UUID orderId);
}
```

- [ ] **Step 7: Run test — expect PASS**

```bash
cd backend && mvn test -Dtest=InAppNotificationTest -q
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/notification/domain/
git add backend/src/test/java/com/pilarestilo/notification/domain/
git commit -m "feat(notification): InAppNotification domain model, ports, and NotificationType"
```

---

## Task 3: Notification persistence

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/notification/infrastructure/persistence/entities/NotificationEntity.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/infrastructure/persistence/repositories/NotificationJpaRepository.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/infrastructure/persistence/repositories/NotificationRepositoryAdapter.java`

- [ ] **Step 1: Create `NotificationEntity.java`**

```java
package com.pilarestilo.notification.infrastructure.persistence.entities;

import com.pilarestilo.notification.domain.enums.NotificationType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: Create `NotificationJpaRepository.java`**

```java
package com.pilarestilo.notification.infrastructure.persistence.repositories;

import com.pilarestilo.notification.infrastructure.persistence.entities.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.readAt = CURRENT_TIMESTAMP WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL")
    void markAsRead(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.readAt IS NULL")
    void markAllAsReadByUserId(@Param("userId") UUID userId);
}
```

- [ ] **Step 3: Create `NotificationRepositoryAdapter.java`**

```java
package com.pilarestilo.notification.infrastructure.persistence.repositories;

import com.pilarestilo.notification.domain.model.InAppNotification;
import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import com.pilarestilo.notification.infrastructure.persistence.entities.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationRepositoryAdapter implements InAppNotificationRepository {

    private final NotificationJpaRepository jpa;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public InAppNotification save(InAppNotification n) {
        return toDomain(jpa.save(toEntity(n)));
    }

    @Override
    public Page<InAppNotification> findByUserId(UUID userId, Pageable pageable) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toDomain);
    }

    @Override
    public Page<InAppNotification> findRecentByUserId(UUID userId, Pageable pageable) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toDomain);
    }

    @Override
    public long countUnreadByUserId(UUID userId) {
        return jpa.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    public Optional<InAppNotification> findByIdAndUserId(UUID id, UUID userId) {
        return jpa.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    @Transactional
    public void markAsRead(UUID id, UUID userId) {
        jpa.markAsRead(id, userId);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        jpa.markAllAsReadByUserId(userId);
    }

    private NotificationEntity toEntity(InAppNotification n) {
        NotificationEntity e = new NotificationEntity();
        if (n.getId() != null) e.setId(n.getId());
        e.setUserId(n.getUserId());
        e.setType(n.getType());
        e.setTitle(n.getTitle());
        e.setBody(n.getBody());
        e.setMetadata(n.getMetadata());
        e.setReadAt(n.getReadAt());
        e.setCreatedAt(n.getCreatedAt() != null ? n.getCreatedAt() : java.time.Instant.now());
        return e;
    }

    private InAppNotification toDomain(NotificationEntity e) {
        InAppNotification n = InAppNotification.create(
            e.getUserId(), e.getType(), e.getTitle(), e.getBody(), e.getMetadata()
        );
        n.setId(e.getId());
        n.setReadAt(e.getReadAt());
        n.setCreatedAt(e.getCreatedAt());
        return n;
    }
}
```

- [ ] **Step 4: Verify backend starts without errors**

Start backend. No `BeanCreationException` for `NotificationRepositoryAdapter`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/notification/infrastructure/persistence/
git commit -m "feat(notification): NotificationEntity, JpaRepository, RepositoryAdapter"
```

---

## Task 4: InAppNotificationSender + use cases + DTO

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/notification/infrastructure/adapters/InAppNotificationSender.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/application/dto/InAppNotificationDto.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/application/usecases/GetNotificationsUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/application/usecases/GetUnreadCountUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/application/usecases/MarkNotificationReadUseCase.java`
- Create: `backend/src/main/java/com/pilarestilo/notification/application/usecases/MarkAllNotificationsReadUseCase.java`
- Test: `backend/src/test/java/com/pilarestilo/notification/application/usecases/GetUnreadCountUseCaseTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.pilarestilo.notification.application.usecases;

import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUnreadCountUseCaseTest {

    @Mock InAppNotificationRepository repository;
    @InjectMocks GetUnreadCountUseCase useCase;

    @Test
    void returnsCountFromRepository() {
        UUID userId = UUID.randomUUID();
        when(repository.countUnreadByUserId(userId)).thenReturn(3L);
        assertEquals(3L, useCase.execute(userId));
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd backend && mvn test -Dtest=GetUnreadCountUseCaseTest -q 2>&1 | tail -5
```

- [ ] **Step 3: Create `InAppNotificationDto.java`**

```java
package com.pilarestilo.notification.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InAppNotificationDto(
    UUID id,
    String type,
    String title,
    String body,
    Map<String, Object> metadata,
    boolean read,
    Instant createdAt
) {}
```

- [ ] **Step 4: Create `GetNotificationsUseCase.java`**

```java
package com.pilarestilo.notification.application.usecases;

import com.pilarestilo.notification.application.dto.InAppNotificationDto;
import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class GetNotificationsUseCase {

    private final InAppNotificationRepository repository;

    public GetNotificationsUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<InAppNotificationDto> execute(UUID userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable).map(n -> new InAppNotificationDto(
            n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
            n.getMetadata(), n.isRead(), n.getCreatedAt()
        ));
    }

    @Transactional(readOnly = true)
    public Page<InAppNotificationDto> executeRecent(UUID userId, Pageable pageable) {
        return repository.findRecentByUserId(userId, pageable).map(n -> new InAppNotificationDto(
            n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
            n.getMetadata(), n.isRead(), n.getCreatedAt()
        ));
    }
}
```

- [ ] **Step 5: Create `GetUnreadCountUseCase.java`**

```java
package com.pilarestilo.notification.application.usecases;

import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class GetUnreadCountUseCase {

    private final InAppNotificationRepository repository;

    public GetUnreadCountUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public long execute(UUID userId) {
        return repository.countUnreadByUserId(userId);
    }
}
```

- [ ] **Step 6: Create `MarkNotificationReadUseCase.java`**

```java
package com.pilarestilo.notification.application.usecases;

import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class MarkNotificationReadUseCase {

    private final InAppNotificationRepository repository;

    public MarkNotificationReadUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void execute(UUID notificationId, UUID userId) {
        repository.markAsRead(notificationId, userId);
    }
}
```

- [ ] **Step 7: Create `MarkAllNotificationsReadUseCase.java`**

```java
package com.pilarestilo.notification.application.usecases;

import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class MarkAllNotificationsReadUseCase {

    private final InAppNotificationRepository repository;

    public MarkAllNotificationsReadUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void execute(UUID userId) {
        repository.markAllAsRead(userId);
    }
}
```

- [ ] **Step 8: Create `InAppNotificationSender.java`**

```java
package com.pilarestilo.notification.infrastructure.adapters;

import com.pilarestilo.notification.domain.enums.NotificationType;
import com.pilarestilo.notification.domain.model.InAppNotification;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class InAppNotificationSender implements InAppNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationSender.class);
    private final InAppNotificationRepository repository;

    public InAppNotificationSender(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void notifyDiscountCodeAssigned(UUID userId, String code) {
        save(userId, NotificationType.DISCOUNT_CODE_ASSIGNED,
            "Código de descuento exclusivo",
            "Tienes un código de descuento exclusivo: " + code + ". Úsalo en tu próxima compra.",
            Map.of("code", code));
    }

    @Override
    public void notifyOrderConfirmed(UUID userId, UUID orderId) {
        save(userId, NotificationType.ORDER_CONFIRMED,
            "Pedido confirmado",
            "Tu pedido fue creado correctamente. Te notificaremos cuando avance.",
            Map.of("orderId", orderId.toString()));
    }

    @Override
    public void notifyPaymentReceived(UUID userId, UUID paymentId) {
        save(userId, NotificationType.PAYMENT_RECEIVED,
            "Pago recibido",
            "Confirmamos tu pago. Gracias por tu compra en Pilar Estilo.",
            Map.of("paymentId", paymentId.toString()));
    }

    @Override
    public void notifyOrderShipped(UUID userId, UUID orderId) {
        save(userId, NotificationType.ORDER_SHIPPED,
            "Pedido enviado",
            "Tu pedido ya fue enviado. Pronto llegará a destino.",
            Map.of("orderId", orderId.toString()));
    }

    private void save(UUID userId, NotificationType type, String title, String body, Map<String, Object> metadata) {
        try {
            repository.save(InAppNotification.create(userId, type, title, body, metadata));
        } catch (Exception ex) {
            log.warn("[IN_APP] failed to save notification type={} userId={} reason={}", type, userId, ex.getMessage());
        }
    }
}
```

- [ ] **Step 9: Run test — expect PASS**

```bash
cd backend && mvn test -Dtest=GetUnreadCountUseCaseTest -q
```

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/notification/
git add backend/src/test/java/com/pilarestilo/notification/
git commit -m "feat(notification): InAppNotificationSender, use cases, and DTO"
```

---

## Task 5: NotificationController

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/notification/infrastructure/web/controllers/NotificationController.java`

- [ ] **Step 1: Create `NotificationController.java`**

```java
package com.pilarestilo.notification.infrastructure.web.controllers;

import com.pilarestilo.notification.application.dto.InAppNotificationDto;
import com.pilarestilo.notification.application.usecases.*;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;
    private final GetUnreadCountUseCase getUnreadCountUseCase;
    private final MarkNotificationReadUseCase markReadUseCase;
    private final MarkAllNotificationsReadUseCase markAllReadUseCase;

    public NotificationController(GetNotificationsUseCase getNotificationsUseCase,
                                   GetUnreadCountUseCase getUnreadCountUseCase,
                                   MarkNotificationReadUseCase markReadUseCase,
                                   MarkAllNotificationsReadUseCase markAllReadUseCase) {
        this.getNotificationsUseCase = getNotificationsUseCase;
        this.getUnreadCountUseCase = getUnreadCountUseCase;
        this.markReadUseCase = markReadUseCase;
        this.markAllReadUseCase = markAllReadUseCase;
    }

    @GetMapping
    public Page<InAppNotificationDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean recentOnly,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 50));
        if (recentOnly) {
            return getNotificationsUseCase.executeRecent(currentUser.id(), pageable);
        }
        return getNotificationsUseCase.execute(currentUser.id(), pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Map.of("count", getUnreadCountUseCase.execute(currentUser.id()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id,
                                          @AuthenticationPrincipal AuthenticatedUser currentUser) {
        markReadUseCase.execute(id, currentUser.id());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        markAllReadUseCase.execute(currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Verify with curl (backend running)**

```bash
curl -s -H "Authorization: Bearer <token>" http://localhost:8080/api/notifications/unread-count
# Expected: {"count":0}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/notification/infrastructure/web/
git commit -m "feat(notification): NotificationController REST endpoints"
```

---

## Task 6: Wire in-app sender into existing event listeners

**Files:**
- Modify: `backend/src/main/java/com/pilarestilo/notification/infrastructure/listeners/OrderNotificationListener.java`
- Modify: `backend/src/main/java/com/pilarestilo/notification/infrastructure/listeners/PaymentNotificationListener.java`

- [ ] **Step 1: Modify `OrderNotificationListener.java`** — inject `InAppNotificationPort` and call alongside existing sender

```java
package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderNotificationListener {

    private final NotificationSender notificationSender;
    private final InAppNotificationPort inAppNotificationPort;
    private final UserRepository userRepository;

    public OrderNotificationListener(NotificationSender notificationSender,
                                      InAppNotificationPort inAppNotificationPort,
                                      UserRepository userRepository) {
        this.notificationSender = notificationSender;
        this.inAppNotificationPort = inAppNotificationPort;
        this.userRepository = userRepository;
    }

    @EventListener
    public void onOrderCreated(OrderCreated event) {
        userRepository.findById(event.customerId()).ifPresent(user -> {
            notificationSender.sendOrderConfirmation(
                event.orderId(),
                NotificationRecipient.of(user.getPhone(), user.getEmail(),
                    user.getNotificationChannelPreference().name())
            );
            inAppNotificationPort.notifyOrderConfirmed(user.getId(), event.orderId());
        });
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        if (event.newStatus() == OrderStatus.SHIPPED) {
            userRepository.findById(event.customerId()).ifPresentOrElse(
                user -> {
                    notificationSender.sendOrderShipped(
                        event.orderId(),
                        NotificationRecipient.of(user.getPhone(), user.getEmail(),
                            user.getNotificationChannelPreference().name())
                    );
                    inAppNotificationPort.notifyOrderShipped(user.getId(), event.orderId());
                },
                () -> notificationSender.sendOrderShipped(event.orderId(), NotificationRecipient.unknown())
            );
        }
    }
}
```

- [ ] **Step 2: Modify `PaymentNotificationListener.java`** — inject `InAppNotificationPort`

```java
package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.payment.domain.events.PaymentConfirmed;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentNotificationListener {

    private final NotificationSender notificationSender;
    private final InAppNotificationPort inAppNotificationPort;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public PaymentNotificationListener(NotificationSender notificationSender,
                                       InAppNotificationPort inAppNotificationPort,
                                       OrderRepository orderRepository,
                                       UserRepository userRepository) {
        this.notificationSender = notificationSender;
        this.inAppNotificationPort = inAppNotificationPort;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @EventListener
    public void onPaymentConfirmed(PaymentConfirmed event) {
        NotificationRecipient recipient = orderRepository.findById(event.orderId())
            .flatMap(order -> userRepository.findById(order.getCustomerId()))
            .map(user -> NotificationRecipient.of(user.getPhone(), user.getEmail(),
                user.getNotificationChannelPreference().name()))
            .orElse(NotificationRecipient.unknown());
        notificationSender.sendPaymentReceived(event.paymentId(), recipient);

        orderRepository.findById(event.orderId())
            .map(order -> order.getCustomerId())
            .ifPresent(userId -> inAppNotificationPort.notifyPaymentReceived(userId, event.paymentId()));
    }
}
```

- [ ] **Step 3: Start backend — verify no startup errors**

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/notification/infrastructure/listeners/
git commit -m "feat(notification): wire in-app sender into order and payment event listeners"
```

---

## Task 7: Add sendDiscountCodeAssigned to NotificationSender + all adapters

**Files:**
- Modify: `NotificationSender.java` and all 7 adapter classes + `DiscountNotificationListener`

- [ ] **Step 1: Add method to `NotificationSender.java`**

```java
void sendDiscountCodeAssigned(String code, NotificationRecipient recipient);
```

- [ ] **Step 2: Add to `LogNotificationSender.java`**

```java
@Override
public void sendDiscountCodeAssigned(String code, NotificationRecipient recipient) {
    log.info("[NOTIFICATION] DISCOUNT_CODE_ASSIGNED code={} recipient={}", code, recipient.preferredEmailThenPhone());
}
```

- [ ] **Step 3: Add to `SimulatedWhatsAppNotificationSender.java`**

```java
@Override
public void sendDiscountCodeAssigned(String code, NotificationRecipient recipient) {
    EffectiveConfig config = resolveConfig();
    if (!recipient.allowsWhatsApp()) {
        log.info("[WHATSAPP:SIMULATED] skipped template=DISCOUNT_CODE_ASSIGNED code={} reason=channel-preference preference={}", code, recipient.preference());
        return;
    }
    log.info("[WHATSAPP:SIMULATED] sender={} to={} template=DISCOUNT_CODE_ASSIGNED code={} recipient={}",
        config.senderAlias(), config.simulatedTo(), code, recipient.preferredPhoneThenEmail());
}
```

- [ ] **Step 4: Add to `TwilioWhatsAppNotificationSender.java`**

```java
@Override
public void sendDiscountCodeAssigned(String code, NotificationRecipient recipient) {
    if (!recipient.allowsWhatsApp()) {
        log.info("[WHATSAPP:TWILIO] skipped template=DISCOUNT_CODE_ASSIGNED code={} reason=channel-preference preference={}", code, recipient.preference());
        return;
    }
    EffectiveConfig config = resolveConfig();
    if (config == null) return;

    String body = String.format(Locale.ROOT, "%s: tienes un código de descuento exclusivo: %s. Úsalo en tu próxima compra.",
        config.senderAlias(), code);
    String recipientContact = normalize(recipient.preferredPhoneThenEmail(), "unknown");
    String toAddress = resolveToAddress(recipientContact, config.fallbackToAddress());

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("To", toAddress);
    form.add("From", config.fromAddress());
    form.add("Body", body);

    try {
        RestClient restClient = restClientBuilder
            .baseUrl(config.apiBaseUrl())
            .defaultHeaders(headers -> headers.setBasicAuth(config.accountSid(), config.authToken()))
            .build();
        restClient.post()
            .uri("/2010-04-01/Accounts/{sid}/Messages.json", config.accountSid())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .toBodilessEntity();
        log.info("[WHATSAPP:TWILIO] template=DISCOUNT_CODE_ASSIGNED to={} code={}", toAddress, code);
    } catch (Exception ex) {
        log.warn("[WHATSAPP:TWILIO] send failed template=DISCOUNT_CODE_ASSIGNED code={} reason={}", code, ex.getMessage());
    }
}
```

- [ ] **Step 5: Add to `SendGridEmailNotificationSender.java`**

```java
@Override
public void sendDiscountCodeAssigned(String code, NotificationRecipient recipient) {
    String subject = "Código de descuento exclusivo para ti";
    String body = String.format(Locale.ROOT,
        "Tienes un código de descuento exclusivo: %s%nÚsalo en tu próxima compra en Pilar Estilo.",
        code);
    send("DISCOUNT_CODE_ASSIGNED", null, recipient, subject, body);
}
```

- [ ] **Step 6: Add to `SmtpEmailNotificationSender.java`**

```java
@Override
public void sendDiscountCodeAssigned(String code, NotificationRecipient recipient) {
    String subject = "Código de descuento exclusivo para ti";
    String body = String.format(Locale.ROOT,
        "Tienes un código de descuento exclusivo: %s%nÚsalo en tu próxima compra en Pilar Estilo.",
        code);
    send("DISCOUNT_CODE_ASSIGNED", null, recipient, subject, body);
}
```

- [ ] **Step 7: Add to `N8nWebhookNotificationSender.java`** — add private helper and override

Add private method (reuses existing `resolveConfig`, `firstNonBlank`, etc.):

```java
@Override
public void sendDiscountCodeAssigned(String code, NotificationRecipient recipient) {
    sendWebhookCode("DISCOUNT_CODE_ASSIGNED", code, recipient);
}

private void sendWebhookCode(String eventType, String reference, NotificationRecipient recipient) {
    EffectiveConfig config = resolveConfig();
    if (config == null) {
        log.warn("[NOTIFICATION:N8N] disabled: missing webhook URL.");
        return;
    }
    Map<String, Object> recipientPayload = new LinkedHashMap<>();
    recipientPayload.put("phone", recipient.phone());
    recipientPayload.put("email", recipient.email());
    recipientPayload.put("channelPreference", recipient.preference().name());
    recipientPayload.put("allowWhatsApp", recipient.allowsWhatsApp());
    recipientPayload.put("allowEmail", recipient.allowsEmail());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventType", eventType);
    payload.put("reference", reference);
    payload.put("occurredAt", Instant.now().toString());
    payload.put("recipient", recipientPayload);

    try {
        RestClient.RequestBodySpec request = restClientBuilder.build()
            .post().uri(config.webhookUrl()).contentType(MediaType.APPLICATION_JSON);
        if (!config.apiKey().isBlank()) {
            request = request.header(config.tokenHeaderName(), config.apiKey());
        }
        request.body(payload).retrieve().toBodilessEntity();
        log.info("[NOTIFICATION:N8N] event={} reference={}", eventType, reference);
    } catch (Exception ex) {
        log.warn("[NOTIFICATION:N8N] send failed event={} reference={} reason={}", eventType, reference, ex.getMessage());
    }
}
```

- [ ] **Step 8: Add to `SystemSettingsNotificationSender.java`**

```java
@Override
public void sendDiscountCodeAssigned(String code, NotificationRecipient recipient) {
    resolveSender().sendDiscountCodeAssigned(code, recipient);
}
```

- [ ] **Step 9: Create `DiscountCodeAssigned.java`** domain event

```java
package com.pilarestilo.discount.domain.events;

import com.pilarestilo.shared.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record DiscountCodeAssigned(
    UUID discountId,
    String code,
    UUID assignedUserId,
    Instant occurredAt
) implements DomainEvent {

    public DiscountCodeAssigned(UUID discountId, String code, UUID assignedUserId) {
        this(discountId, code, assignedUserId, Instant.now());
    }
}
```

- [ ] **Step 10: Create `DiscountNotificationListener.java`**

```java
package com.pilarestilo.notification.infrastructure.listeners;

import com.pilarestilo.discount.domain.events.DiscountCodeAssigned;
import com.pilarestilo.notification.domain.model.NotificationRecipient;
import com.pilarestilo.notification.domain.ports.InAppNotificationPort;
import com.pilarestilo.notification.domain.ports.NotificationSender;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DiscountNotificationListener {

    private final InAppNotificationPort inAppNotificationPort;
    private final NotificationSender notificationSender;
    private final UserRepository userRepository;

    public DiscountNotificationListener(InAppNotificationPort inAppNotificationPort,
                                         NotificationSender notificationSender,
                                         UserRepository userRepository) {
        this.inAppNotificationPort = inAppNotificationPort;
        this.notificationSender = notificationSender;
        this.userRepository = userRepository;
    }

    @EventListener
    public void onDiscountCodeAssigned(DiscountCodeAssigned event) {
        inAppNotificationPort.notifyDiscountCodeAssigned(event.assignedUserId(), event.code());

        userRepository.findById(event.assignedUserId()).ifPresent(user ->
            notificationSender.sendDiscountCodeAssigned(
                event.code(),
                NotificationRecipient.of(user.getPhone(), user.getEmail(),
                    user.getNotificationChannelPreference().name())
            )
        );
    }
}
```

- [ ] **Step 11: Verify backend starts with no compilation errors**

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/notification/
git add backend/src/main/java/com/pilarestilo/discount/domain/events/DiscountCodeAssigned.java
git commit -m "feat(notification): sendDiscountCodeAssigned on all adapters + DiscountNotificationListener"
```

---

## Task 8: Add assignedUserId to Discount domain + persistence

**Files:**
- Modify: `Discount.java`, `DiscountEntity.java`, `DiscountRepositoryAdapter.java`, `DiscountDto.java`, `DiscountMapper.java`
- Test: `backend/src/test/java/com/pilarestilo/discount/application/usecases/ValidateDiscountForUserUseCaseTest.java`

- [ ] **Step 1: Write failing test for user-specific validation**

```java
package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidateDiscountForUserUseCaseTest {

    @Mock DiscountRepository discountRepository;
    @InjectMocks ValidateDiscountForUserUseCase useCase;

    @Test
    void throwsWhenCodeAssignedToOtherUser() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        Discount d = Discount.create("CODE", com.pilarestilo.discount.domain.enums.DiscountType.FIXED,
            BigDecimal.TEN, Money.zero(),
            LocalDate.now(), LocalDate.now().plusDays(10), 5);
        d.setAssignedUserId(owner);

        when(discountRepository.findByCode("CODE")).thenReturn(Optional.of(d));
        when(discountRepository.hasUserUsedDiscount(d.getId(), other)).thenReturn(false);

        assertThrows(DomainException.class, () ->
            useCase.execute("CODE", BigDecimal.valueOf(1000), other));
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd backend && mvn test -Dtest=ValidateDiscountForUserUseCaseTest -q 2>&1 | tail -5
```

- [ ] **Step 3: Add `assignedUserId` to `Discount.java`**

After the `active` field, add:
```java
private UUID assignedUserId;
```

Add getter and setter:
```java
public UUID getAssignedUserId() { return assignedUserId; }
public void setAssignedUserId(UUID assignedUserId) { this.assignedUserId = assignedUserId; }
```

- [ ] **Step 4: Update `ValidateDiscountForUserUseCase.java`** — add check after `validate()` call

```java
@Transactional(readOnly = true)
public DiscountDto execute(String code, BigDecimal subtotalAmount, UUID userId) {
    Discount discount = discountRepository.findByCode(code.toUpperCase())
        .orElseThrow(() -> new DomainException("Código de descuento no encontrado"));

    discount.validate(Money.of(subtotalAmount));

    if (discount.getAssignedUserId() != null && !discount.getAssignedUserId().equals(userId)) {
        throw new DomainException("Este código no está disponible para tu cuenta");
    }

    if (discountRepository.hasUserUsedDiscount(discount.getId(), userId)) {
        throw new DomainException("Ya usaste este código de descuento");
    }

    return DiscountMapper.toDto(discount);
}
```

- [ ] **Step 5: Run test — expect PASS**

```bash
cd backend && mvn test -Dtest=ValidateDiscountForUserUseCaseTest -q
```

- [ ] **Step 6: Update `DiscountEntity.java`** — add column

```java
@Column(name = "assigned_user_id")
private UUID assignedUserId;

public UUID getAssignedUserId() { return assignedUserId; }
public void setAssignedUserId(UUID assignedUserId) { this.assignedUserId = assignedUserId; }
```

- [ ] **Step 7: Update `DiscountRepositoryAdapter.java`** — propagate `assignedUserId` in `toEntity` and `toDomain`

In `toEntity`, add after `entity.setActive(...)`:
```java
entity.setAssignedUserId(discount.getAssignedUserId());
```

In `toDomain`, add after `discount.setActive(...)`:
```java
discount.setAssignedUserId(entity.getAssignedUserId());
```

- [ ] **Step 8: Update `DiscountDto.java`** — add three fields

```java
public record DiscountDto(
    UUID id,
    String code,
    String type,
    BigDecimal value,
    BigDecimal minOrderAmount,
    String minOrderCurrency,
    LocalDate validFrom,
    LocalDate validUntil,
    int maxUses,
    int timesUsed,
    boolean active,
    UUID assignedUserId,
    String assignedUserName,
    String assignedUserEmail
) {}
```

- [ ] **Step 9: Update `DiscountMapper.java`** — two overloads

```java
public class DiscountMapper {

    private DiscountMapper() {}

    public static DiscountDto toDto(Discount discount) {
        return toDto(discount, null, null);
    }

    public static DiscountDto toDto(Discount discount, String assignedUserName, String assignedUserEmail) {
        return new DiscountDto(
            discount.getId(), discount.getCode(), discount.getType().name(),
            discount.getValue(), discount.getMinOrderAmount().amount(),
            discount.getMinOrderAmount().currency(),
            discount.getValidFrom(), discount.getValidUntil(),
            discount.getMaxUses(), discount.getTimesUsed(), discount.isActive(),
            discount.getAssignedUserId(), assignedUserName, assignedUserEmail
        );
    }
}
```

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/discount/
git add backend/src/test/java/com/pilarestilo/discount/
git commit -m "feat(discount): assignedUserId on Discount model, entity, DTO, and mapper"
```

---

## Task 9: CreateDiscountUseCase + ListDiscountsUseCase + GetDiscountUseCase + request + controller

**Files:**
- Modify: `CreateDiscountUseCase.java`, `ListDiscountsUseCase.java`, `GetDiscountUseCase.java`, `CreateDiscountRequest.java`, `DiscountController.java`

- [ ] **Step 1: Update `CreateDiscountRequest.java`** — add optional `assignedUserId`

```java
public record CreateDiscountRequest(
    @NotBlank(message = "Code is required")
    String code,

    @NotBlank(message = "Type is required")
    String type,

    @NotNull(message = "Value is required")
    @DecimalMin(value = "0.01", message = "Value must be positive")
    BigDecimal value,

    BigDecimal minOrderAmount,

    @NotNull(message = "validFrom is required")
    LocalDate validFrom,

    @NotNull(message = "validUntil is required")
    LocalDate validUntil,

    @Min(value = 1, message = "maxUses must be at least 1")
    int maxUses,

    UUID assignedUserId
) {}
```

- [ ] **Step 2: Update `CreateDiscountUseCase.java`** — accept `assignedUserId`, validate user exists, publish event

```java
@Service
public class CreateDiscountUseCase {

    private final DiscountRepository discountRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;

    public CreateDiscountUseCase(DiscountRepository discountRepository,
                                  UserRepository userRepository,
                                  DomainEventPublisher eventPublisher) {
        this.discountRepository = discountRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DiscountDto execute(String code, String type, BigDecimal value,
                                BigDecimal minOrderAmount, LocalDate validFrom,
                                LocalDate validUntil, int maxUses, UUID assignedUserId) {
        if (discountRepository.findByCode(code.toUpperCase()).isPresent()) {
            throw new DomainException("Discount code already exists: " + code);
        }
        if (assignedUserId != null && !userRepository.existsById(assignedUserId)) {
            throw new DomainException("Assigned user not found: " + assignedUserId);
        }

        DiscountType discountType = DiscountType.valueOf(type);
        Money minAmount = Money.of(minOrderAmount != null ? minOrderAmount : BigDecimal.ZERO);

        Discount discount = Discount.create(code, discountType, value, minAmount, validFrom, validUntil, maxUses);
        discount.setAssignedUserId(assignedUserId);
        DiscountDto dto = DiscountMapper.toDto(discountRepository.save(discount));

        if (assignedUserId != null) {
            eventPublisher.publish(new DiscountCodeAssigned(discount.getId(), discount.getCode(), assignedUserId));
        }

        return dto;
    }
}
```

Also add the missing imports:
```java
import com.pilarestilo.discount.domain.events.DiscountCodeAssigned;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.user.domain.ports.UserRepository;
```

- [ ] **Step 3: Update `DiscountController.java`** — pass `assignedUserId` to use case

In the `create` method, update the `createDiscountUseCase.execute(...)` call:
```java
DiscountDto dto = createDiscountUseCase.execute(
    request.code(), request.type(), request.value(),
    request.minOrderAmount(), request.validFrom(), request.validUntil(),
    request.maxUses(), request.assignedUserId()
);
```

- [ ] **Step 4: Update `ListDiscountsUseCase.java`** — enrich with user name/email

```java
@Service
public class ListDiscountsUseCase {

    private final DiscountRepository discountRepository;
    private final UserRepository userRepository;

    public ListDiscountsUseCase(DiscountRepository discountRepository,
                                 UserRepository userRepository) {
        this.discountRepository = discountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<DiscountDto> execute(Pageable pageable) {
        return discountRepository.findAll(pageable).map(this::enrich);
    }

    @Transactional(readOnly = true)
    public List<DiscountDto> executeByStatus(String status) {
        return discountRepository.findAllByStatus(status).stream().map(this::enrich).toList();
    }

    private DiscountDto enrich(Discount d) {
        if (d.getAssignedUserId() == null) return DiscountMapper.toDto(d);
        return userRepository.findById(d.getAssignedUserId())
            .map(u -> DiscountMapper.toDto(d, u.getFullName(), u.getEmail()))
            .orElse(DiscountMapper.toDto(d));
    }
}
```

- [ ] **Step 5: Update `GetDiscountUseCase.java`** — enrich

```java
@Service
public class GetDiscountUseCase {

    private final DiscountRepository discountRepository;
    private final UserRepository userRepository;

    public GetDiscountUseCase(DiscountRepository discountRepository,
                               UserRepository userRepository) {
        this.discountRepository = discountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public DiscountDto execute(UUID id) {
        Discount d = discountRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Discount not found: " + id));
        if (d.getAssignedUserId() == null) return DiscountMapper.toDto(d);
        return userRepository.findById(d.getAssignedUserId())
            .map(u -> DiscountMapper.toDto(d, u.getFullName(), u.getEmail()))
            .orElse(DiscountMapper.toDto(d));
    }
}
```

- [ ] **Step 6: Verify backend starts and `POST /api/discounts` with `assignedUserId` field compiles**

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/discount/
git commit -m "feat(discount): user assignment in create, list, and get use cases"
```

---

## Task 10: User search backend

**Files:**
- Create: `backend/src/main/java/com/pilarestilo/user/application/dto/UserSearchResultDto.java`
- Create: `backend/src/main/java/com/pilarestilo/user/application/usecases/SearchUsersUseCase.java`
- Modify: `UserRepository.java`, `UserJpaRepository.java`, `UserRepositoryAdapter.java`, `UserController.java`

- [ ] **Step 1: Create `UserSearchResultDto.java`**

```java
package com.pilarestilo.user.application.dto;

import java.util.UUID;

public record UserSearchResultDto(UUID id, String fullName, String email) {}
```

- [ ] **Step 2: Add `searchByQuery` to `UserRepository.java`**

```java
import java.util.List;
// ...
List<User> searchByQuery(String query, int limit);
```

- [ ] **Step 3: Add JPQL query to `UserJpaRepository.java`**

```java
@Query("SELECT u FROM UserEntity u WHERE u.active = true AND " +
       "(LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))) " +
       "ORDER BY u.fullName")
List<UserEntity> searchByNameOrEmail(@Param("q") String q, Pageable pageable);
```

Add `import org.springframework.data.repository.query.Param;` if not present.

- [ ] **Step 4: Implement in `UserRepositoryAdapter.java`**

```java
@Override
public List<User> searchByQuery(String query, int limit) {
    return jpaRepository.searchByNameOrEmail(query, PageRequest.of(0, limit))
        .stream().map(this::toDomain).toList();
}
```

Add import: `import org.springframework.data.domain.PageRequest;`

- [ ] **Step 5: Create `SearchUsersUseCase.java`**

```java
package com.pilarestilo.user.application.usecases;

import com.pilarestilo.user.application.dto.UserSearchResultDto;
import com.pilarestilo.user.domain.ports.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class SearchUsersUseCase {

    private final UserRepository userRepository;

    public SearchUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSearchResultDto> execute(String query) {
        if (query == null || query.isBlank()) return List.of();
        return userRepository.searchByQuery(query.trim(), 10)
            .stream()
            .map(u -> new UserSearchResultDto(u.getId(), u.getFullName(), u.getEmail()))
            .toList();
    }
}
```

- [ ] **Step 6: Add search endpoint to `UserController.java`**

Inject `SearchUsersUseCase` in constructor and add:

```java
@GetMapping("/search")
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
public List<UserSearchResultDto> search(@RequestParam String q) {
    return searchUsersUseCase.execute(q);
}
```

Add import: `import com.pilarestilo.user.application.dto.UserSearchResultDto;`

- [ ] **Step 7: Test with curl (backend running)**

```bash
curl -s -H "Authorization: Bearer <admin-token>" "http://localhost:8080/api/users/search?q=test"
# Expected: JSON array
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/pilarestilo/user/
git commit -m "feat(user): user search endpoint for discount code assignment autocomplete"
```

---

## Task 11: Frontend API types + functions

**Files:**
- Modify: `frontend/src/lib/api.ts`

- [ ] **Step 1: Add TypeScript types** near existing discount/notification types

```typescript
export interface InAppNotificationDto {
  id: string;
  type: 'DISCOUNT_CODE_ASSIGNED' | 'ORDER_CONFIRMED' | 'PAYMENT_RECEIVED' | 'ORDER_SHIPPED';
  title: string;
  body: string;
  metadata: Record<string, unknown> | null;
  read: boolean;
  createdAt: string;
}

export interface PageDto<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface UserSearchResultDto {
  id: string;
  fullName: string;
  email: string;
}
```

Also extend `DiscountCodeDto` with the new fields:
```typescript
// In existing DiscountCodeDto interface, add:
assignedUserId?: string | null;
assignedUserName?: string | null;
assignedUserEmail?: string | null;
```

And extend `CreateDiscountCodeRequest`:
```typescript
// In existing CreateDiscountCodeRequest interface, add:
assignedUserId?: string | null;
```

- [ ] **Step 2: Add API functions** at the end of the file (before the last export if any, otherwise just append):

```typescript
export async function getNotifications(
  token: string,
  page = 0,
  size = 20,
): Promise<PageDto<InAppNotificationDto>> {
  return apiFetch<PageDto<InAppNotificationDto>>(
    `/notifications?page=${page}&size=${size}`,
    { headers: authHeaders(token) },
  );
}

export async function getRecentNotifications(
  token: string,
  size = 5,
): Promise<PageDto<InAppNotificationDto>> {
  return apiFetch<PageDto<InAppNotificationDto>>(
    `/notifications?page=0&size=${size}&recentOnly=true`,
    { headers: authHeaders(token) },
  );
}

export async function getUnreadNotificationsCount(
  token: string,
): Promise<{ count: number }> {
  return apiFetch<{ count: number }>('/notifications/unread-count', {
    headers: authHeaders(token),
  });
}

export async function markNotificationRead(
  id: string,
  token: string,
): Promise<void> {
  return apiFetch<void>(`/notifications/${id}/read`, {
    method: 'PUT',
    headers: authHeaders(token),
  });
}

export async function markAllNotificationsRead(token: string): Promise<void> {
  return apiFetch<void>('/notifications/read-all', {
    method: 'PUT',
    headers: authHeaders(token),
  });
}

export async function searchUsers(
  query: string,
  token: string,
): Promise<UserSearchResultDto[]> {
  const q = encodeURIComponent(query);
  return apiFetch<UserSearchResultDto[]>(`/users/search?q=${q}`, {
    headers: authHeaders(token),
  });
}
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit 2>&1 | head -20
```

Expected: no errors related to the new types.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/api.ts
git commit -m "feat(frontend): notification and user search API types and functions"
```

---

## Task 12: NavNotificationBell refactor

**Files:**
- Modify: `frontend/src/islands/NavNotificationBell.tsx`

- [ ] **Step 1: Replace the entire file content**

```tsx
import { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Bell, X } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../lib/authStore';
import {
  getMyProfile,
  getRecentNotifications,
  getUnreadNotificationsCount,
  markNotificationRead,
  type InAppNotificationDto,
} from '../lib/api';

interface Props {
  locale: 'es' | 'en';
}

function getTheme() {
  if (typeof document === 'undefined') return 'dark';
  return document.documentElement.getAttribute('data-theme') ?? 'dark';
}

function relativeTime(iso: string, es: boolean): string {
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (diff < 60) return es ? 'hace un momento' : 'just now';
  if (diff < 3600) return es ? `hace ${Math.floor(diff / 60)} min` : `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return es ? `hace ${Math.floor(diff / 3600)}h` : `${Math.floor(diff / 3600)}h ago`;
  return es ? `hace ${Math.floor(diff / 86400)}d` : `${Math.floor(diff / 86400)}d ago`;
}

export default function NavNotificationBell({ locale }: Props) {
  const { user, token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [open, setOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [recent, setRecent] = useState<InAppNotificationDto[]>([]);
  const [configAlerts, setConfigAlerts] = useState<{ id: string; message: string; href: string }[]>([]);
  const [theme, setTheme] = useState<string>('dark');
  const [dropdownPos, setDropdownPos] = useState({ top: 0, right: 0 });
  const btnRef = useRef<HTMLButtonElement>(null);
  const dropRef = useRef<HTMLDivElement>(null);
  const es = locale === 'es';

  useEffect(() => {
    setTheme(getTheme());
    const obs = new MutationObserver(() => setTheme(getTheme()));
    obs.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => obs.disconnect();
  }, []);

  const fetchData = async () => {
    if (!effectiveToken || !user) return;
    try {
      const [countRes, recentRes, profile] = await Promise.all([
        getUnreadNotificationsCount(effectiveToken),
        getRecentNotifications(effectiveToken, 5),
        getMyProfile(effectiveToken),
      ]);
      setUnreadCount(countRes.count);
      setRecent(recentRes.content);

      const alerts: { id: string; message: string; href: string }[] = [];
      if (!profile.notificationChannelPreference || profile.notificationChannelPreference === 'AUTO') {
        alerts.push({
          id: 'notif-channel',
          message: es
            ? 'Configura tu canal de notificaciones para recibir actualizaciones de pedidos.'
            : 'Set up your notification channel to receive order updates.',
          href: `/${locale}/account?tab=profile`,
        });
      }
      if (!profile.phone) {
        alerts.push({
          id: 'phone',
          message: es
            ? 'Agrega tu número de teléfono para comunicación más rápida.'
            : 'Add your phone number for faster communication.',
          href: `/${locale}/account?tab=profile`,
        });
      }
      setConfigAlerts(alerts);
    } catch {
      // ignore
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 60_000);
    return () => clearInterval(interval);
  }, [user?.id, effectiveToken]);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      const t = e.target as Node;
      if (btnRef.current && !btnRef.current.contains(t) && dropRef.current && !dropRef.current.contains(t)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  function calcPos() {
    if (!btnRef.current) return;
    const rect = btnRef.current.getBoundingClientRect();
    setDropdownPos({ top: rect.bottom + 10, right: window.innerWidth - rect.right });
  }

  useEffect(() => {
    if (!open) return;
    calcPos();
    window.addEventListener('scroll', calcPos, { passive: true });
    window.addEventListener('resize', calcPos, { passive: true });
    return () => { window.removeEventListener('scroll', calcPos); window.removeEventListener('resize', calcPos); };
  }, [open]);

  if (!user) return null;

  const dark = theme === 'dark';
  const bg = dark ? '#1c1c1c' : '#ffffff';
  const border = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.12)';
  const shadow = dark
    ? '0 12px 48px rgba(0,0,0,0.70), 0 2px 10px rgba(0,0,0,0.50)'
    : '0 12px 48px rgba(0,0,0,0.16), 0 2px 10px rgba(0,0,0,0.09)';
  const divider = dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.07)';
  const label = dark ? '#777' : '#aaa';
  const text = dark ? '#d6d6d6' : '#333';
  const subtext = dark ? '#888' : '#999';
  const hoverBg = dark ? 'rgba(183,110,121,0.10)' : 'rgba(183,110,121,0.06)';
  const closeClr = dark ? '#555' : '#bbb';
  const closeHov = dark ? '#999' : '#666';
  const linkClr = '#B76E79';
  const linkHov = dark ? '#d4929d' : '#8B4A55';
  const unreadDot = '#B76E79';
  const unreadBg = dark ? 'rgba(183,110,121,0.08)' : 'rgba(183,110,121,0.04)';

  const handleNotifClick = async (n: InAppNotificationDto) => {
    setOpen(false);
    if (!n.read && effectiveToken) {
      await markNotificationRead(n.id, effectiveToken).catch(() => {});
      setUnreadCount(c => Math.max(0, c - 1));
    }
    const link = n.metadata?.link as string | undefined;
    if (link) window.location.href = link;
  };

  const dropdown = (
    <div
      ref={dropRef}
      style={{ position: 'fixed', top: dropdownPos.top, right: dropdownPos.right, width: '320px', backgroundColor: bg, border: `1px solid ${border}`, boxShadow: shadow, zIndex: 9999 }}
    >
      <div style={{ borderBottom: `1px solid ${divider}`, padding: '10px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontFamily: 'var(--font-sans, sans-serif)', fontSize: '0.62rem', letterSpacing: '0.18em', textTransform: 'uppercase', color: label }}>
          {es ? 'Notificaciones' : 'Notifications'}
        </span>
        <button onClick={() => setOpen(false)} style={{ color: closeClr, background: 'none', border: 'none', cursor: 'pointer', padding: '2px', lineHeight: 0 }}
          onMouseEnter={e => (e.currentTarget.style.color = closeHov)} onMouseLeave={e => (e.currentTarget.style.color = closeClr)}>
          <X size={13} />
        </button>
      </div>

      {recent.length === 0 && configAlerts.length === 0 && (
        <div style={{ padding: '20px 16px', textAlign: 'center', fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.74rem', color: subtext }}>
          {es ? 'Sin notificaciones' : 'No notifications'}
        </div>
      )}

      {recent.length > 0 && (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, maxHeight: '280px', overflowY: 'auto' }}>
          {recent.map(n => (
            <li key={n.id} style={{ borderBottom: `1px solid ${divider}`, backgroundColor: n.read ? 'transparent' : unreadBg }}>
              <button onClick={() => handleNotifClick(n)}
                style={{ display: 'flex', gap: '12px', padding: '12px 16px', textDecoration: 'none', backgroundColor: 'transparent', border: 'none', width: '100%', textAlign: 'left', cursor: 'pointer', transition: 'background-color 150ms' }}
                onMouseEnter={e => (e.currentTarget.style.backgroundColor = hoverBg)}
                onMouseLeave={e => (e.currentTarget.style.backgroundColor = 'transparent')}>
                <span style={{ marginTop: '5px', flexShrink: 0, width: '6px', height: '6px', borderRadius: '50%', backgroundColor: n.read ? 'transparent' : unreadDot, border: n.read ? `1px solid ${subtext}` : 'none' }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <p style={{ margin: 0, fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.74rem', lineHeight: '1.4', color: text, fontWeight: n.read ? 400 : 500 }}>
                    {n.title}
                  </p>
                  <p style={{ margin: '2px 0 0', fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.66rem', color: subtext, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {n.body}
                  </p>
                  <p style={{ margin: '3px 0 0', fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.60rem', color: subtext }}>
                    {relativeTime(n.createdAt, es)}
                  </p>
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}

      {configAlerts.length > 0 && (
        <>
          {recent.length > 0 && <div style={{ height: '1px', backgroundColor: divider, margin: '0 16px' }} />}
          <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {configAlerts.map(a => (
              <li key={a.id} style={{ borderBottom: `1px solid ${divider}` }}>
                <a href={a.href} onClick={() => setOpen(false)}
                  style={{ display: 'flex', gap: '12px', padding: '12px 16px', textDecoration: 'none', backgroundColor: 'transparent', transition: 'background-color 150ms' }}
                  onMouseEnter={e => (e.currentTarget.style.backgroundColor = hoverBg)}
                  onMouseLeave={e => (e.currentTarget.style.backgroundColor = 'transparent')}>
                  <span style={{ marginTop: '5px', flexShrink: 0, width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#e6a817' }} />
                  <span style={{ fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.74rem', lineHeight: '1.5', color: text }}>{a.message}</span>
                </a>
              </li>
            ))}
          </ul>
        </>
      )}

      <div style={{ padding: '10px 16px', borderTop: `1px solid ${divider}` }}>
        <a href={`/${locale}/account#notifications`} onClick={() => setOpen(false)}
          style={{ fontFamily: 'var(--font-sans,sans-serif)', fontSize: '0.62rem', letterSpacing: '0.16em', textTransform: 'uppercase', color: linkClr, textDecoration: 'none' }}
          onMouseEnter={e => (e.currentTarget.style.color = linkHov)}
          onMouseLeave={e => (e.currentTarget.style.color = linkClr)}>
          {es ? 'Ver historial →' : 'View history →'}
        </a>
      </div>
    </div>
  );

  return (
    <div style={{ position: 'relative' }}>
      <button
        ref={btnRef}
        onClick={() => setOpen(v => !v)}
        className="relative text-pe-white/40 hover:text-pe-rose-soft transition-colors duration-200 p-1 focus:outline-none"
        aria-label={unreadCount > 0 ? (es ? `${unreadCount} notificaciones sin leer` : `${unreadCount} unread notifications`) : (es ? 'Notificaciones' : 'Notifications')}
      >
        <Bell size={18} />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-pe-rose rounded-full flex items-center justify-center shadow-sm">
            <span className="font-sans text-[0.5rem] font-bold text-white leading-none">{unreadCount > 9 ? '9+' : unreadCount}</span>
          </span>
        )}
      </button>
      {open && typeof document !== 'undefined' && createPortal(dropdown, document.body)}
    </div>
  );
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit 2>&1 | grep NavNotificationBell
```

- [ ] **Step 3: Start dev server and verify bell shows, dropdown opens, history link works**

```bash
cd frontend && npm run dev
```

Navigate to any page. Bell icon should be visible. Click it — dropdown opens showing empty state or recent notifications. "Ver historial" link points to `/{locale}/account#notifications`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/islands/NavNotificationBell.tsx
git commit -m "feat(frontend): NavNotificationBell refactor - always visible, real notifications, history link"
```

---

## Task 13: NotificationHistory island + AccountPage integration

**Files:**
- Create: `frontend/src/islands/NotificationHistory.tsx`
- Modify: `frontend/src/islands/auth/AccountPage.tsx`

- [ ] **Step 1: Create `NotificationHistory.tsx`**

```tsx
import { useState, useEffect } from 'react';
import { Bell } from 'lucide-react';
import { useAuthStore, readAuthTokenCookie } from '../lib/authStore';
import {
  getNotifications,
  markAllNotificationsRead,
  type InAppNotificationDto,
  type PageDto,
} from '../lib/api';

interface Props {
  locale: 'es' | 'en';
}

const TYPE_COLORS: Record<string, string> = {
  DISCOUNT_CODE_ASSIGNED: '#16a34a',
  ORDER_CONFIRMED: '#2563eb',
  PAYMENT_RECEIVED: '#7c3aed',
  ORDER_SHIPPED: '#ea580c',
};

const TYPE_LABELS_ES: Record<string, string> = {
  DISCOUNT_CODE_ASSIGNED: 'Descuento',
  ORDER_CONFIRMED: 'Pedido',
  PAYMENT_RECEIVED: 'Pago',
  ORDER_SHIPPED: 'Envío',
};

const TYPE_LABELS_EN: Record<string, string> = {
  DISCOUNT_CODE_ASSIGNED: 'Discount',
  ORDER_CONFIRMED: 'Order',
  PAYMENT_RECEIVED: 'Payment',
  ORDER_SHIPPED: 'Shipping',
};

function relativeTime(iso: string, es: boolean): string {
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (diff < 60) return es ? 'hace un momento' : 'just now';
  if (diff < 3600) return es ? `hace ${Math.floor(diff / 60)} min` : `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return es ? `hace ${Math.floor(diff / 3600)}h` : `${Math.floor(diff / 3600)}h ago`;
  const days = Math.floor(diff / 86400);
  return es ? `hace ${days}d` : `${days}d ago`;
}

export default function NotificationHistory({ locale }: Props) {
  const { token } = useAuthStore();
  const effectiveToken = token ?? readAuthTokenCookie();
  const [page, setPage] = useState<PageDto<InAppNotificationDto> | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [marking, setMarking] = useState(false);
  const es = locale === 'es';
  const labels = es ? TYPE_LABELS_ES : TYPE_LABELS_EN;

  const load = async (p: number) => {
    if (!effectiveToken) return;
    setLoading(true);
    try {
      const result = await getNotifications(effectiveToken, p, 20);
      setPage(result);
      setCurrentPage(p);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(0); }, [effectiveToken]);

  const handleMarkAll = async () => {
    if (!effectiveToken) return;
    setMarking(true);
    try {
      await markAllNotificationsRead(effectiveToken);
      await load(currentPage);
    } finally {
      setMarking(false);
    }
  };

  const allRead = page?.content.every(n => n.read) ?? true;

  return (
    <div id="notifications" style={{ paddingTop: '2rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
        <h2 className="font-display text-2xl text-pe-black font-light" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Bell size={20} />
          {es ? 'Notificaciones' : 'Notifications'}
        </h2>
        {!allRead && (
          <button
            onClick={handleMarkAll}
            disabled={marking}
            className="font-sans text-xs text-pe-rose hover:text-pe-rose-deep transition-colors disabled:opacity-50"
          >
            {es ? 'Marcar todas como leídas' : 'Mark all as read'}
          </button>
        )}
      </div>

      {loading && (
        <div className="font-sans text-sm text-pe-charcoal/50" style={{ padding: '2rem 0', textAlign: 'center' }}>
          {es ? 'Cargando...' : 'Loading...'}
        </div>
      )}

      {!loading && page && page.content.length === 0 && (
        <div className="font-sans text-sm text-pe-charcoal/50 border border-pe-black/10 p-8 text-center">
          {es ? 'No tenés notificaciones aún.' : "You don't have any notifications yet."}
        </div>
      )}

      {!loading && page && page.content.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
          {page.content.map(n => (
            <div
              key={n.id}
              style={{
                display: 'flex', gap: '1rem', padding: '1rem',
                backgroundColor: n.read ? 'transparent' : 'rgba(183,110,121,0.04)',
                border: '1px solid rgba(0,0,0,0.07)',
                borderLeft: n.read ? '3px solid transparent' : `3px solid ${TYPE_COLORS[n.type] ?? '#B76E79'}`,
              }}
            >
              <div style={{ flexShrink: 0, marginTop: '2px' }}>
                <span style={{
                  display: 'inline-block', padding: '2px 8px', borderRadius: '2px',
                  backgroundColor: TYPE_COLORS[n.type] ?? '#B76E79',
                  color: '#fff', fontSize: '0.60rem', fontFamily: 'var(--font-sans,sans-serif)',
                  letterSpacing: '0.1em', textTransform: 'uppercase', fontWeight: 600,
                }}>
                  {labels[n.type] ?? n.type}
                </span>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <p className="font-sans text-sm text-pe-black" style={{ margin: 0, fontWeight: n.read ? 400 : 600 }}>
                  {n.title}
                </p>
                <p className="font-sans text-sm text-pe-charcoal/75" style={{ margin: '4px 0 0' }}>
                  {n.body}
                </p>
              </div>
              <div style={{ flexShrink: 0, textAlign: 'right' }}>
                <span className="font-sans" style={{ fontSize: '0.65rem', color: '#aaa' }}>
                  {relativeTime(n.createdAt, es)}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {page && page.totalPages > 1 && (
        <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center', marginTop: '1.5rem' }}>
          <button
            disabled={currentPage === 0}
            onClick={() => load(currentPage - 1)}
            className="font-sans text-xs px-3 py-1 border border-pe-black/20 disabled:opacity-30 hover:border-pe-rose transition-colors"
          >
            {es ? 'Anterior' : 'Previous'}
          </button>
          <span className="font-sans text-xs self-center text-pe-charcoal/50">
            {currentPage + 1} / {page.totalPages}
          </span>
          <button
            disabled={currentPage >= page.totalPages - 1}
            onClick={() => load(currentPage + 1)}
            className="font-sans text-xs px-3 py-1 border border-pe-black/20 disabled:opacity-30 hover:border-pe-rose transition-colors"
          >
            {es ? 'Siguiente' : 'Next'}
          </button>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Modify `AccountPage.tsx`** — add notifications tab

In `AccountPage.tsx`, change the `Tab` type:
```typescript
type Tab = 'profile' | 'reviews' | 'orders' | 'notifications';
```

Add import for `NotificationHistory` near the top:
```typescript
import NotificationHistory from '../NotificationHistory';
```

In the tab buttons section (find where the tab buttons are rendered), add after the orders tab button:
```tsx
<button
  onClick={() => setTab('notifications')}
  className={`font-sans text-xs tracking-widest uppercase pb-2 border-b-2 transition-colors ${
    tab === 'notifications'
      ? 'border-pe-rose text-pe-rose'
      : 'border-transparent text-pe-charcoal/50 hover:text-pe-charcoal'
  }`}
>
  {locale === 'es' ? 'Notificaciones' : 'Notifications'}
</button>
```

In the tab content section, add a render case for the notifications tab. Find the section that conditionally renders tab content and add:
```tsx
{tab === 'notifications' && <NotificationHistory locale={locale} />}
```

Also add this effect to auto-switch to notifications tab when URL has `#notifications`:
```typescript
useEffect(() => {
  if (typeof window !== 'undefined' && window.location.hash === '#notifications') {
    setTab('notifications');
  }
}, []);
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npx tsc --noEmit 2>&1 | grep -E "NotificationHistory|AccountPage"
```

- [ ] **Step 4: Test in browser**

Start dev server. Navigate to `/{locale}/account#notifications`. Verify: notifications tab auto-activates, history loads, empty state shows correctly if no notifications.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/islands/NotificationHistory.tsx
git add frontend/src/islands/auth/AccountPage.tsx
git commit -m "feat(frontend): NotificationHistory island and notifications tab in AccountPage"
```

---

## Task 14: DiscountCodeManager user assignment field

**Files:**
- Modify: `frontend/src/islands/admin/DiscountCodeManager.tsx`

- [ ] **Step 1: Add state and handlers for user search** after existing state declarations (find the section with `useState` calls)

```tsx
const [userQuery, setUserQuery] = useState('');
const [userResults, setUserResults] = useState<UserSearchResultDto[]>([]);
const [selectedUser, setSelectedUser] = useState<UserSearchResultDto | null>(null);
const [userSearchOpen, setUserSearchOpen] = useState(false);
```

Add import at the top of the file:
```tsx
import { searchUsers, type UserSearchResultDto } from '../../lib/api';
```

- [ ] **Step 2: Add debounced search effect** after the state declarations

```tsx
useEffect(() => {
  if (!userQuery.trim() || !token) {
    setUserResults([]);
    return;
  }
  const timer = setTimeout(async () => {
    try {
      const results = await searchUsers(userQuery, token);
      setUserResults(results);
      setUserSearchOpen(results.length > 0);
    } catch {
      setUserResults([]);
    }
  }, 300);
  return () => clearTimeout(timer);
}, [userQuery, token]);
```

- [ ] **Step 3: Add user assignment field to the create form**

In the form JSX, add this field block after the `maxUses` field and before the submit button:

```tsx
{/* User assignment */}
<div style={{ position: 'relative' }}>
  <label className="font-sans text-xs text-pe-charcoal/60 uppercase tracking-wider block mb-1">
    {locale === 'es' ? 'Asignar a usuario (opcional)' : 'Assign to user (optional)'}
  </label>
  {selectedUser ? (
    <div className="flex items-center gap-2 border border-pe-black/20 px-3 py-2 text-sm font-sans">
      <span className="text-pe-charcoal">{selectedUser.fullName}</span>
      <span className="text-pe-charcoal/50 text-xs">{selectedUser.email}</span>
      <button
        type="button"
        onClick={() => { setSelectedUser(null); setUserQuery(''); }}
        className="ml-auto text-pe-charcoal/40 hover:text-pe-charcoal"
      >
        ×
      </button>
    </div>
  ) : (
    <input
      type="text"
      value={userQuery}
      onChange={e => setUserQuery(e.target.value)}
      onFocus={() => userResults.length > 0 && setUserSearchOpen(true)}
      onBlur={() => setTimeout(() => setUserSearchOpen(false), 150)}
      placeholder={locale === 'es' ? 'Buscar por nombre o email...' : 'Search by name or email...'}
      className="w-full border border-pe-black/20 px-3 py-2 text-sm font-sans focus:outline-none focus:border-pe-rose"
    />
  )}
  {userSearchOpen && userResults.length > 0 && (
    <ul className="absolute z-50 w-full border border-pe-black/20 bg-white shadow-lg mt-0.5 max-h-48 overflow-y-auto">
      {userResults.map(u => (
        <li key={u.id}>
          <button
            type="button"
            onMouseDown={() => { setSelectedUser(u); setUserQuery(''); setUserSearchOpen(false); }}
            className="w-full text-left px-3 py-2 hover:bg-pe-rose/5 flex gap-2 items-center"
          >
            <span className="font-sans text-sm text-pe-charcoal">{u.fullName}</span>
            <span className="font-sans text-xs text-pe-charcoal/50">{u.email}</span>
          </button>
        </li>
      ))}
    </ul>
  )}
</div>
```

- [ ] **Step 4: Pass `assignedUserId` when creating discount**

In the form `onSubmit` handler, find the `createDiscountCode(...)` call and add `assignedUserId: selectedUser?.id ?? null` to the request object. Also reset `selectedUser` after successful creation:
```tsx
setSelectedUser(null);
setUserQuery('');
```

- [ ] **Step 5: Add "Disponible para" column to the discount list table**

In the table header row, add after the existing last `<th>`:
```tsx
<th className="font-sans text-xs text-pe-charcoal/50 uppercase tracking-wider text-left pb-2">
  {locale === 'es' ? 'Disponible para' : 'Available to'}
</th>
```

In the table body rows, add the corresponding `<td>`:
```tsx
<td className="font-sans text-xs text-pe-charcoal py-3 pr-4">
  {discount.assignedUserName ? (
    <span className="inline-flex items-center gap-1 border border-pe-black/15 px-2 py-0.5 text-xs">
      {discount.assignedUserName}
    </span>
  ) : (
    <span className="text-pe-charcoal/40">{locale === 'es' ? 'Todos' : 'All'}</span>
  )}
</td>
```

- [ ] **Step 6: TypeScript check**

```bash
cd frontend && npx tsc --noEmit 2>&1 | grep DiscountCodeManager
```

- [ ] **Step 7: Test in browser**

Navigate to admin discounts page. Verify: "Asignar a usuario" field appears, typing triggers autocomplete, selecting user shows chip, creating code works, table column shows user name or "Todos".

- [ ] **Step 8: Commit**

```bash
git add frontend/src/islands/admin/DiscountCodeManager.tsx
git commit -m "feat(admin): user assignment autocomplete and column in DiscountCodeManager"
```

---

## Task 15: how-we-sell.astro — discount codes section

**Files:**
- Modify: `frontend/src/pages/[locale]/how-we-sell.astro`

- [ ] **Step 1: Add discount info data** in the frontmatter script, after the `faqs` array

```astro
const discountInfo = isEs
  ? {
      title: 'Códigos de descuento',
      items: [
        'Para usar un código, ingrésalo en el carrito antes de confirmar tu compra.',
        'Cada código es personal y puede usarse una sola vez por cuenta.',
        'Los códigos no son transferibles entre cuentas ni acumulables.',
        'Si un código fue asignado específicamente a tu cuenta, solo tu usuario puede utilizarlo.',
        'Los códigos generales están disponibles para cualquier usuario hasta que se agoten los usos disponibles.',
      ],
    }
  : {
      title: 'Discount codes',
      items: [
        'To use a code, enter it in the cart before confirming your order.',
        'Each code is personal and can only be used once per account.',
        'Codes are not transferable between accounts and cannot be combined.',
        'If a code was assigned specifically to your account, only your user can redeem it.',
        'General codes are available to any user until the available uses run out.',
      ],
    };
```

- [ ] **Step 2: Add section HTML** between the process flow section and the FAQ section (after the closing `</section>` of the process flow, before the `<section class="bg-pe-black ...">` FAQ section)

```astro
<section class="py-14 md:py-20 border-t border-pe-black/10">
  <div class="pe-container">
    <h2 class="font-display text-3xl md:text-4xl text-pe-black font-light mb-6">
      {discountInfo.title}
    </h2>
    <ul class="grid grid-cols-1 md:grid-cols-2 gap-3 md:gap-4">
      {discountInfo.items.map(item => (
        <li class="flex gap-3 bg-pe-white border border-pe-black/10 p-5">
          <span class="mt-1.5 flex-shrink-0 w-1.5 h-1.5 rounded-full bg-pe-rose"></span>
          <p class="font-sans text-sm text-pe-charcoal/80 leading-relaxed">{item}</p>
        </li>
      ))}
    </ul>
  </div>
</section>
```

- [ ] **Step 3: Verify page renders correctly**

With dev server running, navigate to `/es/how-we-sell` and `/en/how-we-sell`. New section appears between process flow and FAQ with 5 bullet points about discount codes.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/[locale]/how-we-sell.astro
git commit -m "feat(how-we-sell): add discount codes section explaining usage rules"
```

---

## Self-Review

After all tasks complete, run the full test suite:

```bash
cd backend && mvn test -q
```

Check for:
- All new tests pass
- No regressions in existing tests (DiscountTest, PaymentTest, etc.)

Then do a quick smoke test in browser:
1. Create a discount code assigned to a user → verify in-app notification appears for that user
2. Bell icon shows on all pages even when logged in with 0 notifications
3. Click bell → dropdown opens, "Ver historial" link works
4. Account page `#notifications` auto-selects the Notifications tab
5. `/es/how-we-sell` shows the new discount codes section
6. Trying to use another user's assigned code returns 422 error in cart
