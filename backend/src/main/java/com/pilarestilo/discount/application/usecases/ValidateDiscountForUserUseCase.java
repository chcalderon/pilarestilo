package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.application.mappers.DiscountMapper;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.application.DiscountRedemptionService;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ValidateDiscountForUserUseCase {

    private final DiscountRedemptionService redemptionService;

    public ValidateDiscountForUserUseCase(DiscountRedemptionService redemptionService) {
        this.redemptionService = redemptionService;
    }

    @Transactional(readOnly = true)
    public DiscountDto execute(String code, BigDecimal subtotalAmount, UUID userId) {
        // Guards live in DiscountRedemptionService so this endpoint and order creation cannot
        // drift apart -- they did, and order creation was missing the ownership and prior-use
        // checks entirely.
        return DiscountMapper.toDto(
                redemptionService.evaluate(code, Money.of(subtotalAmount), userId).discount());
    }
}
