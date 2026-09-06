package com.pilarestilo.order.application.usecases;

import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.inventory.domain.model.StockMovementOrigin;
import com.pilarestilo.order.application.commands.RegisterExternalSaleCommand;
import com.pilarestilo.order.application.mappers.OrderMapper;
import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.events.OrderStatusChanged;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.pilarestilo.order.domain.enums.OrderStatus.PAID;
import static com.pilarestilo.order.domain.enums.OrderStatus.PENDING_PAYMENT;

/**
 * The server side of an off-platform sale. Behaviour lives here; the web controller is a transport.
 * Not to be confused with {@code CreateOrderUseCase}, which is the web checkout and is left alone.
 */
@Service
public class RegisterExternalSaleUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final DomainEventPublisher eventPublisher;
    private final SystemSettingsRepository systemSettingsRepository;

    public RegisterExternalSaleUseCase(OrderRepository orderRepository,
                                       ProductRepository productRepository,
                                       InventoryService inventoryService,
                                       DomainEventPublisher eventPublisher,
                                       SystemSettingsRepository systemSettingsRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
        this.systemSettingsRepository = systemSettingsRepository;
    }

    @Transactional
    public RegisterExternalSaleResult execute(RegisterExternalSaleCommand cmd) {
        validate(cmd);

        if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) {
            var existing = orderRepository.findByExternalIdempotencyKey(cmd.idempotencyKey());
            if (existing.isPresent()) {
                return new RegisterExternalSaleResult(OrderMapper.toDto(existing.get()), true);
            }
        }

        List<OrderItem> items = new ArrayList<>();
        for (RegisterExternalSaleCommand.Line line : cmd.items()) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new NoSuchElementException("Product not found: " + line.productId()));
            items.add(new OrderItem(
                    UUID.randomUUID(), product.getId(), product.getName(),
                    Money.of(line.unitPrice()), line.quantity(),
                    line.variantColor(), line.variantSize()));
        }

        BigDecimal vatRate = systemSettingsRepository.get().getTax().vatRate();
        Order order = Order.createExternalSale(
                cmd.buyerName(), cmd.buyerContact(), items, cmd.paymentMethod(),
                cmd.deliveryMethod(), cmd.shippingAddress(), cmd.notes(),
                cmd.salesChannel(), vatRate, cmd.idempotencyKey());

        // Sell stock — blocking. reserve() throws InsufficientStockException (-> 409) when a line
        // would go short, and @Transactional rolls the whole thing back. Reserve + confirm in one
        // go: the sale already happened, there is no window where the stock is only "held". The
        // order exists now, so its RESERVE/CONFIRM ledger lines name it rather than a discarded UUID.
        for (RegisterExternalSaleCommand.Line line : cmd.items()) {
            StockMovementOrigin origin = StockMovementOrigin.forOrder(order.getId());
            inventoryService.reserve(line.productId(), line.quantity(),
                    line.variantColor(), line.variantSize(), origin);
            inventoryService.confirm(line.productId(), line.quantity(),
                    line.variantColor(), line.variantSize(), origin);
        }

        order.markAsPendingPayment();
        order.markAsPaid();

        Order saved = orderRepository.save(order);

        eventPublisher.publish(new OrderCreated(saved.getId(), null, Instant.now()));
        eventPublisher.publish(new OrderStatusChanged(
                saved.getId(), null, PENDING_PAYMENT, PAID, Instant.now()));

        return new RegisterExternalSaleResult(OrderMapper.toDto(saved), false);
    }

    private void validate(RegisterExternalSaleCommand cmd) {
        if (cmd.items() == null || cmd.items().isEmpty()) {
            throw new DomainException("Al menos un producto es obligatorio");
        }
        if (cmd.items().size() > 50) {
            throw new DomainException("Demasiadas lineas");
        }
        if (isBlank(cmd.buyerName()) || isBlank(cmd.buyerContact())) {
            throw new DomainException("Nombre y contacto del comprador son obligatorios");
        }
        if (cmd.salesChannel() == null || cmd.paymentMethod() == null || cmd.deliveryMethod() == null) {
            throw new DomainException("Canal, metodo de pago y entrega son obligatorios");
        }
        if (cmd.deliveryMethod() == DeliveryMethod.SHIPPING && isBlank(cmd.shippingAddress())) {
            throw new DomainException("La direccion es obligatoria para un envio");
        }
        cmd.items().forEach(RegisterExternalSaleUseCase::validateLine);
    }

    private static void validateLine(RegisterExternalSaleCommand.Line line) {
        if (line.productId() == null) {
            throw new DomainException("Falta el producto en una linea");
        }
        if (line.quantity() < 1 || line.quantity() > 999) {
            throw new DomainException("Cantidad invalida");
        }
        if (line.unitPrice() == null || line.unitPrice().signum() < 0) {
            throw new DomainException("Precio invalido");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
