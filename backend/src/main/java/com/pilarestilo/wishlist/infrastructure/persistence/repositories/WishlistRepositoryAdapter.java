package com.pilarestilo.wishlist.infrastructure.persistence.repositories;

import com.pilarestilo.wishlist.domain.model.Wishlist;
import com.pilarestilo.wishlist.domain.ports.WishlistRepository;
import com.pilarestilo.wishlist.infrastructure.persistence.entities.WishlistEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class WishlistRepositoryAdapter implements WishlistRepository {

    private final WishlistJpaRepository jpa;

    public WishlistRepositoryAdapter(WishlistJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Wishlist save(Wishlist wishlist) {
        WishlistEntity entity = jpa.findById(wishlist.getCustomerId())
                .orElse(new WishlistEntity(wishlist.getCustomerId()));
        entity.setProductIds(new java.util.LinkedHashSet<>(wishlist.getProductIds()));
        jpa.save(entity);
        return wishlist;
    }

    @Override
    public Optional<Wishlist> findByCustomerId(UUID customerId) {
        return jpa.findById(customerId)
                .map(e -> Wishlist.reconstruct(e.getCustomerId(), e.getProductIds()));
    }
}
