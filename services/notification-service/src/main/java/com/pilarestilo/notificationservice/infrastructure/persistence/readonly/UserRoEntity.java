package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.util.UUID;

/** Read-only view of {@code users}: only what a notification recipient needs. */
@Entity
@Immutable
@Table(name = "users")
public class UserRoEntity {

    @Id
    private UUID id;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "role")
    private String role;

    @Column(name = "active")
    private boolean active;

    @Column(name = "notification_channel_preference")
    private String notificationChannelPreference;

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public String getNotificationChannelPreference() { return notificationChannelPreference; }
}
