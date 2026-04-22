package com.pilarestilo.wishlist.application.usecases;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.wishlist.application.dto.SharedWishlistDto;
import com.pilarestilo.wishlist.domain.model.Wishlist;
import com.pilarestilo.wishlist.domain.ports.WishlistRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetSharedWishlistUseCase {

    private final WishlistRepository wishlistRepository;

    public GetSharedWishlistUseCase(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    public SharedWishlistDto execute(UUID shareToken) {
        Wishlist wishlist = wishlistRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new DomainException("Shared wishlist not found"));
        return new SharedWishlistDto(
                shareToken,
                wishlist.getProductIds().stream().toList()
        );
    }
}
