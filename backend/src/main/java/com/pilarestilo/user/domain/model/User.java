package com.pilarestilo.user.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.user.domain.enums.NotificationChannelPreference;
import com.pilarestilo.user.domain.enums.UserRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class User {

    private static final String WHATSAPP_PREFIX = "whatsapp:";

    private UUID id;
    private String email;
    private String fullName;
    private String phone;
    private NotificationChannelPreference notificationChannelPreference;
    private UserRole role;
    private boolean active;
    private String passwordHash;
    private int sessionVersion = 1;
    private String avatarUrl;
    private boolean avatarManuallySet;
    private Instant createdAt;
    private LocalDate workerVigencyStart;
    private LocalDate workerVigencyEnd;

    private User() {}

    /**
     * The single normalization every email lookup or write must agree on. {@link #create} always
     * stores this form, so a caller comparing against the raw value a customer typed -- a login
     * attempt, a duplicate-registration check -- has to normalize the same way first, or it
     * compares "Maria@Gmail.com" against the stored "maria@gmail.com" and finds nothing.
     */
    public static String normalizeEmail(String rawEmail) {
        return rawEmail == null ? null : rawEmail.trim().toLowerCase();
    }

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
        user.email = normalizeEmail(email);
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

    // One parameter per column a user actually carries; rehydration follows the schema.
    @SuppressWarnings("java:S107")
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

    @SuppressWarnings("java:S107")
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
    public int getSessionVersion() { return sessionVersion; }
    public String getAvatarUrl() { return avatarUrl; }
    public boolean isAvatarManuallySet() { return avatarManuallySet; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Stops this person being identifiable, without removing the row.
     *
     * <p>The row stays because orders, payments and boletas point at it, and those have retention
     * of their own — six years for a tax document. What goes is everything that names a human: the
     * address becomes an opaque one derived from the id, the name a placeholder, the phone and the
     * avatar nothing. The account is deactivated in the same move, since there is no longer an
     * address to sign in with.
     *
     * <p>Deliberately not reversible, and deliberately not a delete: a deleted user would leave a
     * boleta pointing at nothing, and the shop would be unable to answer for a sale it made.
     */
    public void anonymise() {
        this.email = "anonimo+" + id + "@pilarestilo.invalid";
        this.fullName = "Cliente anonimizado";
        this.phone = null;
        this.avatarUrl = null;
        this.avatarManuallySet = false;
        this.active = false;
        // The hash is replaced rather than blanked: a null would let a login path that skips the
        // check through, and an empty string is a password somebody could type.
        this.passwordHash = "anonymised-" + UUID.randomUUID();
    }

    public void updateAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void markAvatarAsManual() { this.avatarManuallySet = true; }
    public void setAvatarManuallySet(boolean avatarManuallySet) { this.avatarManuallySet = avatarManuallySet; }

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

    /**
     * Bumped on every password change, self-service or admin-forced. Every JWT carries the value
     * it was minted with; the auth filter rejects a token whose value is behind this one, which
     * is how a reset logs every existing session out without a revocation list.
     */
    public void incrementSessionVersion() {
        this.sessionVersion++;
    }

    /** Rehydration only — carries the persisted {@code session_version} back onto the aggregate. */
    public void setSessionVersion(int sessionVersion) {
        if (sessionVersion < 1) {
            throw new DomainException("User session version must be at least 1");
        }
        this.sessionVersion = sessionVersion;
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

    public LocalDate getWorkerVigencyStart() { return workerVigencyStart; }
    public LocalDate getWorkerVigencyEnd() { return workerVigencyEnd; }
    public void setWorkerVigencyStart(LocalDate date) { this.workerVigencyStart = date; }
    public void setWorkerVigencyEnd(LocalDate date) { this.workerVigencyEnd = date; }

    private static String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String candidate = rawPhone.trim();
        if (candidate.regionMatches(true, 0, WHATSAPP_PREFIX, 0, WHATSAPP_PREFIX.length())) {
            candidate = candidate.substring(WHATSAPP_PREFIX.length()).trim();
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
        } catch (IllegalArgumentException _) {
            throw new DomainException("Unsupported notification channel preference: " + rawPreference);
        }
    }
}
