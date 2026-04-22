package com.pilarestilo.wishlist.infrastructure.web;

import com.pilarestilo.shared.auth.application.usecases.GetCurrentUserUseCase;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.wishlist.application.dto.SharedWishlistDto;
import com.pilarestilo.wishlist.application.dto.WishlistDto;
import com.pilarestilo.wishlist.application.dto.WishlistShareLinkDto;
import com.pilarestilo.wishlist.application.usecases.AddToWishlistUseCase;
import com.pilarestilo.wishlist.application.usecases.DisableWishlistShareLinkUseCase;
import com.pilarestilo.wishlist.application.usecases.EnableWishlistShareLinkUseCase;
import com.pilarestilo.wishlist.application.usecases.GetSharedWishlistUseCase;
import com.pilarestilo.wishlist.application.usecases.GetWishlistUseCase;
import com.pilarestilo.wishlist.application.usecases.GetWishlistShareLinkUseCase;
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
    private final GetWishlistShareLinkUseCase getWishlistShareLinkUseCase;
    private final EnableWishlistShareLinkUseCase enableWishlistShareLinkUseCase;
    private final DisableWishlistShareLinkUseCase disableWishlistShareLinkUseCase;
    private final GetSharedWishlistUseCase getSharedWishlistUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public WishlistController(GetWishlistUseCase getWishlistUseCase,
                               AddToWishlistUseCase addToWishlistUseCase,
                               RemoveFromWishlistUseCase removeFromWishlistUseCase,
                               GetWishlistShareLinkUseCase getWishlistShareLinkUseCase,
                               EnableWishlistShareLinkUseCase enableWishlistShareLinkUseCase,
                               DisableWishlistShareLinkUseCase disableWishlistShareLinkUseCase,
                               GetSharedWishlistUseCase getSharedWishlistUseCase,
                               GetCurrentUserUseCase getCurrentUserUseCase) {
        this.getWishlistUseCase = getWishlistUseCase;
        this.addToWishlistUseCase = addToWishlistUseCase;
        this.removeFromWishlistUseCase = removeFromWishlistUseCase;
        this.getWishlistShareLinkUseCase = getWishlistShareLinkUseCase;
        this.enableWishlistShareLinkUseCase = enableWishlistShareLinkUseCase;
        this.disableWishlistShareLinkUseCase = disableWishlistShareLinkUseCase;
        this.getSharedWishlistUseCase = getSharedWishlistUseCase;
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

    @GetMapping("/share-link")
    @PreAuthorize("isAuthenticated()")
    public WishlistShareLinkDto getShareLink() {
        return getWishlistShareLinkUseCase.execute(currentUserId());
    }

    @PostMapping("/share-link")
    @PreAuthorize("isAuthenticated()")
    public WishlistShareLinkDto enableShareLink() {
        return enableWishlistShareLinkUseCase.execute(currentUserId());
    }

    @DeleteMapping("/share-link")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> disableShareLink() {
        disableWishlistShareLinkUseCase.execute(currentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared/{token}")
    public SharedWishlistDto getShared(@PathVariable UUID token) {
        return getSharedWishlistUseCase.execute(token);
    }

    private UUID currentUserId() {
        return getCurrentUserUseCase.execute()
                .map(AuthenticatedUser::id)
                .orElseThrow(() -> new DomainException("Not authenticated"));
    }
}
