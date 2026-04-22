package com.pilarestilo.wishlist.infrastructure.persistence.entities;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "wishlists")
public class WishlistEntity {

    @Id
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "wishlist_items",
            joinColumns = @JoinColumn(name = "customer_id"))
    @Column(name = "product_id")
    private Set<UUID> productIds = new LinkedHashSet<>();

    @Column(name = "share_token")
    private UUID shareToken;

    @Column(name = "share_enabled", nullable = false)
    private boolean shareEnabled = false;

    protected WishlistEntity() {}

    public WishlistEntity(UUID customerId) {
        this.customerId = customerId;
        this.createdAt = Instant.now();
    }

    public UUID getCustomerId() { return customerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<UUID> getProductIds() { return productIds; }
    public void setProductIds(Set<UUID> productIds) { this.productIds = productIds; }
    public UUID getShareToken() { return shareToken; }
    public void setShareToken(UUID shareToken) { this.shareToken = shareToken; }
    public boolean isShareEnabled() { return shareEnabled; }
    public void setShareEnabled(boolean shareEnabled) { this.shareEnabled = shareEnabled; }
}
