package com.pilarestilo.discount.application.mappers;

import com.pilarestilo.discount.application.dto.DiscountDto;
import com.pilarestilo.discount.domain.model.Discount;

public class DiscountMapper {

    private DiscountMapper() {}

    public static DiscountDto toDto(Discount discount) {
        return toDto(discount, null, null);
    }

    public static DiscountDto toDto(Discount discount, String assignedUserName, String assignedUserEmail) {
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
                discount.isActive(),
                discount.getAssignedUserId(),
                assignedUserName,
                assignedUserEmail
        );
    }
}
