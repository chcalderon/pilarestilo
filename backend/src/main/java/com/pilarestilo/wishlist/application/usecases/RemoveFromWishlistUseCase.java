package com.pilarestilo.wishlist.application.usecases;

import com.pilarestilo.wishlist.domain.model.Wishlist;
import com.pilarestilo.wishlist.domain.ports.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RemoveFromWishlistUseCase {

    private final WishlistRepository wishlistRepository;

    public RemoveFromWishlistUseCase(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    @Transactional
    public void execute(UUID customerId, UUID productId) {
        wishlistRepository.findByCustomerId(customerId).ifPresent(wishlist -> {
            wishlist.removeProduct(productId);
            wishlistRepository.save(wishlist);
        });
    }
}
