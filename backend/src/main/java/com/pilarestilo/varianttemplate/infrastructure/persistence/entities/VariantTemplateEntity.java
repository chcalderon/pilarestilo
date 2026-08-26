package com.pilarestilo.varianttemplate.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "variant_templates")
public class VariantTemplateEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at")
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_config", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> fieldConfig;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Map<String, Object> getFieldConfig() { return fieldConfig; }
    public void setFieldConfig(Map<String, Object> fieldConfig) { this.fieldConfig = fieldConfig; }
}
