package com.pilarestilo.wishlist.infrastructure.persistence.repositories;

import com.pilarestilo.wishlist.infrastructure.persistence.entities.WishlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WishlistJpaRepository extends JpaRepository<WishlistEntity, UUID> {
    Optional<WishlistEntity> findByShareTokenAndShareEnabledTrue(UUID shareToken);
}
