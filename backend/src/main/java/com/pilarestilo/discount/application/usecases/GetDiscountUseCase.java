package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.application.mappers.DiscountMapper;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetDiscountUseCase {

    private final DiscountRepository discountRepository;

    public GetDiscountUseCase(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    @Transactional(readOnly = true)
    public DiscountDto execute(UUID id) {
        return discountRepository.findById(id)
                .map(DiscountMapper::toDto)
                .orElseThrow(() -> new NoSuchElementException("Discount not found: " + id));
    }
}
