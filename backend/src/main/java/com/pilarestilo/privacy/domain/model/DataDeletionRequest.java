package com.pilarestilo.privacy.domain.model;

import com.pilarestilo.privacy.domain.enums.DeletionStatus;
import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

/**
 * Somebody asking the shop to forget them.
 *
 * <p>Resolving it anonymises rather than deletes. Both laws are true at once: the Ley 21.719 gives
 * her the right to have her data removed, and the tax law obliges the shop to keep the boleta for
 * six years. What reconciles them is that a boleta already carries a snapshot of the buyer's name
 * and email, taken when it was issued, precisely so the account behind it can stop existing.
 *
 * <p>A refusal has to carry its reason. "No" without one is not an answer to a right.
 */
public class DataDeletionRequest {

    private UUID id;
    private UUID userId;
    private DeletionStatus status;
    private String reason;
    private Instant requestedAt;
    private Instant resolvedAt;
    private UUID resolvedBy;
    private String resolution;
    private Instant createdAt;

    private DataDeletionRequest() {}

    public static DataDeletionRequest open(UUID userId, String reason) {
        if (userId == null) {
            throw new DomainException("A deletion request belongs to somebody");
        }
        DataDeletionRequest request = new DataDeletionRequest();
        request.id = UUID.randomUUID();
        request.userId = userId;
        request.status = DeletionStatus.REQUESTED;
        request.reason = trimToNull(reason);
        request.requestedAt = Instant.now();
        request.createdAt = request.requestedAt;
        return request;
    }

    /** The person has been anonymised. There is no way back from here, which is the point. */
    public void markAnonymised(UUID resolvedBy) {
        assertOpen();
        this.status = DeletionStatus.ANONYMISED;
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedBy;
        this.resolution = "Datos personales anonimizados; los documentos tributarios se conservan por obligacion legal.";
    }

    /**
     * Refused, with the reason the customer is owed — an order still in flight, a dispute open,
     * a retention period that has not run out.
     */
    public void refuse(String why, UUID resolvedBy) {
        assertOpen();
        if (why == null || why.isBlank()) {
            throw new DomainException("Refusing a deletion request requires a reason");
        }
        this.status = DeletionStatus.REFUSED;
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedBy;
        this.resolution = why.trim();
    }

    public boolean isOpen() {
        return status == DeletionStatus.REQUESTED;
    }

    private void assertOpen() {
        if (status != DeletionStatus.REQUESTED) {
            throw new DomainException("This request was already resolved as " + status);
        }
    }

    public static DataDeletionRequest reconstruct(UUID id,
                                                  UUID userId,
                                                  DeletionStatus status,
                                                  String reason,
                                                  Instant requestedAt,
                                                  Instant resolvedAt,
                                                  UUID resolvedBy,
                                                  String resolution,
                                                  Instant createdAt) {
        DataDeletionRequest request = new DataDeletionRequest();
        request.id = id;
        request.userId = userId;
        request.status = status;
        request.reason = reason;
        request.requestedAt = requestedAt;
        request.resolvedAt = resolvedAt;
        request.resolvedBy = resolvedBy;
        request.resolution = resolution;
        request.createdAt = createdAt;
        return request;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public DeletionStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public UUID getResolvedBy() { return resolvedBy; }
    public String getResolution() { return resolution; }
    public Instant getCreatedAt() { return createdAt; }
}
