package com.pilarestilo.customercredit.infrastructure.web.controllers;

import com.pilarestilo.customercredit.application.dto.CreditMovementDto;
import com.pilarestilo.customercredit.application.dto.CustomerCreditDto;
import com.pilarestilo.customercredit.application.usecases.GetCustomerCreditUseCase;
import com.pilarestilo.customercredit.application.usecases.GrantCreditUseCase;
import com.pilarestilo.customercredit.application.usecases.ListMovementsUseCase;
import com.pilarestilo.customercredit.application.usecases.UseCreditUseCase;
import com.pilarestilo.customercredit.infrastructure.web.requests.GrantCreditRequest;
import com.pilarestilo.customercredit.infrastructure.web.requests.UseCreditRequest;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.auth.domain.AuthenticatedUser;
import com.pilarestilo.user.domain.enums.UserRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers/{customerId}/credit")
public class CustomerCreditController {

    private final GrantCreditUseCase grantCreditUseCase;
    private final UseCreditUseCase useCreditUseCase;
    private final GetCustomerCreditUseCase getCustomerCreditUseCase;
    private final ListMovementsUseCase listMovementsUseCase;

    public CustomerCreditController(GrantCreditUseCase grantCreditUseCase,
                                     UseCreditUseCase useCreditUseCase,
                                     GetCustomerCreditUseCase getCustomerCreditUseCase,
                                     ListMovementsUseCase listMovementsUseCase) {
        this.grantCreditUseCase = grantCreditUseCase;
        this.useCreditUseCase = useCreditUseCase;
        this.getCustomerCreditUseCase = getCustomerCreditUseCase;
        this.listMovementsUseCase = listMovementsUseCase;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public CustomerCreditDto getBalance(@PathVariable UUID customerId,
                                        @AuthenticationPrincipal AuthenticatedUser currentUser) {
        guardCustomerAccess(customerId, currentUser);
        return getCustomerCreditUseCase.execute(customerId);
    }

    @PostMapping("/grant")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public CustomerCreditDto grant(@PathVariable UUID customerId,
                                    @Valid @RequestBody GrantCreditRequest request) {
        return grantCreditUseCase.execute(customerId, Money.of(request.amount()), request.reason());
    }

    @PostMapping("/use")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public CustomerCreditDto use(@PathVariable UUID customerId,
                                  @Valid @RequestBody UseCreditRequest request) {
        return useCreditUseCase.execute(customerId, Money.of(request.amount()), request.reason());
    }

    @GetMapping("/movements")
    @PreAuthorize("isAuthenticated()")
    public Page<CreditMovementDto> movements(@PathVariable UUID customerId,
                                             Pageable pageable,
                                             @AuthenticationPrincipal AuthenticatedUser currentUser) {
        guardCustomerAccess(customerId, currentUser);
        return listMovementsUseCase.execute(customerId, pageable);
    }

    private void guardCustomerAccess(UUID customerId, AuthenticatedUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        if (currentUser.role() == UserRole.CUSTOMER && !currentUser.id().equals(customerId)) {
            throw new AccessDeniedException("You can only access your own credit data");
        }
    }
}
