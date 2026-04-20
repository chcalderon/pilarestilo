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
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public CustomerCreditDto getBalance(@PathVariable UUID customerId) {
        return getCustomerCreditUseCase.execute(customerId);
    }

    @PostMapping("/grant")
    public CustomerCreditDto grant(@PathVariable UUID customerId,
                                    @Valid @RequestBody GrantCreditRequest request) {
        return grantCreditUseCase.execute(customerId, Money.of(request.amount()), request.reason());
    }

    @PostMapping("/use")
    public CustomerCreditDto use(@PathVariable UUID customerId,
                                  @Valid @RequestBody UseCreditRequest request) {
        return useCreditUseCase.execute(customerId, Money.of(request.amount()), request.reason());
    }

    @GetMapping("/movements")
    public Page<CreditMovementDto> movements(@PathVariable UUID customerId, Pageable pageable) {
        return listMovementsUseCase.execute(customerId, pageable);
    }
}
