package com.pilarestilo.notification.application.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InAppNotificationDto(
    UUID id,
    String type,
    String title,
    String body,
    Map<String, Object> metadata,
    boolean read,
    Instant createdAt
) {}
