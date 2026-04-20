package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.domain.ports.DiscountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DeleteDiscountUseCase {

    private final DiscountRepository discountRepository;

    public DeleteDiscountUseCase(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    @Transactional
    public void execute(UUID id) {
        discountRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Discount not found: " + id));
        discountRepository.deleteById(id);
    }
}
