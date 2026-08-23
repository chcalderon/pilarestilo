package com.pilarestilo.navigation.domain.model;

import com.pilarestilo.navigation.domain.enums.NavigationLayout;
import com.pilarestilo.shared.domain.DomainException;

import java.time.Instant;
import java.util.UUID;

/**
 * Pure domain aggregate — no JPA, no Spring, no Lombok.
 */
public class NavigationSection {

    private UUID id;
    private UUID rootCategoryId;
    private NavigationLayout layout;
    private int columnCount;
    private String bannerImageUrl;
    private String bannerTitle;
    private String bannerSubtitle;
    private String bannerLink;
    private boolean active;
    private int sortOrder;
    private Instant createdAt;
    private Instant updatedAt;

    private NavigationSection() {}

    // One parameter per column a navigation section actually carries.
    @SuppressWarnings("java:S107")
    public static NavigationSection create(UUID rootCategoryId, NavigationLayout layout, int columnCount,
                                           String bannerImageUrl, String bannerTitle, String bannerSubtitle,
                                           String bannerLink, boolean active, int sortOrder) {
        if (columnCount < 1 || columnCount > 6) {
            throw new DomainException("columnCount must be between 1 and 6");
        }
        NavigationSection s = new NavigationSection();
        s.id = UUID.randomUUID();
        s.rootCategoryId = rootCategoryId;
        s.layout = layout != null ? layout : NavigationLayout.COLUMNS;
        s.columnCount = columnCount;
        s.bannerImageUrl = bannerImageUrl;
        s.bannerTitle = bannerTitle;
        s.bannerSubtitle = bannerSubtitle;
        s.bannerLink = bannerLink;
        s.active = active;
        s.sortOrder = sortOrder;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    @SuppressWarnings("java:S107")
    public static NavigationSection reconstruct(UUID id, UUID rootCategoryId, NavigationLayout layout,
                                                int columnCount, String bannerImageUrl, String bannerTitle,
                                                String bannerSubtitle, String bannerLink, boolean active,
                                                int sortOrder, Instant createdAt, Instant updatedAt) {
        NavigationSection s = new NavigationSection();
        s.id = id;
        s.rootCategoryId = rootCategoryId;
        s.layout = layout;
        s.columnCount = columnCount;
        s.bannerImageUrl = bannerImageUrl;
        s.bannerTitle = bannerTitle;
        s.bannerSubtitle = bannerSubtitle;
        s.bannerLink = bannerLink;
        s.active = active;
        s.sortOrder = sortOrder;
        s.createdAt = createdAt;
        s.updatedAt = updatedAt;
        return s;
    }

    @SuppressWarnings("java:S107")
    public void update(UUID rootCategoryId, NavigationLayout layout, int columnCount,
                       String bannerImageUrl, String bannerTitle, String bannerSubtitle,
                       String bannerLink, boolean active, int sortOrder) {
        if (columnCount < 1 || columnCount > 6) {
            throw new DomainException("columnCount must be between 1 and 6");
        }
        this.rootCategoryId = rootCategoryId;
        this.layout = layout != null ? layout : NavigationLayout.COLUMNS;
        this.columnCount = columnCount;
        this.bannerImageUrl = bannerImageUrl;
        this.bannerTitle = bannerTitle;
        this.bannerSubtitle = bannerSubtitle;
        this.bannerLink = bannerLink;
        this.active = active;
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRootCategoryId() { return rootCategoryId; }
    public NavigationLayout getLayout() { return layout; }
    public int getColumnCount() { return columnCount; }
    public String getBannerImageUrl() { return bannerImageUrl; }
    public String getBannerTitle() { return bannerTitle; }
    public String getBannerSubtitle() { return bannerSubtitle; }
    public String getBannerLink() { return bannerLink; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
