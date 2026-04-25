package com.pilarestilo.discount.infrastructure.web.controllers;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.application.usecases.*;
import com.pilarestilo.discount.infrastructure.web.requests.CreateDiscountRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/discounts")
public class DiscountController {

    private final CreateDiscountUseCase createDiscountUseCase;
    private final GetDiscountUseCase getDiscountUseCase;
    private final ListDiscountsUseCase listDiscountsUseCase;
    private final ValidateDiscountUseCase validateDiscountUseCase;
    private final ValidateDiscountForUserUseCase validateForUserUseCase;
    private final DeleteDiscountUseCase deleteDiscountUseCase;
    private final SuggestDiscountCodeUseCase suggestDiscountCodeUseCase;

    public DiscountController(CreateDiscountUseCase createDiscountUseCase,
                               GetDiscountUseCase getDiscountUseCase,
                               ListDiscountsUseCase listDiscountsUseCase,
                               ValidateDiscountUseCase validateDiscountUseCase,
                               ValidateDiscountForUserUseCase validateForUserUseCase,
                               DeleteDiscountUseCase deleteDiscountUseCase,
                               SuggestDiscountCodeUseCase suggestDiscountCodeUseCase) {
        this.createDiscountUseCase = createDiscountUseCase;
        this.getDiscountUseCase = getDiscountUseCase;
        this.listDiscountsUseCase = listDiscountsUseCase;
        this.validateDiscountUseCase = validateDiscountUseCase;
        this.validateForUserUseCase = validateForUserUseCase;
        this.deleteDiscountUseCase = deleteDiscountUseCase;
        this.suggestDiscountCodeUseCase = suggestDiscountCodeUseCase;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<DiscountDto> create(@Valid @RequestBody CreateDiscountRequest request) {
        DiscountDto dto = createDiscountUseCase.execute(
                request.code(), request.type(), request.value(),
                request.minOrderAmount(), request.validFrom(), request.validUntil(), request.maxUses()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public DiscountDto getById(@PathVariable UUID id) {
        return getDiscountUseCase.execute(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public Object list(@RequestParam(required = false) String status, Pageable pageable) {
        if (status != null) {
            List<DiscountDto> filtered = listDiscountsUseCase.executeByStatus(status);
            return filtered;
        }
        return listDiscountsUseCase.execute(pageable);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteDiscountUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/suggest-code")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public Map<String, String> suggestCode() {
        return Map.of("code", suggestDiscountCodeUseCase.execute());
    }

    /** Validates a discount code for the authenticated user + subtotal. Returns discount details or error. */
    @GetMapping("/validate-for-user")
    @PreAuthorize("isAuthenticated()")
    public DiscountDto validateForUser(
            @RequestParam String code,
            @RequestParam BigDecimal subtotal,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return validateForUserUseCase.execute(code, subtotal, currentUser.id());
    }

    /** Admin preview — no per-user check, returns discount amount. */
    @GetMapping("/validate")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public BigDecimal validate(@RequestParam String code, @RequestParam BigDecimal subtotal) {
        return validateDiscountUseCase.execute(code, subtotal).amount();
    }
}
