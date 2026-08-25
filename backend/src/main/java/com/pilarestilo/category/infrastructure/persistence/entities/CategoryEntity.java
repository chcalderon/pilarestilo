package com.pilarestilo.category.infrastructure.persistence.entities;

import com.pilarestilo.category.domain.enums.CategoryType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
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

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "menu_visible", nullable = false)
    private boolean menuVisible = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", length = 24, nullable = false)
    private CategoryType categoryType = CategoryType.GENERIC;

    @Column(name = "hero_image_url", length = 500)
    private String heroImageUrl;

    @Column(name = "defines_variant_fields", nullable = false)
    private boolean definesVariantFields = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variant_field_config", columnDefinition = "jsonb")
    private Map<String, Object> variantFieldConfig;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getNameEs() { return nameEs; }
    public void setNameEs(String nameEs) { this.nameEs = nameEs; }
    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public boolean isMenuVisible() { return menuVisible; }
    public void setMenuVisible(boolean menuVisible) { this.menuVisible = menuVisible; }
    public CategoryType getCategoryType() { return categoryType; }
    public void setCategoryType(CategoryType categoryType) { this.categoryType = categoryType; }
    public String getHeroImageUrl() { return heroImageUrl; }
    public void setHeroImageUrl(String heroImageUrl) { this.heroImageUrl = heroImageUrl; }
    public boolean isDefinesVariantFields() { return definesVariantFields; }
    public void setDefinesVariantFields(boolean definesVariantFields) { this.definesVariantFields = definesVariantFields; }
    public Map<String, Object> getVariantFieldConfig() { return variantFieldConfig; }
    public void setVariantFieldConfig(Map<String, Object> variantFieldConfig) { this.variantFieldConfig = variantFieldConfig; }
}
