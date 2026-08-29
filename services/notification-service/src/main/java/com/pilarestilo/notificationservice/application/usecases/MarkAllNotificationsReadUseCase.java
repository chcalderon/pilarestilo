package com.pilarestilo.notificationservice.application.usecases;

import com.pilarestilo.notificationservice.domain.ports.InAppNotificationRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MarkAllNotificationsReadUseCase {

    private final InAppNotificationRepository repository;

    public MarkAllNotificationsReadUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    public void execute(UUID userId) {
        repository.markAllAsRead(userId);
    }
}
