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
