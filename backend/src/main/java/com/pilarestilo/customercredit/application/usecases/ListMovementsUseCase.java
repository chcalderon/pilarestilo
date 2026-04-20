package com.pilarestilo.customercredit.application.usecases;

import com.pilarestilo.customercredit.application.dto.CreditMovementDto;
import com.pilarestilo.customercredit.domain.ports.CreditMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ListMovementsUseCase {

    private final CreditMovementRepository creditMovementRepository;

    public ListMovementsUseCase(CreditMovementRepository creditMovementRepository) {
        this.creditMovementRepository = creditMovementRepository;
    }

    @Transactional(readOnly = true)
    public Page<CreditMovementDto> execute(UUID customerId, Pageable pageable) {
        return creditMovementRepository.findByCustomerId(customerId, pageable)
                .map(m -> new CreditMovementDto(
                        m.getId(), m.getCustomerId(), m.getType(),
                        m.getAmount().amount(), m.getAmount().currency(),
                        m.getReason(), m.getCreatedAt()
                ));
    }
}
