package com.pilarestilo.notificationservice.infrastructure.persistence.owned;

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
