package com.pilarestilo.order.application.mappers;

import com.pilarestilo.order.application.dto.MoneyDto;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.dto.OrderItemDto;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.shared.application.Money;

import java.util.List;

public class OrderMapper {

    private OrderMapper() {}

    public static OrderDto toDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(OrderMapper::toItemDto)
                .toList();
        return new OrderDto(
                order.getId(),
                order.getPublicReference(),
                order.getCustomerId(),
                itemDtos,
                toMoneyDto(order.getSubtotal()),
                toMoneyDto(order.getDiscountAmount()),
                toMoneyDto(order.getTotalAmount()),
                toMoneyDto(order.getNetAmount()),
                toMoneyDto(order.getTaxAmount()),
                order.getTaxRate(),
                order.getPaymentMethod(),
                order.getShippingZoneCode(),
                order.getShippingCourierId(),
                order.getShippingCourierName(),
                order.getShippingPaymentMode(),
                order.getShippingAddressId(),
                order.getShippingAddressReference(),
                order.getNotes(),
                order.getSalesChannel(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public static OrderItemDto toItemDto(OrderItem item) {
        return new OrderItemDto(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                toMoneyDto(item.getUnitPrice()),
                item.getQuantity(),
                item.getVariantColor(),
                item.getVariantSize()
        );
    }

    private static MoneyDto toMoneyDto(Money money) {
        return new MoneyDto(money.amount(), money.currency());
    }
}
