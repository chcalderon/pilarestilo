package com.pilarestilo.wishlist.application.usecases;

import com.pilarestilo.wishlist.domain.model.Wishlist;
import com.pilarestilo.wishlist.domain.ports.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AddToWishlistUseCase {

    private final WishlistRepository wishlistRepository;

    public AddToWishlistUseCase(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    @Transactional
    public void execute(UUID customerId, UUID productId) {
        Wishlist wishlist = wishlistRepository.findByCustomerId(customerId)
                .orElse(Wishlist.create(customerId));
        wishlist.addProduct(productId);
        wishlistRepository.save(wishlist);
    }
}
