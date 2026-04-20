package com.pilarestilo.customercredit.application.usecases;

import com.pilarestilo.customercredit.application.dto.CustomerCreditDto;
import com.pilarestilo.customercredit.domain.model.CustomerCredit;
import com.pilarestilo.customercredit.domain.ports.CustomerCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetCustomerCreditUseCase {

    private final CustomerCreditRepository customerCreditRepository;

    public GetCustomerCreditUseCase(CustomerCreditRepository customerCreditRepository) {
        this.customerCreditRepository = customerCreditRepository;
    }

    @Transactional(readOnly = true)
    public CustomerCreditDto execute(UUID customerId) {
        CustomerCredit credit = customerCreditRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("No store credit found for customer: " + customerId));
        return GrantCreditUseCase.toDto(credit);
    }
}
