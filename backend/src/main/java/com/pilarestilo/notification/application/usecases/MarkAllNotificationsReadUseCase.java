package com.pilarestilo.notification.application.usecases;

import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class MarkAllNotificationsReadUseCase {

    /*
     * No @Transactional here. The manager is picked by which database the repository belongs to,
     * and naming an infrastructure bean from a use case would point the dependency the wrong way.
     * Spring Data's proxies already run on the manager @EnableJpaRepositories names, and the
     * writes are wrapped by NotificationRepositoryAdapter.
     */

    private final InAppNotificationRepository repository;

    public MarkAllNotificationsReadUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    public void execute(UUID userId) {
        repository.markAllAsRead(userId);
    }
}
