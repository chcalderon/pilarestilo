package com.pilarestilo.navigation.infrastructure.persistence.entities;

import com.pilarestilo.navigation.domain.enums.NavigationLayout;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "navigation_sections")
@Getter
@Setter
public class NavigationSectionEntity {

    @Id
    private UUID id;

    @Column(name = "root_category_id", nullable = false)
    private UUID rootCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private NavigationLayout layout;

    @Column(name = "column_count", nullable = false)
    private int columnCount;

    @Column(name = "banner_image_url", length = 500)
    private String bannerImageUrl;

    @Column(name = "banner_title", length = 120)
    private String bannerTitle;

    @Column(name = "banner_subtitle", length = 240)
    private String bannerSubtitle;

    @Column(name = "banner_link", length = 500)
    private String bannerLink;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
