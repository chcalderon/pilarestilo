package com.pilarestilo.cashregister.infrastructure.web;

import com.pilarestilo.cashregister.application.dto.CashMovementDto;
import com.pilarestilo.cashregister.application.dto.CashRegisterDto;
import com.pilarestilo.cashregister.application.usecases.*;
import com.pilarestilo.cashregister.domain.enums.CashMovementCategory;
import com.pilarestilo.cashregister.domain.enums.CashMovementType;
import com.pilarestilo.cashregister.domain.enums.CashRegisterStatus;
import com.pilarestilo.cashregister.infrastructure.web.requests.AddMovementRequest;
import com.pilarestilo.cashregister.infrastructure.web.requests.CloseCashRegisterRequest;
import com.pilarestilo.cashregister.infrastructure.web.requests.OpenCashRegisterRequest;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/caja")
public class CajaController {

    private final OpenCashRegisterUseCase openUseCase;
    private final CloseCashRegisterUseCase closeUseCase;
    private final GetCurrentCashRegisterUseCase getCurrentUseCase;
    private final AddCashMovementUseCase addMovementUseCase;
    private final ListSellerCashRegisterHistoryUseCase historyUseCase;

    public CajaController(OpenCashRegisterUseCase openUseCase,
                           CloseCashRegisterUseCase closeUseCase,
                           GetCurrentCashRegisterUseCase getCurrentUseCase,
                           AddCashMovementUseCase addMovementUseCase,
                           ListSellerCashRegisterHistoryUseCase historyUseCase) {
        this.openUseCase = openUseCase;
        this.closeUseCase = closeUseCase;
        this.getCurrentUseCase = getCurrentUseCase;
        this.addMovementUseCase = addMovementUseCase;
        this.historyUseCase = historyUseCase;
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

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public Page<CashRegisterDto> history(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                         @RequestParam(required = false) CashRegisterStatus status,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         Pageable pageable) {
        return historyUseCase.execute(currentUser.id(), status, from, to, pageable);
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public CashMovementDto addMovement(@RequestBody @Valid AddMovementRequest req,
                                        @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return addMovementUseCase.execute(currentUser.id(),
                CashMovementType.valueOf(req.type()),
                CashMovementCategory.valueOf(req.category()),
                req.amount(), req.description());
    }
}
