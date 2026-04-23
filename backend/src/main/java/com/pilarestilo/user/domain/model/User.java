package com.pilarestilo.user.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.NotificationChannelPreference;
import com.pilarestilo.user.domain.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public class User {

    private UUID id;
    private String email;
    private String fullName;
    private String phone;
    private NotificationChannelPreference notificationChannelPreference;
    private UserRole role;
    private boolean active;
    private String passwordHash;
    private Instant createdAt;

    private User() {}

    public static User create(String email, String fullName, UserRole role, String passwordHash) {
        return create(email, fullName, null, null, role, passwordHash);
    }

    public static User create(String email, String fullName, String phone, UserRole role, String passwordHash) {
        return create(email, fullName, phone, null, role, passwordHash);
    }

    public static User create(
            String email,
            String fullName,
            String phone,
            String notificationChannelPreference,
            UserRole role,
            String passwordHash
    ) {
        if (email == null || email.isBlank()) {
            throw new DomainException("User email cannot be blank");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new DomainException("User full name cannot be blank");
        }
        if (role == null) {
            throw new DomainException("User role cannot be null");
        }

        User user = new User();
        user.id = UUID.randomUUID();
        user.email = email.trim().toLowerCase();
        user.fullName = fullName.trim();
        user.phone = normalizePhone(phone);
        user.notificationChannelPreference = normalizeNotificationChannelPreference(notificationChannelPreference);
        user.role = role;
        user.active = true;
        user.passwordHash = passwordHash;
        user.createdAt = Instant.now();
        return user;
    }

    public static User reconstruct(UUID id, String email, String fullName, UserRole role, boolean active, String passwordHash, Instant createdAt) {
        return reconstruct(id, email, fullName, null, null, role, active, passwordHash, createdAt);
    }

    public static User reconstruct(
            UUID id,
            String email,
            String fullName,
            String phone,
            UserRole role,
            boolean active,
            String passwordHash,
            Instant createdAt
    ) {
        return reconstruct(id, email, fullName, phone, null, role, active, passwordHash, createdAt);
    }

    public static User reconstruct(
            UUID id,
            String email,
            String fullName,
            String phone,
            String notificationChannelPreference,
            UserRole role,
            boolean active,
            String passwordHash,
            Instant createdAt
    ) {
        if (id == null) {
            throw new DomainException("User id cannot be null");
        }
        User user = create(email, fullName, phone, notificationChannelPreference, role, passwordHash);
        user.id = id;
        user.active = active;
        user.createdAt = createdAt;
        return user;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public NotificationChannelPreference getNotificationChannelPreference() { return notificationChannelPreference; }
    public UserRole getRole() { return role; }
    public boolean isActive() { return active; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }

    public void updateFullName(String newFullName) {
        if (newFullName == null || newFullName.isBlank()) {
            throw new DomainException("User full name cannot be blank");
        }
        this.fullName = newFullName.trim();
    }

    public void changeRole(UserRole newRole) {
        if (newRole == null) {
            throw new DomainException("User role cannot be null");
        }
        this.role = newRole;
    }

    public void changePasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new DomainException("User password hash cannot be blank");
        }
        this.passwordHash = newPasswordHash;
    }

    public void updatePhone(String newPhone) {
        this.phone = normalizePhone(newPhone);
    }

    public void updateNotificationChannelPreference(String newPreference) {
        this.notificationChannelPreference = normalizeNotificationChannelPreference(newPreference);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setId(UUID id) { this.id = id; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    private static String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String candidate = rawPhone.trim();
        if (candidate.regionMatches(true, 0, "whatsapp:", 0, "whatsapp:".length())) {
            candidate = candidate.substring("whatsapp:".length()).trim();
        }
        String digits = candidate.replaceAll("\\D", "");
        if (digits.length() < 8 || digits.length() > 15) {
            throw new DomainException("User phone must contain between 8 and 15 digits");
        }
        return "+" + digits;
    }

    private static NotificationChannelPreference normalizeNotificationChannelPreference(String rawPreference) {
        try {
            return NotificationChannelPreference.fromRaw(rawPreference);
        } catch (IllegalArgumentException ex) {
            throw new DomainException("Unsupported notification channel preference: " + rawPreference);
        }
    }
}
