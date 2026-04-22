package com.pilarestilo.wishlist.application.usecases;

import com.pilarestilo.wishlist.application.dto.WishlistShareLinkDto;
import com.pilarestilo.wishlist.domain.model.Wishlist;
import com.pilarestilo.wishlist.domain.ports.WishlistRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EnableWishlistShareLinkUseCase {

    private final WishlistRepository wishlistRepository;

    public EnableWishlistShareLinkUseCase(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    public WishlistShareLinkDto execute(UUID customerId) {
        Wishlist wishlist = wishlistRepository.findByCustomerId(customerId)
                .orElse(Wishlist.create(customerId));
        wishlist.enableSharing();
        wishlistRepository.save(wishlist);
        return new WishlistShareLinkDto(wishlist.getShareToken().orElse(null), wishlist.isShareEnabled());
    }
}
