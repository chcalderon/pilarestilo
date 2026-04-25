package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.domain.ports.DiscountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuggestDiscountCodeUseCase {

    private static final String PREFIX = "PILAR_PE_ESTILO_";

    private final DiscountRepository discountRepository;

    public SuggestDiscountCodeUseCase(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    @Transactional(readOnly = true)
    public String execute() {
        long count = discountRepository.countByCodePattern(PREFIX + "%");
        long next = count + 1;
        return PREFIX + String.format("%06d", next);
    }
}
