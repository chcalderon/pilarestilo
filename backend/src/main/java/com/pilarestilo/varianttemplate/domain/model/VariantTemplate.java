package com.pilarestilo.varianttemplate.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;

import java.time.Instant;
import java.util.UUID;

public class VariantTemplate {

    private UUID id;
    private String name;
    private VariantFieldConfig config;
    private Instant createdAt;

    private VariantTemplate() {}

    public static VariantTemplate create(String name, VariantFieldConfig config) {
        validate(name, config);
        VariantTemplate t = new VariantTemplate();
        t.id = UUID.randomUUID();
        t.name = name.trim();
        t.config = config;
        t.createdAt = Instant.now();
        return t;
    }

    public void update(String name, VariantFieldConfig config) {
        validate(name, config);
        this.name = name.trim();
        this.config = config;
    }

    private static void validate(String name, VariantFieldConfig config) {
        if (name == null || name.isBlank()) {
            throw new DomainException("Variant template name cannot be blank");
        }
        if (config == null) {
            throw new DomainException("Variant template config cannot be null");
        }
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public VariantFieldConfig getConfig() { return config; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
