package com.pilarestilo.category.domain.model;

import com.pilarestilo.category.domain.enums.CategoryType;
import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public class Category {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private UUID id;
    private String slug;
    private String nameEs;
    private String nameEn;
    private UUID parentId;
    private int sortOrder;
    private boolean active;
    private boolean featured;
    private String imageUrl;
    private Instant createdAt;
    private boolean menuVisible = true;
    private CategoryType categoryType = CategoryType.GENERIC;
    private String heroImageUrl;

    private Category() {}

    public static Category create(String slug, String nameEs, String nameEn,
                                  UUID parentId, int sortOrder, String imageUrl) {
        validate(slug, nameEs, nameEn);
        Category c = new Category();
        c.id = UUID.randomUUID();
        c.slug = slug.trim().toLowerCase();
        c.nameEs = nameEs.trim();
        c.nameEn = nameEn.trim();
        c.parentId = parentId;
        c.sortOrder = sortOrder;
        c.active = true;
        c.imageUrl = imageUrl;
        c.createdAt = Instant.now();
        c.menuVisible = true;
        c.categoryType = CategoryType.GENERIC;
        return c;
    }

    public void update(String slug, String nameEs, String nameEn,
                       UUID parentId, int sortOrder, boolean active, boolean featured, String imageUrl) {
        validate(slug, nameEs, nameEn);
        this.slug = slug.trim().toLowerCase();
        this.nameEs = nameEs.trim();
        this.nameEn = nameEn.trim();
        this.parentId = parentId;
        this.sortOrder = sortOrder;
        this.active = active;
        this.featured = featured;
        this.imageUrl = imageUrl;
    }

    public void updateMenuMetadata(boolean menuVisible, CategoryType categoryType, String heroImageUrl) {
        this.menuVisible = menuVisible;
        this.categoryType = categoryType != null ? categoryType : CategoryType.GENERIC;
        this.heroImageUrl = heroImageUrl;
    }

    private static void validate(String slug, String nameEs, String nameEn) {
        if (slug == null || slug.isBlank() || !SLUG_PATTERN.matcher(slug.trim().toLowerCase()).matches()) {
            throw new DomainException("Category slug must be lowercase, hyphen-separated url-safe text");
        }
        if (nameEs == null || nameEs.isBlank()) throw new DomainException("Category nameEs is required");
        if (nameEn == null || nameEn.isBlank()) throw new DomainException("Category nameEn is required");
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getNameEs() { return nameEs; }
    public String getNameEn() { return nameEn; }
    public UUID getParentId() { return parentId; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public boolean isFeatured() { return featured; }
    public String getImageUrl() { return imageUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isMenuVisible() { return menuVisible; }
    public CategoryType getCategoryType() { return categoryType; }
    public String getHeroImageUrl() { return heroImageUrl; }

    public void reorder(int sortOrder) { this.sortOrder = sortOrder; }

    // setters for persistence rehydration
    public void setId(UUID id) { this.id = id; }
    public void setActive(boolean active) { this.active = active; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setMenuVisible(boolean menuVisible) { this.menuVisible = menuVisible; }
    public void setCategoryType(CategoryType categoryType) { this.categoryType = categoryType; }
    public void setHeroImageUrl(String heroImageUrl) { this.heroImageUrl = heroImageUrl; }
}
