package com.pilarestilo.orderservice.web;

import com.pilarestilo.orderservice.persistence.OrderEntity;
import com.pilarestilo.orderservice.persistence.OrderItemEntity;
import com.pilarestilo.orderservice.web.dto.MoneyDto;
import com.pilarestilo.orderservice.web.dto.OrderDto;
import com.pilarestilo.orderservice.web.dto.OrderItemDto;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderDto toDto(OrderEntity entity) {
        return new OrderDto(
                entity.getId(),
                entity.getCustomerId(),
                entity.getItems().stream().map(OrderMapper::toItemDto).toList(),
                new MoneyDto(entity.getSubtotalAmount(), entity.getSubtotalCurrency()),
                new MoneyDto(entity.getDiscountAmount(), entity.getDiscountCurrency()),
                new MoneyDto(entity.getTotalAmount(), entity.getTotalCurrency()),
                entity.getPaymentMethod(),
                entity.getShippingZoneCode(),
                entity.getShippingCourierId(),
                entity.getShippingCourierName(),
                entity.getShippingPaymentMode(),
                entity.getShippingAddressReference(),
                entity.getNotes(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static OrderItemDto toItemDto(OrderItemEntity entity) {
        return new OrderItemDto(
                entity.getId(),
                entity.getProductId(),
                entity.getProductName(),
                new MoneyDto(entity.getUnitPriceAmount(), entity.getUnitPriceCurrency()),
                entity.getQuantity()
        );
    }
}
