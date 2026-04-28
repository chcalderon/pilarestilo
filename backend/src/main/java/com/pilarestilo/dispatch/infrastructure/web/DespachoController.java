package com.pilarestilo.dispatch.infrastructure.web;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.application.usecases.*;
import com.pilarestilo.dispatch.infrastructure.web.requests.MarkDispatchedRequest;
import com.pilarestilo.dispatch.infrastructure.web.requests.MarkFailedRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/despachos")
public class DespachoController {

    private final ListDispatchesUseCase listUseCase;
    private final ClaimDispatchUseCase claimUseCase;
    private final UnclaimDispatchUseCase unclaimUseCase;
    private final MarkDispatchedUseCase markDispatchedUseCase;
    private final MarkDeliveredUseCase markDeliveredUseCase;
    private final MarkFailedUseCase markFailedUseCase;

    public DespachoController(ListDispatchesUseCase listUseCase,
                               ClaimDispatchUseCase claimUseCase,
                               UnclaimDispatchUseCase unclaimUseCase,
                               MarkDispatchedUseCase markDispatchedUseCase,
                               MarkDeliveredUseCase markDeliveredUseCase,
                               MarkFailedUseCase markFailedUseCase) {
        this.listUseCase = listUseCase;
        this.claimUseCase = claimUseCase;
        this.unclaimUseCase = unclaimUseCase;
        this.markDispatchedUseCase = markDispatchedUseCase;
        this.markDeliveredUseCase = markDeliveredUseCase;
        this.markFailedUseCase = markFailedUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public List<DispatchDto> list(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return listUseCase.executeForDispatcher(currentUser.id());
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public DispatchDto claim(@PathVariable UUID id,
                              @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return claimUseCase.execute(id, currentUser.id());
    }

    @PostMapping("/{id}/unclaim")
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public DispatchDto unclaim(@PathVariable UUID id,
                                @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return unclaimUseCase.execute(id, currentUser.id());
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public DispatchDto dispatch(@PathVariable UUID id,
                                 @RequestBody @Valid MarkDispatchedRequest req,
                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return markDispatchedUseCase.execute(id, currentUser.id(),
                req.carrier(), req.trackingCode(), req.scheduledDate(), req.notes());
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public DispatchDto deliver(@PathVariable UUID id) {
        return markDeliveredUseCase.execute(id);
    }

    @PostMapping("/{id}/fail")
    @PreAuthorize("hasAnyRole('DESPACHADOR', 'ADMIN')")
    public DispatchDto fail(@PathVariable UUID id, @RequestBody MarkFailedRequest req) {
        return markFailedUseCase.execute(id, req.notes());
    }
}
