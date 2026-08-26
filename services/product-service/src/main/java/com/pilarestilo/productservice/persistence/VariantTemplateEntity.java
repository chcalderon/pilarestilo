package com.pilarestilo.productservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "variant_templates")
public class VariantTemplateEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_config", columnDefinition = "jsonb")
    private Map<String, Object> fieldConfig;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getFieldConfig() {
        return fieldConfig;
    }
}
