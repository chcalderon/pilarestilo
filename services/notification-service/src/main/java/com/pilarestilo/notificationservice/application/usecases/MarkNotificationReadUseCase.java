package com.pilarestilo.notificationservice.application.usecases;

import com.pilarestilo.notificationservice.domain.ports.InAppNotificationRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MarkNotificationReadUseCase {

    private final InAppNotificationRepository repository;

    public MarkNotificationReadUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    public void execute(UUID notificationId, UUID userId) {
        repository.markAsRead(notificationId, userId);
    }
}
