package com.pilarestilo.user.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public class User {

    private UUID id;
    private String email;
    private String fullName;
    private UserRole role;
    private String passwordHash;
    private Instant createdAt;

    private User() {}

    public static User create(String email, String fullName, UserRole role, String passwordHash) {
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
        user.role = role;
        user.passwordHash = passwordHash;
        user.createdAt = Instant.now();
        return user;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public UserRole getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
