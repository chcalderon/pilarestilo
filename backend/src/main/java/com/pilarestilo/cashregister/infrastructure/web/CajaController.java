package com.pilarestilo.cashregister.infrastructure.web;

import com.pilarestilo.cashregister.application.dto.CashMovementDto;
import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.application.usecases.*;
import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.infrastructure.web.requests.AddMovementRequest;
import com.pilarestilo.cashregister.infrastructure.web.requests.CloseCashRegisterRequest;
import com.pilarestilo.cashregister.infrastructure.web.requests.OpenCashRegisterRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final OpenCashRegisterUseCase openUseCase;
    private final CloseCashRegisterUseCase closeUseCase;
    private final GetCurrentCashRegisterUseCase getCurrentUseCase;
    private final AddCashMovementUseCase addMovementUseCase;

    public CajaController(OpenCashRegisterUseCase openUseCase,
                           CloseCashRegisterUseCase closeUseCase,
                           GetCurrentCashRegisterUseCase getCurrentUseCase,
                           AddCashMovementUseCase addMovementUseCase) {
        this.openUseCase = openUseCase;
        this.closeUseCase = closeUseCase;
        this.getCurrentUseCase = getCurrentUseCase;
        this.addMovementUseCase = addMovementUseCase;
    }

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public CashRegisterDto open(@RequestBody @Valid OpenCashRegisterRequest req,
                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return openUseCase.execute(currentUser.id(), req.openingBalance());
    }

    @PostMapping("/close")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public CashRegisterDto close(@RequestBody @Valid CloseCashRegisterRequest req,
                                  @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return closeUseCase.execute(currentUser.id(), req.closingBalance(), req.notes());
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public CashRegisterDto current(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return getCurrentUseCase.execute(currentUser.id());
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public CashMovementDto addMovement(@RequestBody @Valid AddMovementRequest req,
                                        @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return addMovementUseCase.execute(currentUser.id(),
                CashMovementType.valueOf(req.type()), req.amount(), req.description());
    }
}
