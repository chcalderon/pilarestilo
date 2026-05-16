package com.pilarestilo.orderservice.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilarestilo.orderservice.domain.OrderStatus;
import com.pilarestilo.orderservice.domain.PaymentMethod;
import com.pilarestilo.orderservice.persistence.OrderEntity;
import com.pilarestilo.orderservice.persistence.OrderItemEntity;
import com.pilarestilo.orderservice.persistence.OrderRepository;
import com.pilarestilo.orderservice.persistence.PaymentEntity;
import com.pilarestilo.orderservice.persistence.PaymentRepository;
import com.pilarestilo.orderservice.persistence.CustomerAddressEntity;
import com.pilarestilo.orderservice.persistence.CustomerAddressRepository;
import com.pilarestilo.orderservice.persistence.ProductEntity;
import com.pilarestilo.orderservice.persistence.ProductRepository;
import com.pilarestilo.orderservice.persistence.SystemSettingsEntity;
import com.pilarestilo.orderservice.persistence.SystemSettingsRepository;
import com.pilarestilo.orderservice.web.dto.CreateOrderRequest;
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
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderCommandService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_CURRENCY = "CLP";
    private static final String PAYMENT_STATUS_PENDING = "PENDING";
    private static final String DEFAULT_TRANSFER_ACCOUNT_HOLDER = "Pilar Estilo";
    private static final String DEFAULT_TRANSFER_ACCOUNT_EMAIL = "admin@pilarestilo.com";
    private static final String DEFAULT_TRANSFER_ACCOUNT_NUMBER = "0000000000";
    private static final String DEFAULT_TRANSFER_BANK_NAME = "Banco de Chile";
    private static final String DEFAULT_TRANSFER_ACCOUNT_TYPE = "Cuenta Corriente";
    private static final String DEFAULT_SHIPPING_PAYMENT_MODE = "POR_PAGAR";
    private static final short DEFAULT_SYSTEM_SETTINGS_ID = 1;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final SystemSettingsRepository systemSettingsRepository;
    private final InventoryCommandClient inventoryCommandClient;
    private final CustomerAddressRepository customerAddressRepository;

    public OrderCommandService(OrderRepository orderRepository,
                               ProductRepository productRepository,
                               PaymentRepository paymentRepository,
                               SystemSettingsRepository systemSettingsRepository,
                               InventoryCommandClient inventoryCommandClient,
                               CustomerAddressRepository customerAddressRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.systemSettingsRepository = systemSettingsRepository;
        this.inventoryCommandClient = inventoryCommandClient;
        this.customerAddressRepository = customerAddressRepository;
    }

    @Transactional
    public OrderEntity create(CreateOrderRequest request) {
        validateCreateRequest(request);
        PaymentMethod paymentMethod = parsePaymentMethod(request.paymentMethod());
        ShippingSelection shippingSelection = resolveShippingSelection(request);
        Instant now = Instant.now();

        List<OrderLineSnapshot> lines = loadOrderLines(request.items());
        reserveStock(lines);

        String currency = resolveCurrency(lines);
        BigDecimal subtotal = calculateSubtotal(lines);
        BigDecimal discount = resolveDiscountAmount(request, subtotal, currency);
        BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);

        OrderEntity order = new OrderEntity();
        order.setId(UUID.randomUUID());
        order.setCustomerId(request.customerId());
        order.setSubtotalAmount(subtotal);
        order.setSubtotalCurrency(currency);
        order.setDiscountAmount(discount);
        order.setDiscountCurrency(currency);
        order.setTotalAmount(total);
        order.setTotalCurrency(currency);
        order.setPaymentMethod(paymentMethod.name());
        order.setShippingZoneCode(shippingSelection.zoneCode());
        order.setShippingCourierId(shippingSelection.courierId());
        order.setShippingCourierName(shippingSelection.courierName());
        order.setShippingPaymentMode(shippingSelection.paymentMode());
        order.setShippingAddressId(shippingSelection.addressId());
        order.setShippingAddressReference(shippingSelection.addressReference());
        order.setNotes(request.notes());
        order.setSalesChannel(request.salesChannel() != null && !request.salesChannel().isBlank()
                ? request.salesChannel().toUpperCase(Locale.ROOT)
                : "ECOMMERCE");
        order.setStatus(OrderStatus.CREATED.name());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.clearItems();

        for (OrderLineSnapshot line : lines) {
            OrderItemEntity item = new OrderItemEntity();
            item.setId(UUID.randomUUID());
            item.setProductId(line.product().getId());
            item.setProductName(line.product().getName());
            item.setUnitPriceAmount(line.product().getPriceAmount().setScale(2, RoundingMode.HALF_UP));
            item.setUnitPriceCurrency(currency);
            item.setQuantity(line.quantity());
            order.addItem(item);
        }

        OrderEntity saved = orderRepository.save(order);
        createPaymentIfAbsent(saved, now);
        return saved;
    }

    @Transactional
    public OrderEntity updateStatus(UUID orderId, String targetStatusRaw) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order id is required");
        }

        OrderStatus targetStatus = parseOrderStatus(targetStatusRaw);
        if (targetStatus == OrderStatus.CREATED) {
            throw new IllegalArgumentException("Unsupported target status: " + targetStatus);
        }

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        OrderStatus currentStatus = parseOrderStatus(order.getStatus());
        validateTransition(currentStatus, targetStatus);

        order.setStatus(targetStatus.name());
        order.setUpdatedAt(Instant.now());
        return orderRepository.save(order);
    }

    private void validateCreateRequest(CreateOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Order request is required");
        }
        if (request.customerId() == null) {
            throw new IllegalArgumentException("Customer id is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        if (request.paymentMethod() == null || request.paymentMethod().isBlank()) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (request.shippingZoneCode() == null || request.shippingZoneCode().isBlank()) {
            throw new IllegalArgumentException("Shipping zone is required");
        }
        if (request.shippingCourierId() == null || request.shippingCourierId().isBlank()) {
            throw new IllegalArgumentException("Shipping courier is required");
        }
        if (request.shippingAddressId() == null) {
            throw new IllegalArgumentException("shippingAddressId is required");
        }
    }

    private List<OrderLineSnapshot> loadOrderLines(List<CreateOrderRequest.OrderItemRequest> items) {
        List<OrderLineSnapshot> lines = new ArrayList<>();
        for (CreateOrderRequest.OrderItemRequest item : items) {
            if (item.productId() == null) {
                throw new IllegalArgumentException("Product id is required");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero");
            }
            if ((item.variantColor() == null) != (item.variantSize() == null)) {
                throw new IllegalArgumentException("variantColor and variantSize must be provided together");
            }

            ProductEntity product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new NoSuchElementException("Product not found: " + item.productId()));
            lines.add(new OrderLineSnapshot(product, item.quantity(), item.variantColor(), item.variantSize()));
        }
        return lines;
    }

    private void reserveStock(List<OrderLineSnapshot> lines) {
        for (OrderLineSnapshot line : lines) {
            inventoryCommandClient.reserve(
                    line.product().getId(),
                    line.quantity(),
                    line.variantColor(),
                    line.variantSize()
            );
        }
    }

    private String resolveCurrency(List<OrderLineSnapshot> lines) {
        String currency = null;
        for (OrderLineSnapshot line : lines) {
            String lineCurrency = line.product().getPriceCurrency();
            if (currency == null) {
                currency = lineCurrency;
                continue;
            }
            if (!currency.equals(lineCurrency)) {
                throw new IllegalArgumentException("All order items must share the same currency");
            }
        }
        return currency == null ? DEFAULT_CURRENCY : currency;
    }

    private BigDecimal calculateSubtotal(List<OrderLineSnapshot> lines) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderLineSnapshot line : lines) {
            BigDecimal lineTotal = line.product().getPriceAmount().multiply(BigDecimal.valueOf(line.quantity()));
            subtotal = subtotal.add(lineTotal);
        }
        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveDiscountAmount(CreateOrderRequest request, BigDecimal subtotal, String currency) {
        BigDecimal discount = request.discountAmount() == null
                ? BigDecimal.ZERO
                : request.discountAmount();

        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount amount cannot be negative");
        }

        String discountCurrency = request.discountCurrency();
        if (discountCurrency != null && !discountCurrency.isBlank() && !currency.equals(discountCurrency)) {
            throw new IllegalArgumentException("Discount currency must match order currency");
        }

        discount = discount.setScale(2, RoundingMode.HALF_UP);

        if (request.employeeDiscountEligible()) {
            BigDecimal employeeDiscount = subtotal
                    .multiply(new BigDecimal("0.10"))
                    .setScale(2, RoundingMode.HALF_UP);
            discount = discount.add(employeeDiscount).setScale(2, RoundingMode.HALF_UP);
        }

        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed order subtotal");
        }

        return discount;
    }

    private ShippingSelection resolveShippingSelection(CreateOrderRequest request) {
        String zoneCode = request.shippingZoneCode().trim().toUpperCase(Locale.ROOT);
        String courierId = request.shippingCourierId().trim();
        CustomerAddressEntity address = customerAddressRepository.findByIdAndCustomerId(
                        request.shippingAddressId(),
                        request.customerId()
                )
                .orElseThrow(() -> new IllegalArgumentException("Shipping address not found for customer"));
        String addressReference = composeAddressSnapshot(address);

        SystemSettingsEntity settings = systemSettingsRepository.findById(DEFAULT_SYSTEM_SETTINGS_ID).orElse(null);
        Set<String> activeZoneCodes = parseActiveShippingZones(settings == null ? null : settings.getShippingZonesJson());
        Map<String, String> activeCouriers = parseActiveShippingCouriers(settings == null ? null : settings.getShippingCouriersJson());

        if (!activeZoneCodes.contains(zoneCode)) {
            throw new IllegalArgumentException("Selected shipping zone is not available");
        }
        if (!activeCouriers.containsKey(courierId)) {
            throw new IllegalArgumentException("Selected shipping courier is not available");
        }

        String paymentModeRaw = settings == null ? null : settings.getShippingPaymentMode();
        String paymentMode = defaultIfBlank(paymentModeRaw, DEFAULT_SHIPPING_PAYMENT_MODE).toUpperCase(Locale.ROOT);

        return new ShippingSelection(
                zoneCode,
                courierId,
                activeCouriers.get(courierId),
                paymentMode,
                address.getId(),
                addressReference
        );
    }

    private Set<String> parseActiveShippingZones(String shippingZonesJson) {
        if (shippingZonesJson == null || shippingZonesJson.isBlank()) {
            return Set.of("LOCAL", "REGIONAL", "NACIONAL");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(shippingZonesJson);
            if (root == null || !root.isArray()) {
                throw new IllegalArgumentException("Invalid shipping zones configuration");
            }
            java.util.LinkedHashSet<String> activeZones = new java.util.LinkedHashSet<>();
            for (JsonNode node : root) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                boolean active = !node.has("active") || node.path("active").asBoolean(true);
                if (!active) {
                    continue;
                }
                String code = node.path("code").asText("").trim().toUpperCase(Locale.ROOT);
                if (!code.isBlank()) {
                    activeZones.add(code);
                }
            }
            if (activeZones.isEmpty()) {
                throw new IllegalArgumentException("No active shipping zones configured");
            }
            return Set.copyOf(activeZones);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not parse shipping zones configuration");
        }
    }

    private Map<String, String> parseActiveShippingCouriers(String shippingCouriersJson) {
        if (shippingCouriersJson == null || shippingCouriersJson.isBlank()) {
            return Map.of("starken", "Starken", "chilexpress", "ChilExpress");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(shippingCouriersJson);
            if (root == null || !root.isArray()) {
                throw new IllegalArgumentException("Invalid shipping couriers configuration");
            }
            Map<String, String> activeCouriers = new LinkedHashMap<>();
            for (JsonNode node : root) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                boolean active = !node.has("active") || node.path("active").asBoolean(true);
                if (!active) {
                    continue;
                }
                String id = node.path("id").asText("").trim();
                String name = node.path("name").asText("").trim();
                if (!id.isBlank() && !name.isBlank()) {
                    activeCouriers.put(id, name);
                }
            }
            if (activeCouriers.isEmpty()) {
                throw new IllegalArgumentException("No active shipping couriers configured");
            }
            return Map.copyOf(activeCouriers);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not parse shipping couriers configuration");
        }
    }

    private PaymentMethod parsePaymentMethod(String raw) {
        try {
            return PaymentMethod.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unsupported payment method: " + raw);
        }
    }

    private OrderStatus parseOrderStatus(String raw) {
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unsupported order status: " + raw);
        }
    }

    private void validateTransition(OrderStatus current, OrderStatus target) {
        if (current == target) {
            return;
        }
        if (target == OrderStatus.CANCELLED) {
            if (current == OrderStatus.DELIVERED) {
                throw new IllegalStateException("Cannot cancel a delivered order");
            }
            return;
        }

        switch (target) {
            case PENDING_PAYMENT -> assertStatus(current, OrderStatus.CREATED);
            case PAYMENT_UNDER_REVIEW -> assertStatus(current, OrderStatus.PENDING_PAYMENT);
            case PAID -> assertOneOf(current, Set.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_UNDER_REVIEW));
            case PREPARING_ORDER -> assertStatus(current, OrderStatus.PAID);
            case SHIPPED -> assertStatus(current, OrderStatus.PREPARING_ORDER);
            case DELIVERED -> assertStatus(current, OrderStatus.SHIPPED);
            default -> throw new IllegalArgumentException("Unsupported target status: " + target);
        }
    }

    private void assertStatus(OrderStatus current, OrderStatus expected) {
        if (current != expected) {
            throw new IllegalStateException("Cannot transition from " + current + ", expected " + expected);
        }
    }

    private void assertOneOf(OrderStatus current, Set<OrderStatus> expected) {
        if (!expected.contains(current)) {
            throw new IllegalStateException("Invalid status transition from " + current);
        }
    }

    private void createPaymentIfAbsent(OrderEntity order, Instant now) {
        if (paymentRepository.findByOrderId(order.getId()).isPresent()) {
            return;
        }
        PaymentEntity payment = new PaymentEntity();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(order.getId());
        payment.setMethod(order.getPaymentMethod());
        payment.setStatus(PAYMENT_STATUS_PENDING);
        payment.setProofReference(null);
        payment.setReviewedBy(null);
        payment.setReviewedAt(null);
        payment.setCreatedAt(now);
        if (PaymentMethod.TRANSFER.name().equals(order.getPaymentMethod())) {
            TransferSnapshot snapshot = resolveTransferSnapshot();
            payment.setTransferAccountHolderName(snapshot.accountHolder());
            payment.setTransferAccountEmail(snapshot.contactEmail());
            payment.setTransferAccountNumber(snapshot.accountNumber());
            payment.setTransferBankName(snapshot.bankName());
            payment.setTransferAccountType(snapshot.accountType());
        }
        paymentRepository.save(payment);
    }

    private TransferSnapshot resolveTransferSnapshot() {
        return systemSettingsRepository.findById(DEFAULT_SYSTEM_SETTINGS_ID)
                .map(this::toTransferSnapshot)
                .orElseGet(this::defaultTransferSnapshot);
    }

    private TransferSnapshot toTransferSnapshot(SystemSettingsEntity settings) {
        return new TransferSnapshot(
                defaultIfBlank(settings.getBankTransferAccountHolder(), DEFAULT_TRANSFER_ACCOUNT_HOLDER),
                defaultIfBlank(settings.getBankTransferContactEmail(), DEFAULT_TRANSFER_ACCOUNT_EMAIL),
                defaultIfBlank(settings.getBankTransferAccountNumber(), DEFAULT_TRANSFER_ACCOUNT_NUMBER),
                defaultIfBlank(settings.getBankTransferBankName(), DEFAULT_TRANSFER_BANK_NAME),
                defaultIfBlank(settings.getBankTransferAccountType(), DEFAULT_TRANSFER_ACCOUNT_TYPE)
        );
    }

    private TransferSnapshot defaultTransferSnapshot() {
        return new TransferSnapshot(
                DEFAULT_TRANSFER_ACCOUNT_HOLDER,
                DEFAULT_TRANSFER_ACCOUNT_EMAIL,
                DEFAULT_TRANSFER_ACCOUNT_NUMBER,
                DEFAULT_TRANSFER_BANK_NAME,
                DEFAULT_TRANSFER_ACCOUNT_TYPE
        );
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String composeAddressSnapshot(CustomerAddressEntity address) {
        StringBuilder sb = new StringBuilder();
        sb.append(address.getRecipientName())
                .append(" | ")
                .append(address.getPhone())
                .append(" | ")
                .append(address.getLine1());
        if (address.getLine2() != null && !address.getLine2().isBlank()) {
            sb.append(", ").append(address.getLine2().trim());
        }
        sb.append(", ").append(address.getComuna())
                .append(", ").append(address.getCity())
                .append(", ").append(address.getRegion());
        if (address.getReference() != null && !address.getReference().isBlank()) {
            sb.append(" | Ref: ").append(address.getReference().trim());
        }
        return sb.toString();
    }

    private record OrderLineSnapshot(
            ProductEntity product,
            int quantity,
            String variantColor,
            String variantSize
    ) {
    }

    private record TransferSnapshot(
            String accountHolder,
            String contactEmail,
            String accountNumber,
            String bankName,
            String accountType
    ) {
    }

    private record ShippingSelection(
            String zoneCode,
            String courierId,
            String courierName,
            String paymentMode,
            UUID addressId,
            String addressReference
    ) {
    }
}
