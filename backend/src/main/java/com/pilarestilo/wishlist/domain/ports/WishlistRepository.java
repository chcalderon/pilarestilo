package com.pilarestilo.wishlist.domain.ports;

import com.pilarestilo.wishlist.domain.model.Wishlist;

import java.util.Optional;
import java.util.UUID;

public interface WishlistRepository {
    Wishlist save(Wishlist wishlist);
    Optional<Wishlist> findByCustomerId(UUID customerId);
    Optional<Wishlist> findByShareToken(UUID shareToken);
}
