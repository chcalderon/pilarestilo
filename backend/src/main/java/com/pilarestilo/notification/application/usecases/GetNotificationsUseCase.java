package com.pilarestilo.notification.application.usecases;

import com.pilarestilo.notification.application.dto.InAppNotificationDto;
import com.pilarestilo.notification.domain.ports.InAppNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class GetNotificationsUseCase {

    private final InAppNotificationRepository repository;

    public GetNotificationsUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<InAppNotificationDto> execute(UUID userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable).map(n -> new InAppNotificationDto(
            n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
            n.getMetadata(), n.isRead(), n.getCreatedAt()
        ));
    }

    @Transactional(readOnly = true)
    public Page<InAppNotificationDto> executeRecent(UUID userId, Pageable pageable) {
        return repository.findRecentByUserId(userId, pageable).map(n -> new InAppNotificationDto(
            n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
            n.getMetadata(), n.isRead(), n.getCreatedAt()
        ));
    }
}
