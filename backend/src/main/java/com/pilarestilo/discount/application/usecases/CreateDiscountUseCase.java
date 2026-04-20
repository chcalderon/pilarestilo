package com.pilarestilo.discount.application.usecases;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.application.mappers.DiscountMapper;
import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class CreateDiscountUseCase {

    private final DiscountRepository discountRepository;

    public CreateDiscountUseCase(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    @Transactional
    public DiscountDto execute(String code, String type, BigDecimal value,
                                BigDecimal minOrderAmount, LocalDate validFrom,
                                LocalDate validUntil, int maxUses) {
        if (discountRepository.findByCode(code.toUpperCase()).isPresent()) {
            throw new DomainException("Discount code already exists: " + code);
        }
        DiscountType discountType = DiscountType.valueOf(type);
        Money minAmount = Money.of(minOrderAmount != null ? minOrderAmount : BigDecimal.ZERO);

        Discount discount = Discount.create(code, discountType, value, minAmount, validFrom, validUntil, maxUses);
        return DiscountMapper.toDto(discountRepository.save(discount));
    }
}
