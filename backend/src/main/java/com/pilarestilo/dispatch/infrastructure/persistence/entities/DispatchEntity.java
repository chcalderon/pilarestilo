package com.pilarestilo.dispatch.infrastructure.persistence.entities;

import com.pilarestilo.dispatch.domain.enums.DispatchStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dispatches")
public class DispatchEntity {
    @Id private UUID id;
    @Column(name = "order_id", nullable = false, unique = true) private UUID orderId;
    @Column(name = "dispatcher_id") private UUID dispatcherId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DispatchStatus status;
    @Column(length = 100) private String carrier;
    @Column(name = "tracking_code", length = 200) private String trackingCode;
    @Column(name = "scheduled_date") private LocalDate scheduledDate;
    @Column(name = "dispatched_at") private LocalDateTime dispatchedAt;
    @Column(name = "delivered_at") private LocalDateTime deliveredAt;
    @Column private String notes;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getDispatcherId() { return dispatcherId; }
    public void setDispatcherId(UUID dispatcherId) { this.dispatcherId = dispatcherId; }
    public DispatchStatus getStatus() { return status; }
    public void setStatus(DispatchStatus status) { this.status = status; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(LocalDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
