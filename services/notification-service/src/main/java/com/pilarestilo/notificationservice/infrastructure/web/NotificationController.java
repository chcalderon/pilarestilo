package com.pilarestilo.notificationservice.infrastructure.web;

import com.pilarestilo.notificationservice.application.dto.InAppNotificationDto;
import com.pilarestilo.notificationservice.application.usecases.GetNotificationsUseCase;
import com.pilarestilo.notificationservice.application.usecases.GetUnreadCountUseCase;
import com.pilarestilo.notificationservice.application.usecases.MarkAllNotificationsReadUseCase;
import com.pilarestilo.notificationservice.application.usecases.MarkNotificationReadUseCase;
import com.pilarestilo.notificationservice.auth.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final GetNotificationsUseCase getNotificationsUseCase;
    private final GetUnreadCountUseCase getUnreadCountUseCase;
    private final MarkNotificationReadUseCase markReadUseCase;
    private final MarkAllNotificationsReadUseCase markAllReadUseCase;

    public NotificationController(GetNotificationsUseCase getNotificationsUseCase,
                                   GetUnreadCountUseCase getUnreadCountUseCase,
                                   MarkNotificationReadUseCase markReadUseCase,
                                   MarkAllNotificationsReadUseCase markAllReadUseCase) {
        this.getNotificationsUseCase = getNotificationsUseCase;
        this.getUnreadCountUseCase = getUnreadCountUseCase;
        this.markReadUseCase = markReadUseCase;
        this.markAllReadUseCase = markAllReadUseCase;
    }

    @GetMapping
    public Page<InAppNotificationDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean recentOnly,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 50));
        if (recentOnly) {
            return getNotificationsUseCase.executeRecent(currentUser.id(), pageable);
        }
        return getNotificationsUseCase.execute(currentUser.id(), pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return Map.of("count", getUnreadCountUseCase.execute(currentUser.id()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id,
                                          @AuthenticationPrincipal AuthenticatedUser currentUser) {
        markReadUseCase.execute(id, currentUser.id());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        markAllReadUseCase.execute(currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
