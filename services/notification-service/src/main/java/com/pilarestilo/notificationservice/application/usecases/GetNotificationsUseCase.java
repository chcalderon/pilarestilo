package com.pilarestilo.notificationservice.application.usecases;

import com.pilarestilo.notificationservice.application.dto.InAppNotificationDto;
import com.pilarestilo.notificationservice.domain.ports.InAppNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetNotificationsUseCase {

    private final InAppNotificationRepository repository;

    public GetNotificationsUseCase(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    public Page<InAppNotificationDto> execute(UUID userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable).map(GetNotificationsUseCase::toDto);
    }

    public Page<InAppNotificationDto> executeRecent(UUID userId, Pageable pageable) {
        return repository.findRecentByUserId(userId, pageable).map(GetNotificationsUseCase::toDto);
    }

    private static InAppNotificationDto toDto(com.pilarestilo.notificationservice.domain.model.InAppNotification n) {
        return new InAppNotificationDto(
                n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
                n.getMetadata(), n.isRead(), n.getCreatedAt());
    }
}
