package com.pilarestilo.order.application.usecases;

import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.domain.ports.DiscountRepository;
import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.order.application.commands.CreateOrderCommand;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.mappers.OrderMapper;
import com.pilarestilo.order.application.remote.OrderRemoteCommandClient;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final DomainEventPublisher eventPublisher;
    private final OrderRemoteCommandClient orderRemoteCommandClient;
    private final SystemSettingsRepository systemSettingsRepository;
    private final DiscountRepository discountRepository;

    public CreateOrderUseCase(OrderRepository orderRepository,
                               ProductRepository productRepository,
                               InventoryService inventoryService,
                               DomainEventPublisher eventPublisher,
                               OrderRemoteCommandClient orderRemoteCommandClient,
                               SystemSettingsRepository systemSettingsRepository,
                               DiscountRepository discountRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
        this.orderRemoteCommandClient = orderRemoteCommandClient;
        this.systemSettingsRepository = systemSettingsRepository;
        this.discountRepository = discountRepository;
    }

    @Transactional
    public OrderDto execute(CreateOrderCommand command) {
        validatePaymentMethodEnabled(command.paymentMethod());

        if (orderRemoteCommandClient.isWriteEnabled()) {
            OrderDto created = orderRemoteCommandClient.create(command);
            eventPublisher.publish(new OrderCreated(created.id(), created.customerId(), Instant.now()));
            return created;
        }

        List<OrderItem> orderItems = new ArrayList<>();

        for (CreateOrderCommand.OrderItemCommand itemCmd : command.items()) {
            Product product = productRepository.findById(itemCmd.productId())
                    .orElseThrow(() -> new DomainException("Product not found: " + itemCmd.productId()));

            orderItems.add(new OrderItem(
                    UUID.randomUUID(),
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    itemCmd.quantity()
            ));
        }

        for (CreateOrderCommand.OrderItemCommand itemCmd : command.items()) {
            inventoryService.reserve(itemCmd.productId(), itemCmd.quantity());
        }

        BigDecimal subtotalAmount = orderItems.stream()
                .map(item -> item.getUnitPrice().amount().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Money discount = Money.zero();

        // Apply discount code
        if (command.discountCode() != null && !command.discountCode().isBlank()) {
            Discount disc = discountRepository.findByCode(command.discountCode().toUpperCase())
                    .orElseThrow(() -> new DomainException("Código de descuento no encontrado"));
            Money subtotal = Money.of(subtotalAmount);
            discount = disc.apply(subtotal);
            discountRepository.save(disc); // persist incremented timesUsed
            discountRepository.recordUsage(disc.getId(), command.customerId());
        }

        // Employee discount stacks on top of code discount
        if (command.employeeDiscountEligible()) {
            BigDecimal employeeDiscountAmount = subtotalAmount
                    .multiply(new BigDecimal("0.10"))
                    .setScale(2, RoundingMode.HALF_UP);
            discount = discount.add(Money.of(employeeDiscountAmount, discount.currency()));
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

    private void validatePaymentMethodEnabled(PaymentMethod paymentMethod) {
        var settings = systemSettingsRepository.get();

        if (paymentMethod == PaymentMethod.BANK_TRANSFER && !settings.isPaymentMethodBankTransferEnabled()) {
            throw new DomainException("Payment method BANK_TRANSFER is currently disabled");
        }

        if (paymentMethod == PaymentMethod.PAYMENT_GATEWAY) {
            if (!settings.isPaymentMethodGatewayEnabled()) {
                throw new DomainException("Payment method PAYMENT_GATEWAY is currently disabled");
            }
            if (settings.getPaymentGatewayProviders().isEmpty()) {
                throw new DomainException("No payment gateway provider is configured");
            }
        }
    }
}
