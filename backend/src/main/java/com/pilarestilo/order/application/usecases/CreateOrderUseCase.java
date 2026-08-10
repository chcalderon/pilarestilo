package com.pilarestilo.order.application.usecases;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.pilarestilo.customeraddress.application.CustomerAddressBookService;
import com.pilarestilo.customeraddress.domain.model.CustomerAddress;
import com.pilarestilo.discount.application.DiscountRedemptionService;
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
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CreateOrderUseCase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final DomainEventPublisher eventPublisher;
    private final OrderRemoteCommandClient orderRemoteCommandClient;
    private final SystemSettingsRepository systemSettingsRepository;
    private final DiscountRedemptionService discountRedemptionService;
    private final CustomerAddressBookService customerAddressBookService;

    public CreateOrderUseCase(OrderRepository orderRepository,
                               ProductRepository productRepository,
                               InventoryService inventoryService,
                               DomainEventPublisher eventPublisher,
                               OrderRemoteCommandClient orderRemoteCommandClient,
                               SystemSettingsRepository systemSettingsRepository,
                               DiscountRedemptionService discountRedemptionService,
                               CustomerAddressBookService customerAddressBookService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
        this.orderRemoteCommandClient = orderRemoteCommandClient;
        this.systemSettingsRepository = systemSettingsRepository;
        this.discountRedemptionService = discountRedemptionService;
        this.customerAddressBookService = customerAddressBookService;
    }

    @Transactional
    public OrderDto execute(CreateOrderCommand command) {
        var settings = systemSettingsRepository.get();
        validatePaymentMethodEnabled(command.paymentMethod(), settings);
        ResolvedShippingSelection shippingSelection = resolveShippingSelection(command, settings);

        if (orderRemoteCommandClient.isWriteEnabled()) {
            // OrderRemoteCommandClient.toCreateRequest drops discountCode from the outbound
            // payload, so the customer was shown a discount and then charged full price with no
            // error. Failing loudly until order-service speaks the redemption protocol.
            if (command.discountCode() != null && !command.discountCode().isBlank()) {
                throw new DomainException("Los códigos de descuento no están disponibles en este modo");
            }
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
                    itemCmd.quantity(),
                    itemCmd.variantColor(),
                    itemCmd.variantSize()
            ));
        }

        BigDecimal subtotalAmount = orderItems.stream()
                .map(item -> item.getUnitPrice().amount().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Evaluated before inventory is touched. Reserving first meant an invalid code left stock
        // reserved -- masked by @Transactional locally, but not when APP_INVENTORY_REMOTE_ENABLED
        // sends the reservation to inventory-service as a committed HTTP call no rollback undoes.
        DiscountRedemptionService.DiscountEvaluation evaluation = null;
        if (command.discountCode() != null && !command.discountCode().isBlank()) {
            evaluation = discountRedemptionService.evaluate(
                    command.discountCode(), Money.of(subtotalAmount), command.customerId());
        }

        for (CreateOrderCommand.OrderItemCommand itemCmd : command.items()) {
            inventoryService.reserve(
                    itemCmd.productId(),
                    itemCmd.quantity(),
                    itemCmd.variantColor(),
                    itemCmd.variantSize()
            );
        }

        Money discount = evaluation != null ? evaluation.amount() : Money.zero();

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
                shippingSelection.zoneCode(),
                shippingSelection.courierId(),
                shippingSelection.courierName(),
                shippingSelection.paymentMode(),
                shippingSelection.addressId(),
                shippingSelection.addressReference(),
                command.notes(),
                command.salesChannel()
        );

        Order saved = orderRepository.save(order);

        // After save: discount_code_usages.order_id references orders(id). Losing a capacity race
        // here throws and rolls back the order, which is correct -- the customer was quoted a
        // price that is no longer available.
        if (evaluation != null) {
            discountRedemptionService.reserve(evaluation, command.customerId(), saved.getId());
        }

        eventPublisher.publish(new OrderCreated(saved.getId(), saved.getCustomerId(), Instant.now()));
        return OrderMapper.toDto(saved);
    }

    private void validatePaymentMethodEnabled(PaymentMethod paymentMethod, SystemSettings settings) {
        if (paymentMethod == PaymentMethod.TRANSFER && !settings.isPaymentMethodBankTransferEnabled()) {
            throw new DomainException("Payment method TRANSFER is currently disabled");
        }

        if (paymentMethod == PaymentMethod.WEBPAY || paymentMethod == PaymentMethod.MERCADOPAGO) {
            if (!settings.isPaymentMethodGatewayEnabled()) {
                throw new DomainException("Payment method " + paymentMethod + " is currently disabled");
            }
            if (settings.getPaymentGatewayProviders().isEmpty()) {
                throw new DomainException("No payment gateway provider is configured");
            }
        }
    }

    private ResolvedShippingSelection resolveShippingSelection(
            CreateOrderCommand command,
            SystemSettings settings
    ) {
        String zoneCode = normalizeRequired(command.shippingZoneCode(), "shipping zone").toUpperCase(Locale.ROOT);
        String courierId = normalizeRequired(command.shippingCourierId(), "shipping courier");
        Map<String, String> activeCouriers = parseActiveCourierMap(settings.getShippingCouriersJson());
        Set<String> activeZones = parseActiveZones(settings.getShippingZonesJson());

        if (!activeZones.contains(zoneCode)) {
            throw new DomainException("Selected shipping zone is not available");
        }
        if (!activeCouriers.containsKey(courierId)) {
            throw new DomainException("Selected shipping courier is not available");
        }

        String courierName = activeCouriers.get(courierId);
        String paymentMode = settings.getShippingPaymentMode().name();
        CustomerAddress address = customerAddressBookService.resolveOwnedAddress(
                command.customerId(),
                command.shippingAddressId()
        );
        String addressReference = composeAddressSnapshot(address);

        return new ResolvedShippingSelection(
                zoneCode,
                courierId,
                courierName,
                paymentMode,
                address.getId(),
                addressReference
        );
    }

    private Set<String> parseActiveZones(String shippingZonesJson) {
        try {
            if (shippingZonesJson == null || shippingZonesJson.isBlank()) {
                throw new DomainException("No shipping zones configured");
            }
            JsonNode root = OBJECT_MAPPER.readTree(shippingZonesJson);
            if (root == null || !root.isArray()) {
                throw new DomainException("Invalid shipping zones configuration");
            }
            java.util.LinkedHashSet<String> zoneCodes = new java.util.LinkedHashSet<>();
            for (JsonNode node : root) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                boolean active = !node.has("active") || node.path("active").asBoolean(true);
                String code = node.path("code").asString("").trim();
                if (!active || code.isBlank()) {
                    continue;
                }
                zoneCodes.add(code.toUpperCase(Locale.ROOT));
            }
            if (zoneCodes.isEmpty()) {
                throw new DomainException("No active shipping zones configured");
            }
            return Set.copyOf(zoneCodes);
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainException("Could not parse shipping zones configuration");
        }
    }

    private Map<String, String> parseActiveCourierMap(String shippingCouriersJson) {
        try {
            if (shippingCouriersJson == null || shippingCouriersJson.isBlank()) {
                throw new DomainException("No shipping couriers configured");
            }
            JsonNode root = OBJECT_MAPPER.readTree(shippingCouriersJson);
            if (root == null || !root.isArray()) {
                throw new DomainException("Invalid shipping couriers configuration");
            }
            Map<String, String> couriers = new LinkedHashMap<>();
            for (JsonNode node : root) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                boolean active = !node.has("active") || node.path("active").asBoolean(true);
                String id = node.path("id").asString("").trim();
                String name = node.path("name").asString("").trim();
                if (!active || id.isBlank() || name.isBlank()) {
                    continue;
                }
                couriers.put(id, name);
            }
            if (couriers.isEmpty()) {
                throw new DomainException("No active shipping couriers configured");
            }
            return Map.copyOf(couriers);
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DomainException("Could not parse shipping couriers configuration");
        }
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new DomainException("Missing " + label);
        }
        return value.trim();
    }

    private String composeAddressSnapshot(CustomerAddress address) {
        StringBuilder sb = new StringBuilder();
        sb.append(address.getRecipientName())
                .append(" | ")
                .append(address.getPhone())
                .append(" | ")
                .append(address.getLine1());
        if (address.getLine2() != null && !address.getLine2().isBlank()) {
            sb.append(", ").append(address.getLine2());
        }
        sb.append(", ").append(address.getComuna())
                .append(", ").append(address.getCity())
                .append(", ").append(address.getRegion());
        if (address.getReference() != null && !address.getReference().isBlank()) {
            sb.append(" | Ref: ").append(address.getReference());
        }
        return sb.toString();
    }

    private record ResolvedShippingSelection(
            String zoneCode,
            String courierId,
            String courierName,
            String paymentMode,
            UUID addressId,
            String addressReference
    ) {
    }
}
