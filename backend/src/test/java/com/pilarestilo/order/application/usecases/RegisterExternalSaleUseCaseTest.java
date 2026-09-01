package com.pilarestilo.order.application.usecases;

import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.inventory.domain.InsufficientStockException;
import com.pilarestilo.inventory.domain.model.StockMovementOrigin;
import com.pilarestilo.order.application.commands.RegisterExternalSaleCommand;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
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
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterExternalSaleUseCaseTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock InventoryService inventoryService;
    @Mock DomainEventPublisher eventPublisher;
    @Mock SystemSettingsRepository systemSettingsRepository;

    RegisterExternalSaleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterExternalSaleUseCase(orderRepository, productRepository,
                inventoryService, eventPublisher, systemSettingsRepository);
    }

    private void stubSettings() {
        SystemSettings s = mock(SystemSettings.class, RETURNS_DEEP_STUBS);
        when(s.getTax().vatRate()).thenReturn(new BigDecimal("19.00"));
        when(systemSettingsRepository.get()).thenReturn(s);
    }

    private void stubSettingsAndSave() {
        stubSettings();
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Registers a product the repository will return. The use case reads only id + name. */
    private void stubProduct(UUID id) {
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(id);
        when(p.getName()).thenReturn("Vestido");
        when(productRepository.findById(id)).thenReturn(Optional.of(p));
    }

    private RegisterExternalSaleCommand.Line line(UUID pid, String color, String size, int qty, String price) {
        return new RegisterExternalSaleCommand.Line(pid, color, size, qty, new BigDecimal(price));
    }

    @Test
    void shipping_sale_creates_a_paid_order_and_sells_stock_and_publishes_both_events() {
        stubSettingsAndSave();
        UUID pid = UUID.randomUUID();
        stubProduct(pid);
        var cmd = new RegisterExternalSaleCommand("k1", "Javiera", "+56911112222",
                SalesChannel.INSTAGRAM, PaymentMethod.TRANSFER, DeliveryMethod.SHIPPING,
                "Av. Siempre Viva 742", "por IG", List.of(line(pid, "Rojo", "M", 2, "15000")));

        OrderDto dto = useCase.execute(cmd).dto();

        assertThat(dto.status()).isEqualTo(OrderStatus.PAID);
        assertThat(dto.salesChannel()).isEqualTo(SalesChannel.INSTAGRAM);
        assertThat(dto.deliveryMethod()).isEqualTo(DeliveryMethod.SHIPPING);
        assertThat(dto.buyerName()).isEqualTo("Javiera");
        assertThat(dto.totalAmount().amount()).isEqualByComparingTo("30000"); // edited price, not 19990
        verify(inventoryService).reserve(eq(pid), eq(2), eq("Rojo"), eq("M"), any());
        verify(inventoryService).confirm(eq(pid), eq(2), eq("Rojo"), eq("M"), any());
        verify(eventPublisher).publish(isA(OrderCreated.class));
        verify(eventPublisher).publish(isA(OrderStatusChanged.class));
    }

    @Test
    void the_stock_movement_origin_carries_the_id_of_the_order_that_persists() {
        stubSettingsAndSave();
        UUID pid = UUID.randomUUID();
        stubProduct(pid);
        var cmd = new RegisterExternalSaleCommand("k-ref", "Javiera", "+56911112222",
                SalesChannel.INSTAGRAM, PaymentMethod.TRANSFER, DeliveryMethod.PICKUP, null, "por IG",
                List.of(line(pid, "Rojo", "M", 1, "15000")));

        UUID orderId = useCase.execute(cmd).dto().id();

        ArgumentCaptor<StockMovementOrigin> reserveOrigin = ArgumentCaptor.forClass(StockMovementOrigin.class);
        ArgumentCaptor<StockMovementOrigin> confirmOrigin = ArgumentCaptor.forClass(StockMovementOrigin.class);
        verify(inventoryService).reserve(eq(pid), eq(1), eq("Rojo"), eq("M"), reserveOrigin.capture());
        verify(inventoryService).confirm(eq(pid), eq(1), eq("Rojo"), eq("M"), confirmOrigin.capture());
        assertThat(reserveOrigin.getValue().referenceId()).isEqualTo(orderId);
        assertThat(confirmOrigin.getValue().referenceId()).isEqualTo(orderId);
    }

    @Test
    void pickup_sale_has_no_address_and_still_publishes_the_paid_event() {
        stubSettingsAndSave();
        UUID pid = UUID.randomUUID();
        stubProduct(pid);
        var cmd = new RegisterExternalSaleCommand("k2", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                List.of(line(pid, null, null, 1, "8000")));

        OrderDto dto = useCase.execute(cmd).dto();

        assertThat(dto.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(dto.shippingAddressReference()).isNull();
        verify(eventPublisher, times(2)).publish(any());
    }

    @Test
    void insufficient_stock_rolls_back_nothing_is_saved() {
        stubSettings();
        UUID pid = UUID.randomUUID();
        stubProduct(pid);
        doThrow(new InsufficientStockException("Stock insuficiente para Rojo / M"))
                .when(inventoryService).reserve(eq(pid), anyInt(), any(), any(), any());
        var cmd = new RegisterExternalSaleCommand("k3", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                List.of(line(pid, "Rojo", "M", 5, "8000")));

        assertThatThrownBy(() -> useCase.execute(cmd)).isInstanceOf(InsufficientStockException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void unknown_product_throws() {
        UUID pid = UUID.randomUUID();
        when(productRepository.findById(pid)).thenReturn(Optional.empty());
        var cmd = new RegisterExternalSaleCommand("k4", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                List.of(line(pid, null, null, 1, "8000")));

        assertThatThrownBy(() -> useCase.execute(cmd)).isInstanceOf(java.util.NoSuchElementException.class);
        verify(inventoryService, never()).reserve(any(), anyInt(), any(), any(), any());
    }

    @Test
    void missing_address_for_a_shipping_sale_throws_before_any_write() {
        var cmd = new RegisterExternalSaleCommand("k5", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.SHIPPING, "  ", null,
                List.of(line(UUID.randomUUID(), null, null, 1, "8000")));

        assertThatThrownBy(() -> useCase.execute(cmd)).isInstanceOf(DomainException.class);
        verifyNoInteractions(inventoryService, orderRepository);
    }

    @Test
    void a_repeated_idempotency_key_returns_the_first_order_and_creates_nothing() {
        Order existing = Order.createExternalSale("Ana", "@ana",
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "x",
                        Money.of(BigDecimal.TEN), 1, null, null)),
                PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                SalesChannel.WHATSAPP, new BigDecimal("19.00"), "dup");
        when(orderRepository.findByExternalIdempotencyKey("dup")).thenReturn(Optional.of(existing));
        var cmd = new RegisterExternalSaleCommand("dup", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                List.of(line(UUID.randomUUID(), null, null, 1, "10")));

        var result = useCase.execute(cmd);

        assertThat(result.replayed()).isTrue();
        assertThat(result.dto().id()).isEqualTo(existing.getId());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void a_zero_price_line_is_accepted() {
        stubSettingsAndSave();
        UUID pid = UUID.randomUUID();
        stubProduct(pid);
        var cmd = new RegisterExternalSaleCommand("k6", "Ana", "@ana",
                SalesChannel.WHATSAPP, PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                List.of(line(pid, null, null, 1, "0")));

        OrderDto dto = useCase.execute(cmd).dto();
        assertThat(dto.totalAmount().amount()).isEqualByComparingTo("0");
    }
}
