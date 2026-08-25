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
@Table(name = "categories")
public class CategoryEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(name = "name_es", nullable = false, length = 120)
    private String nameEs;

    @Column(name = "name_en", nullable = false, length = 120)
    private String nameEn;

    @Column(name = "category_type", nullable = false, length = 32)
    private String categoryType;

    @Column(name = "defines_variant_fields", nullable = false)
    private boolean definesVariantFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variant_field_config", columnDefinition = "jsonb")
    private Map<String, Object> variantFieldConfig;

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getNameEs() {
        return nameEs;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getCategoryType() {
        return categoryType;
    }

    public boolean isDefinesVariantFields() {
        return definesVariantFields;
    }

    public Map<String, Object> getVariantFieldConfig() {
        return variantFieldConfig;
    }
}
