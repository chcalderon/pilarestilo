package com.pilarestilo.notification.application.usecases;

import com.pilarestilo.notification.application.dto.InAppNotificationDto;
import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class GetNotificationsUseCase {

    /*
     * No @Transactional here. The manager is picked by which database the repository belongs to,
     * and naming an infrastructure bean from a use case would point the dependency the wrong way.
     * Spring Data's proxies already run on the manager @EnableJpaRepositories names, and the
     * writes are wrapped by NotificationRepositoryAdapter.
     */

    private final InAppNotificationRepository repository;

    public GetNotificationsUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    public Page<InAppNotificationDto> execute(UUID userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable).map(n -> new InAppNotificationDto(
            n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
            n.getMetadata(), n.isRead(), n.getCreatedAt()
        ));
    }

    public Page<InAppNotificationDto> executeRecent(UUID userId, Pageable pageable) {
        return repository.findRecentByUserId(userId, pageable).map(n -> new InAppNotificationDto(
            n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
            n.getMetadata(), n.isRead(), n.getCreatedAt()
        ));
    }
}
