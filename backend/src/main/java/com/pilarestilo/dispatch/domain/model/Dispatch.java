package com.pilarestilo.dispatch.domain.model;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import com.pilarestilo.shared.domain.DomainException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class Dispatch {
    private UUID id;
    private UUID orderId;
    private UUID dispatcherId;
    private DispatchStatus status;
    private String carrier;
    private String trackingCode;
    private LocalDate scheduledDate;
    private LocalDateTime dispatchedAt;
    private LocalDateTime deliveredAt;
    private String notes;
    private LocalDateTime createdAt;

    private Dispatch() {}

    public static Dispatch create(UUID orderId) {
        Dispatch d = new Dispatch();
        d.id = UUID.randomUUID();
        d.orderId = orderId;
        d.status = DispatchStatus.PENDING;
        d.createdAt = LocalDateTime.now();
        return d;
    }

    public static Dispatch reconstruct(UUID id, UUID orderId, UUID dispatcherId,
                                        DispatchStatus status, String carrier, String trackingCode,
                                        LocalDate scheduledDate, LocalDateTime dispatchedAt,
                                        LocalDateTime deliveredAt, String notes, LocalDateTime createdAt) {
        Dispatch d = new Dispatch();
        d.id = id; d.orderId = orderId; d.dispatcherId = dispatcherId;
        d.status = status; d.carrier = carrier; d.trackingCode = trackingCode;
        d.scheduledDate = scheduledDate; d.dispatchedAt = dispatchedAt;
        d.deliveredAt = deliveredAt; d.notes = notes; d.createdAt = createdAt;
        return d;
    }

    public void claim(UUID dispatcherId) {
        if (status != DispatchStatus.PENDING) {
            throw new DomainException("Only PENDING dispatches can be claimed");
        }
        this.dispatcherId = dispatcherId;
        this.status = DispatchStatus.IN_PROGRESS;
    }

    public void unclaim() {
        if (status != DispatchStatus.IN_PROGRESS) {
            throw new DomainException("Only IN_PROGRESS dispatches can be unclaimed");
        }
        this.dispatcherId = null;
        this.status = DispatchStatus.PENDING;
    }

    public void dispatch(String carrier, String trackingCode, LocalDate scheduledDate, String notes) {
        if (status != DispatchStatus.IN_PROGRESS) {
            throw new DomainException("Only IN_PROGRESS dispatches can be marked as dispatched");
        }
        this.carrier = carrier;
        this.trackingCode = trackingCode;
        this.scheduledDate = scheduledDate;
        this.notes = notes;
        this.dispatchedAt = LocalDateTime.now();
        this.status = DispatchStatus.DISPATCHED;
    }

    public void deliver() {
        if (status != DispatchStatus.DISPATCHED) {
            throw new DomainException("Only DISPATCHED dispatches can be marked delivered");
        }
        this.deliveredAt = LocalDateTime.now();
        this.status = DispatchStatus.DELIVERED;
    }

    public void fail(String notes) {
        if (status != DispatchStatus.DISPATCHED && status != DispatchStatus.IN_PROGRESS) {
            throw new DomainException("Cannot fail a dispatch in status " + status);
        }
        this.notes = notes;
        this.status = DispatchStatus.FAILED;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getDispatcherId() { return dispatcherId; }
    public DispatchStatus getStatus() { return status; }
    public String getCarrier() { return carrier; }
    public String getTrackingCode() { return trackingCode; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
