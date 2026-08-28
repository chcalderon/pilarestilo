package com.pilarestilo.notificationservice.application.usecases;

import com.pilarestilo.notificationservice.domain.ports.InAppNotificationRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetUnreadCountUseCase {

    private final InAppNotificationRepository repository;

    public GetUnreadCountUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    public long execute(UUID userId) {
        return repository.countUnreadByUserId(userId);
    }
}
