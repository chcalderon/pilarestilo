package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.application.mappers.DiscountMapper;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ValidateDiscountForUserUseCase {

    private final DiscountRepository discountRepository;

    public ValidateDiscountForUserUseCase(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    @Transactional(readOnly = true)
    public DiscountDto execute(String code, BigDecimal subtotalAmount, UUID userId) {
        Discount discount = discountRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new DomainException("Código de descuento no encontrado"));

        discount.validate(Money.of(subtotalAmount));

        if (discount.getAssignedUserId() != null && !discount.getAssignedUserId().equals(userId)) {
            throw new DomainException("Este código no está disponible para tu cuenta");
        }

        if (discountRepository.hasUserUsedDiscount(discount.getId(), userId)) {
            throw new DomainException("Ya usaste este código de descuento");
        }

        return DiscountMapper.toDto(discount);
    }
}
