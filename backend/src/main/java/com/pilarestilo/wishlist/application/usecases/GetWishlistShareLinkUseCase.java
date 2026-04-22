package com.pilarestilo.wishlist.application.usecases;

import com.pilarestilo.wishlist.application.dto.WishlistShareLinkDto;
import com.pilarestilo.wishlist.domain.ports.WishlistRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetWishlistShareLinkUseCase {

    private final WishlistRepository wishlistRepository;

    public GetWishlistShareLinkUseCase(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    public WishlistShareLinkDto execute(UUID customerId) {
        return wishlistRepository.findByCustomerId(customerId)
                .map(wishlist -> new WishlistShareLinkDto(wishlist.getShareToken().orElse(null), wishlist.isShareEnabled()))
                .orElse(new WishlistShareLinkDto(null, false));
    }
}
