package com.pilarestilo.notificationservice.infrastructure.persistence.owned;

import com.pilarestilo.notificationservice.domain.model.InAppNotification;
import com.pilarestilo.notificationservice.domain.ports.InAppNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The one class that touches {@code pilarestilo_notifications}.
 *
 * <p>The writes name the notifications transaction manager. There are two in this service — this
 * database and the shared read-only one — so an unqualified {@code @Transactional} would be
 * ambiguous, and getting it wrong would run the write outside the transaction it looks like it is
 * in.
 */
@Component
public class NotificationRepositoryAdapter implements InAppNotificationRepository {

    private final NotificationJpaRepository jpa;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional("notificationsTransactionManager")
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
    @Transactional("notificationsTransactionManager")
    public void markAsRead(UUID id, UUID userId) {
        jpa.markAsRead(id, userId);
    }

    @Override
    @Transactional("notificationsTransactionManager")
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
        e.setCreatedAt(n.getCreatedAt() != null ? n.getCreatedAt() : Instant.now());
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
