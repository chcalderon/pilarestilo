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
