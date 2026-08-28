package com.pilarestilo.notificationservice.domain.model;

import com.pilarestilo.notificationservice.domain.enums.NotificationType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class InAppNotification {

    private UUID id;
    private UUID userId;
    private NotificationType type;
    private String title;
    private String body;
    private Map<String, Object> metadata;
    private Instant readAt;
    private Instant createdAt;

    private InAppNotification() {}

    public static InAppNotification create(UUID userId, NotificationType type,
                                            String title, String body,
                                            Map<String, Object> metadata) {
        InAppNotification n = new InAppNotification();
        n.userId = userId;
        n.type = type;
        n.title = title;
        n.body = body;
        n.metadata = metadata;
        n.createdAt = Instant.now();
        return n;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRead() { return readAt != null; }

    public void setId(UUID id) { this.id = id; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
