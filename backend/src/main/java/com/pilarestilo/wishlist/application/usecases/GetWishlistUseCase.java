package com.pilarestilo.wishlist.application.usecases;

import com.pilarestilo.wishlist.application.dto.WishlistDto;
import com.pilarestilo.wishlist.domain.model.Wishlist;
import com.pilarestilo.wishlist.domain.ports.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetWishlistUseCase {

    private final WishlistRepository wishlistRepository;

    public GetWishlistUseCase(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    @Transactional(readOnly = true)
    public WishlistDto execute(UUID customerId) {
        Wishlist wishlist = wishlistRepository.findByCustomerId(customerId)
                .orElse(Wishlist.create(customerId));
        return new WishlistDto(wishlist.getCustomerId(), wishlist.getProductIds());
    }
}
