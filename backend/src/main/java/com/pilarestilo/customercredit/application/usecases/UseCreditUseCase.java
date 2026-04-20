package com.pilarestilo.customercredit.application.usecases;

import com.pilarestilo.customercredit.application.dto.CustomerCreditDto;
import com.pilarestilo.customercredit.domain.enums.CreditMovementType;
import com.pilarestilo.customercredit.domain.events.StoreCreditUsed;
import com.pilarestilo.customercredit.domain.model.CreditMovement;
import com.pilarestilo.customercredit.domain.model.CustomerCredit;
import com.pilarestilo.customercredit.domain.ports.CreditMovementRepository;
import com.pilarestilo.customercredit.domain.ports.CustomerCreditRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UseCreditUseCase {

    private final CustomerCreditRepository customerCreditRepository;
    private final CreditMovementRepository creditMovementRepository;
    private final DomainEventPublisher eventPublisher;

    public UseCreditUseCase(CustomerCreditRepository customerCreditRepository,
                             CreditMovementRepository creditMovementRepository,
                             DomainEventPublisher eventPublisher) {
        this.customerCreditRepository = customerCreditRepository;
        this.creditMovementRepository = creditMovementRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CustomerCreditDto execute(UUID customerId, Money amount, String reason) {
        CustomerCredit credit = customerCreditRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new NoSuchElementException("No store credit found for customer: " + customerId));

        credit.use(amount);
        CustomerCredit saved = customerCreditRepository.save(credit);

        creditMovementRepository.save(
                CreditMovement.of(customerId, CreditMovementType.CREDIT_USED, amount, reason)
        );
        eventPublisher.publish(new StoreCreditUsed(customerId, amount, Instant.now()));

        return GrantCreditUseCase.toDto(saved);
    }
}
