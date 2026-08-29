package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import com.pilarestilo.notificationservice.domain.ports.OrderReadPort;
import com.pilarestilo.notificationservice.domain.view.Money;
import com.pilarestilo.notificationservice.domain.view.OrderView;
import com.pilarestilo.notificationservice.domain.view.OrderView.OrderItemView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderReadAdapter implements OrderReadPort {

    private final OrderRoRepository repository;

    public OrderReadAdapter(OrderRoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(transactionManager = "sharedRoTransactionManager", readOnly = true)
    public Optional<OrderView> findById(UUID orderId) {
        return repository.findById(orderId).map(OrderReadAdapter::toView);
    }

    private static OrderView toView(OrderRoEntity e) {
        String currency = e.getTotalCurrency();
        List<OrderItemView> items = e.getItems() == null ? List.of() : e.getItems().stream()
                .map(i -> new OrderItemView(
                        i.getProductName(), i.getVariantColor(), i.getVariantSize(), i.getQuantity(),
                        Money.of(i.getUnitPriceAmount(), i.getUnitPriceCurrency())))
                .toList();
        return new OrderView(
                e.getId(),
                e.getPublicReference(),
                e.getCustomerId(),
                e.getStatus(),
                Money.of(e.getSubtotalAmount(), e.getSubtotalCurrency()),
                Money.of(e.getDiscountAmount(), e.getDiscountCurrency()),
                Money.of(e.getNetAmount(), currency),
                Money.of(e.getTaxAmount(), currency),
                e.getTaxRate(),
                Money.of(e.getTotalAmount(), currency),
                e.getShippingCourierId(),
                e.getShippingCourierName(),
                e.getShippingZoneCode(),
                items);
    }
}
