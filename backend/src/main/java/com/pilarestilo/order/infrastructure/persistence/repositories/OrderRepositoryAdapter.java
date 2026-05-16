package com.pilarestilo.order.infrastructure.persistence.repositories;

import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.order.infrastructure.persistence.entities.OrderEntity;
import com.pilarestilo.order.infrastructure.persistence.entities.OrderItemEntity;
import com.pilarestilo.shared.application.Money;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = toEntity(order);
        OrderEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public Page<Order> findByCustomerId(UUID customerId, Pageable pageable) {
        return jpaRepository.findByCustomerId(customerId, pageable).map(this::toDomain);
    }

    private OrderEntity toEntity(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setId(order.getId());
        entity.setCustomerId(order.getCustomerId());
        entity.setSubtotalAmount(order.getSubtotal().amount());
        entity.setSubtotalCurrency(order.getSubtotal().currency());
        entity.setDiscountAmount(order.getDiscountAmount().amount());
        entity.setDiscountCurrency(order.getDiscountAmount().currency());
        entity.setTotalAmount(order.getTotalAmount().amount());
        entity.setTotalCurrency(order.getTotalAmount().currency());
        entity.setPaymentMethod(order.getPaymentMethod());
        entity.setShippingZoneCode(order.getShippingZoneCode());
        entity.setShippingCourierId(order.getShippingCourierId());
        entity.setShippingCourierName(order.getShippingCourierName());
        entity.setShippingPaymentMode(order.getShippingPaymentMode());
        entity.setShippingAddressId(order.getShippingAddressId());
        entity.setShippingAddressReference(order.getShippingAddressReference());
        entity.setNotes(order.getNotes());
        entity.setSalesChannel(order.getSalesChannel() != null ? order.getSalesChannel() : SalesChannel.ECOMMERCE);
        entity.setStatus(order.getStatus());
        entity.setCreatedAt(order.getCreatedAt());
        entity.setUpdatedAt(order.getUpdatedAt());

        List<OrderItemEntity> itemEntities = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            OrderItemEntity itemEntity = new OrderItemEntity();
            itemEntity.setId(item.getId());
            itemEntity.setOrder(entity);
            itemEntity.setProductId(item.getProductId());
            itemEntity.setProductName(item.getProductName());
            itemEntity.setUnitPriceAmount(item.getUnitPrice().amount());
            itemEntity.setUnitPriceCurrency(item.getUnitPrice().currency());
            itemEntity.setQuantity(item.getQuantity());
            itemEntities.add(itemEntity);
        }
        entity.setItems(itemEntities);
        return entity;
    }

    private Order toDomain(OrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(ie -> new OrderItem(
                        ie.getId(),
                        ie.getProductId(),
                        ie.getProductName(),
                        new Money(ie.getUnitPriceAmount(), ie.getUnitPriceCurrency()),
                        ie.getQuantity()
                ))
                .toList();

        return Order.reconstruct(
                entity.getId(),
                entity.getCustomerId(),
                items,
                new Money(entity.getSubtotalAmount(), entity.getSubtotalCurrency()),
                new Money(entity.getDiscountAmount(), entity.getDiscountCurrency()),
                new Money(entity.getTotalAmount(), entity.getTotalCurrency()),
                entity.getPaymentMethod(),
                entity.getShippingZoneCode(),
                entity.getShippingCourierId(),
                entity.getShippingCourierName(),
                entity.getShippingPaymentMode(),
                entity.getShippingAddressId(),
                entity.getShippingAddressReference(),
                entity.getNotes(),
                entity.getSalesChannel() != null ? entity.getSalesChannel() : SalesChannel.ECOMMERCE,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
