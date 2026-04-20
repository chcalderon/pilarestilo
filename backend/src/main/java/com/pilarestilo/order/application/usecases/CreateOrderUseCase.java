package com.pilarestilo.order.application.usecases;

import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.order.application.commands.CreateOrderCommand;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.mappers.OrderMapper;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.shared.application.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final DomainEventPublisher eventPublisher;

    public CreateOrderUseCase(OrderRepository orderRepository,
                               ProductRepository productRepository,
                               InventoryService inventoryService,
                               DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderDto execute(CreateOrderCommand command) {
        List<OrderItem> orderItems = new ArrayList<>();

        for (CreateOrderCommand.OrderItemCommand itemCmd : command.items()) {
            Product product = productRepository.findById(itemCmd.productId())
                    .orElseThrow(() -> new DomainException("Product not found: " + itemCmd.productId()));

            OrderItem orderItem = new OrderItem(
                    UUID.randomUUID(),
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    itemCmd.quantity()
            );
            orderItems.add(orderItem);
        }

        // Reserve stock for each item
        for (CreateOrderCommand.OrderItemCommand itemCmd : command.items()) {
            inventoryService.reserve(itemCmd.productId(), itemCmd.quantity());
        }

        var discount = command.discountAmount() != null ? command.discountAmount() : Money.zero();
        if (command.employeeDiscountEligible()) {
            BigDecimal subtotalAmount = orderItems.stream()
                    .map(item -> item.getUnitPrice().amount().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal employeeDiscountAmount = subtotalAmount
                    .multiply(new BigDecimal("0.10"))
                    .setScale(2, RoundingMode.HALF_UP);
            var employeeDiscount = Money.of(employeeDiscountAmount, discount.currency());
            discount = discount.add(employeeDiscount);
        }

        Order order = Order.create(
                command.customerId(),
                orderItems,
                discount,
                command.paymentMethod(),
                command.notes()
        );

        Order saved = orderRepository.save(order);
        eventPublisher.publish(new OrderCreated(saved.getId(), saved.getCustomerId(), Instant.now()));
        return OrderMapper.toDto(saved);
    }
}
