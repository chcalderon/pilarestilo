package com.pilarestilo.discount.application.mappers;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.domain.model.Discount;

public class DiscountMapper {

    private DiscountMapper() {}

    public static DiscountDto toDto(Discount discount) {
        return new DiscountDto(
                discount.getId(),
                discount.getCode(),
                discount.getType().name(),
                discount.getValue(),
                discount.getMinOrderAmount().amount(),
                discount.getMinOrderAmount().currency(),
                discount.getValidFrom(),
                discount.getValidUntil(),
                discount.getMaxUses(),
                discount.getTimesUsed(),
                discount.isActive()
        );
    }
}
