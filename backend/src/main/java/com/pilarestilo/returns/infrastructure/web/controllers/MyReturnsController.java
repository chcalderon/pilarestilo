package com.pilarestilo.returns.infrastructure.web.controllers;

import com.pilarestilo.returns.application.dto.ReturnRequestDto;
import com.pilarestilo.returns.application.usecases.ListReturnsUseCase;
import com.pilarestilo.returns.application.usecases.RequestReturnUseCase;
import com.pilarestilo.returns.domain.enums.ReturnKind;
import com.pilarestilo.returns.infrastructure.web.requests.ReturnRequests;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The customer's door: the boton de arrepentimiento.
 *
 * <p>She never chooses the kind — what she opens is always a retracto, and the use case refuses it
 * outside the ten-day window rather than letting her open something the shop could then decline.
 * Ownership is enforced server-side: the order id in the body proves nothing on its own.
 */
@RestController
@RequestMapping("/api/me/returns")
public class MyReturnsController {

    private final RequestReturnUseCase requestReturnUseCase;
    private final ListReturnsUseCase listReturnsUseCase;

    public MyReturnsController(RequestReturnUseCase requestReturnUseCase,
                               ListReturnsUseCase listReturnsUseCase) {
        this.requestReturnUseCase = requestReturnUseCase;
        this.listReturnsUseCase = listReturnsUseCase;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ReturnRequestDto> mine(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return listReturnsUseCase.mine(currentUser.id());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReturnRequestDto> retract(
            @Valid @RequestBody ReturnRequests.OpenRetracto request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ReturnRequestDto created = requestReturnUseCase.execute(
                request.orderId(), ReturnKind.RETRACTO, request.reason(), currentUser.id(), true);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * When the window closes for an order, so the page can show the days left rather than offering a
     * button that fails on click.
     */
    @GetMapping("/window/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Instant> window(@PathVariable UUID orderId) {
        Instant closesAt = requestReturnUseCase.retractoClosesAt(orderId).orElse(null);
        return closesAt == null ? Map.of() : Map.of("closesAt", closesAt);
    }
}
