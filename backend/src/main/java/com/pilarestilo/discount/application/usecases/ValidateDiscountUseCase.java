package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ValidateDiscountUseCase {

    private final DiscountRepository discountRepository;

    public ValidateDiscountUseCase(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    @Transactional(readOnly = true)
    public Money execute(String code, BigDecimal subtotalAmount) {
        Discount discount = discountRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new DomainException("Discount code not found: " + code));
        Money subtotal = Money.of(subtotalAmount);
        discount.validate(subtotal);
        // Return preview of discount amount without consuming
        if (discount.getType().name().equals("PERCENTAGE")) {
            return Money.of(subtotal.amount()
                    .multiply(discount.getValue())
                    .divide(BigDecimal.valueOf(100)));
        } else {
            return Money.of(discount.getValue().min(subtotal.amount()));
        }
    }
}
