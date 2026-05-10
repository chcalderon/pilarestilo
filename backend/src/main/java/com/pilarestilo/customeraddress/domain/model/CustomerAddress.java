package com.pilarestilo.customeraddress.domain.model;

import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

public class CustomerAddress {

    private UUID id;
    private UUID customerId;
    private String label;
    private String recipientName;
    private String phone;
    private String line1;
    private String line2;
    private String comuna;
    private String city;
    private String region;
    private String reference;
    private boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;

    private CustomerAddress() {
    }

    public static CustomerAddress create(
            UUID customerId,
            String label,
            String recipientName,
            String phone,
            String line1,
            String line2,
            String comuna,
            String city,
            String region,
            String reference,
            boolean isDefault
    ) {
        if (customerId == null) {
            throw new DomainException("Customer id is required");
        }

        CustomerAddress address = new CustomerAddress();
        address.id = UUID.randomUUID();
        address.customerId = customerId;
        address.label = normalizeRequired(label, "Address label");
        address.recipientName = normalizeRequired(recipientName, "Recipient name");
        address.phone = normalizePhone(phone);
        address.line1 = normalizeRequired(line1, "Address line1");
        address.line2 = normalizeOptional(line2);
        address.comuna = normalizeRequired(comuna, "Comuna");
        address.city = normalizeRequired(city, "City");
        address.region = normalizeRequired(region, "Region");
        address.reference = normalizeOptional(reference);
        address.isDefault = isDefault;
        address.createdAt = Instant.now();
        address.updatedAt = address.createdAt;
        return address;
    }

    public static CustomerAddress reconstruct(
            UUID id,
            UUID customerId,
            String label,
            String recipientName,
            String phone,
            String line1,
            String line2,
            String comuna,
            String city,
            String region,
            String reference,
            boolean isDefault,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (id == null) {
            throw new DomainException("Address id is required");
        }
        CustomerAddress address = create(
                customerId,
                label,
                recipientName,
                phone,
                line1,
                line2,
                comuna,
                city,
                region,
                reference,
                isDefault
        );
        address.id = id;
        address.createdAt = createdAt;
        address.updatedAt = updatedAt;
        return address;
    }

    public void update(
            String label,
            String recipientName,
            String phone,
            String line1,
            String line2,
            String comuna,
            String city,
            String region,
            String reference
    ) {
        this.label = normalizeRequired(label, "Address label");
        this.recipientName = normalizeRequired(recipientName, "Recipient name");
        this.phone = normalizePhone(phone);
        this.line1 = normalizeRequired(line1, "Address line1");
        this.line2 = normalizeOptional(line2);
        this.comuna = normalizeRequired(comuna, "Comuna");
        this.city = normalizeRequired(city, "City");
        this.region = normalizeRequired(region, "Region");
        this.reference = normalizeOptional(reference);
        touch();
    }

    public void markAsDefault() {
        this.isDefault = true;
        touch();
    }

    public void clearDefault() {
        this.isDefault = false;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new DomainException(label + " is required");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new DomainException("Address phone is required");
        }
        String digits = rawPhone.trim().replaceAll("\\D", "");
        if (digits.length() < 8 || digits.length() > 15) {
            throw new DomainException("Address phone must contain between 8 and 15 digits");
        }
        return "+" + digits;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getLabel() {
        return label;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getPhone() {
        return phone;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getComuna() {
        return comuna;
    }

    public String getCity() {
        return city;
    }

    public String getRegion() {
        return region;
    }

    public String getReference() {
        return reference;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

