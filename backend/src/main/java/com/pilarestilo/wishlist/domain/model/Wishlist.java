package com.pilarestilo.wishlist.domain.model;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Wishlist {

    private final UUID customerId;
    private final Set<UUID> productIds;
    private UUID shareToken;
    private boolean shareEnabled;

    private Wishlist(UUID customerId, Set<UUID> productIds, UUID shareToken, boolean shareEnabled) {
        this.customerId = customerId;
        this.productIds = productIds;
        this.shareToken = shareToken;
        this.shareEnabled = shareEnabled;
    }

    public static Wishlist create(UUID customerId) {
        return new Wishlist(customerId, new LinkedHashSet<>(), null, false);
    }

    public static Wishlist reconstruct(UUID customerId, Set<UUID> productIds) {
        return new Wishlist(customerId, new LinkedHashSet<>(productIds), null, false);
    }

    public static Wishlist reconstruct(UUID customerId, Set<UUID> productIds, UUID shareToken, boolean shareEnabled) {
        return new Wishlist(customerId, new LinkedHashSet<>(productIds), shareToken, shareEnabled);
    }

    public void addProduct(UUID productId) {
        productIds.add(productId);
    }

    public void removeProduct(UUID productId) {
        productIds.remove(productId);
    }

    public boolean contains(UUID productId) {
        return productIds.contains(productId);
    }

    public void enableSharing() {
        if (shareToken == null) {
            shareToken = UUID.randomUUID();
        }
        shareEnabled = true;
    }

    public void disableSharing() {
        shareEnabled = false;
    }

    public UUID getCustomerId() { return customerId; }
    public Set<UUID> getProductIds() { return Set.copyOf(productIds); }
    public Optional<UUID> getShareToken() { return Optional.ofNullable(shareToken); }
    public boolean isShareEnabled() { return shareEnabled; }
}
