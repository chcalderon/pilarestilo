package com.pilarestilo.user.infrastructure.persistence.entities;

import com.pilarestilo.user.domain.enums.UserRole;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "notification_channel_preference", nullable = false, length = 20)
    private String notificationChannelPreference = "AUTO";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "avatar_manually_set", nullable = false)
    private boolean avatarManuallySet = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "worker_vigency_start")
    private java.time.LocalDate workerVigencyStart;

    @Column(name = "worker_vigency_end")
    private java.time.LocalDate workerVigencyEnd;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNotificationChannelPreference() { return notificationChannelPreference; }
    public void setNotificationChannelPreference(String notificationChannelPreference) {
        this.notificationChannelPreference = notificationChannelPreference;
    }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isAvatarManuallySet() { return avatarManuallySet; }
    public void setAvatarManuallySet(boolean avatarManuallySet) { this.avatarManuallySet = avatarManuallySet; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public java.time.LocalDate getWorkerVigencyStart() { return workerVigencyStart; }
    public void setWorkerVigencyStart(java.time.LocalDate d) { this.workerVigencyStart = d; }
    public java.time.LocalDate getWorkerVigencyEnd() { return workerVigencyEnd; }
    public void setWorkerVigencyEnd(java.time.LocalDate d) { this.workerVigencyEnd = d; }
}
