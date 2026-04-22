package com.pilarestilo.wishlist.application.usecases;

import com.pilarestilo.wishlist.domain.ports.WishlistRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DisableWishlistShareLinkUseCase {

    private final WishlistRepository wishlistRepository;

    public DisableWishlistShareLinkUseCase(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    public void execute(UUID customerId) {
        wishlistRepository.findByCustomerId(customerId).ifPresent(wishlist -> {
            wishlist.disableSharing();
            wishlistRepository.save(wishlist);
        });
    }
}
