package com.pilarestilo.wishlist.domain.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Wishlist {

    private final UUID customerId;
    private final Set<UUID> productIds;

    private Wishlist(UUID customerId, Set<UUID> productIds) {
        this.customerId = customerId;
        this.productIds = productIds;
    }

    public static Wishlist create(UUID customerId) {
        return new Wishlist(customerId, new LinkedHashSet<>());
    }

    public static Wishlist reconstruct(UUID customerId, Set<UUID> productIds) {
        return new Wishlist(customerId, new LinkedHashSet<>(productIds));
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

    public UUID getCustomerId() { return customerId; }
    public Set<UUID> getProductIds() { return Set.copyOf(productIds); }
}
