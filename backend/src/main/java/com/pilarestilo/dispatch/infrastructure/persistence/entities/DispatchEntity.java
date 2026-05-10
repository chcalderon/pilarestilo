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
    @Column(name = "order_shipping_zone_code", length = 24) private String orderShippingZoneCode;
    @Column(name = "order_shipping_courier_id", length = 120) private String orderShippingCourierId;
    @Column(name = "order_shipping_courier_name", length = 160) private String orderShippingCourierName;
    @Column(name = "order_shipping_address_reference") private String orderShippingAddressReference;
    @Column(name = "carrier_override_configured", length = 160) private String carrierOverrideConfigured;
    @Column(name = "carrier_override_selected", length = 160) private String carrierOverrideSelected;
    @Column(name = "carrier_override_by") private UUID carrierOverrideBy;
    @Column(name = "carrier_override_at") private LocalDateTime carrierOverrideAt;
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
    public String getOrderShippingZoneCode() { return orderShippingZoneCode; }
    public void setOrderShippingZoneCode(String orderShippingZoneCode) { this.orderShippingZoneCode = orderShippingZoneCode; }
    public String getOrderShippingCourierId() { return orderShippingCourierId; }
    public void setOrderShippingCourierId(String orderShippingCourierId) { this.orderShippingCourierId = orderShippingCourierId; }
    public String getOrderShippingCourierName() { return orderShippingCourierName; }
    public void setOrderShippingCourierName(String orderShippingCourierName) { this.orderShippingCourierName = orderShippingCourierName; }
    public String getOrderShippingAddressReference() { return orderShippingAddressReference; }
    public void setOrderShippingAddressReference(String orderShippingAddressReference) { this.orderShippingAddressReference = orderShippingAddressReference; }
    public String getCarrierOverrideConfigured() { return carrierOverrideConfigured; }
    public void setCarrierOverrideConfigured(String carrierOverrideConfigured) { this.carrierOverrideConfigured = carrierOverrideConfigured; }
    public String getCarrierOverrideSelected() { return carrierOverrideSelected; }
    public void setCarrierOverrideSelected(String carrierOverrideSelected) { this.carrierOverrideSelected = carrierOverrideSelected; }
    public UUID getCarrierOverrideBy() { return carrierOverrideBy; }
    public void setCarrierOverrideBy(UUID carrierOverrideBy) { this.carrierOverrideBy = carrierOverrideBy; }
    public LocalDateTime getCarrierOverrideAt() { return carrierOverrideAt; }
    public void setCarrierOverrideAt(LocalDateTime carrierOverrideAt) { this.carrierOverrideAt = carrierOverrideAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
