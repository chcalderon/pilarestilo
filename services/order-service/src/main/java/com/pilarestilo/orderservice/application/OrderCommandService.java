package com.pilarestilo.orderservice.application;

import com.pilarestilo.orderservice.domain.OrderStatus;
import com.pilarestilo.orderservice.domain.PaymentMethod;
import com.pilarestilo.orderservice.persistence.OrderEntity;
import com.pilarestilo.orderservice.persistence.OrderItemEntity;
import com.pilarestilo.orderservice.persistence.OrderRepository;
import com.pilarestilo.orderservice.persistence.PaymentEntity;
import com.pilarestilo.orderservice.persistence.PaymentRepository;
import com.pilarestilo.orderservice.persistence.ProductEntity;
import com.pilarestilo.orderservice.persistence.ProductRepository;
import com.pilarestilo.orderservice.web.dto.CreateOrderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderCommandService {

    private static final String DEFAULT_CURRENCY = "CLP";
    private static final String PAYMENT_STATUS_PENDING = "PENDING";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;

    public OrderCommandService(OrderRepository orderRepository,
                               ProductRepository productRepository,
                               PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public OrderEntity create(CreateOrderRequest request) {
        validateCreateRequest(request);
        PaymentMethod paymentMethod = parsePaymentMethod(request.paymentMethod());
        Instant now = Instant.now();

        List<OrderLineSnapshot> lines = loadOrderLines(request.items());
        reserveStock(lines, now);

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
        order.setNotes(request.notes());
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

            ProductEntity product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new NoSuchElementException("Product not found: " + item.productId()));
            lines.add(new OrderLineSnapshot(product, item.quantity()));
        }
        return lines;
    }

    private void reserveStock(List<OrderLineSnapshot> lines, Instant now) {
        for (OrderLineSnapshot line : lines) {
            int updated = productRepository.reserveStockAndTouch(line.product().getId(), line.quantity(), now);
            if (updated == 0) {
                throw new IllegalStateException("Insufficient stock for product: " + line.product().getId());
            }
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
        paymentRepository.save(payment);
    }

    private record OrderLineSnapshot(
            ProductEntity product,
            int quantity
    ) {
    }
}
