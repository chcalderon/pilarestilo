package com.pilarestilo.wishlist.infrastructure.web;

import com.pilarestilo.shared.auth.application.usecases.GetCurrentUserUseCase;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.wishlist.application.dto.WishlistDto;
import com.pilarestilo.wishlist.application.usecases.AddToWishlistUseCase;
import com.pilarestilo.wishlist.application.usecases.GetWishlistUseCase;
import com.pilarestilo.wishlist.application.usecases.RemoveFromWishlistUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final GetWishlistUseCase getWishlistUseCase;
    private final AddToWishlistUseCase addToWishlistUseCase;
    private final RemoveFromWishlistUseCase removeFromWishlistUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public WishlistController(GetWishlistUseCase getWishlistUseCase,
                               AddToWishlistUseCase addToWishlistUseCase,
                               RemoveFromWishlistUseCase removeFromWishlistUseCase,
                               GetCurrentUserUseCase getCurrentUserUseCase) {
        this.getWishlistUseCase = getWishlistUseCase;
        this.addToWishlistUseCase = addToWishlistUseCase;
        this.removeFromWishlistUseCase = removeFromWishlistUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public WishlistDto get() {
        return getWishlistUseCase.execute(currentUserId());
    }

    @PostMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> add(@PathVariable UUID productId) {
        addToWishlistUseCase.execute(currentUserId(), productId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> remove(@PathVariable UUID productId) {
        removeFromWishlistUseCase.execute(currentUserId(), productId);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() {
        return getCurrentUserUseCase.execute()
                .map(AuthenticatedUser::id)
                .orElseThrow(() -> new DomainException("Not authenticated"));
    }
}
