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
