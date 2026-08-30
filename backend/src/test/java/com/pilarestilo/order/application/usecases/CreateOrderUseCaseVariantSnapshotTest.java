package com.pilarestilo.order.application.usecases;

import com.pilarestilo.customeraddress.application.CustomerAddressBookService;
import com.pilarestilo.customeraddress.domain.model.CustomerAddress;
import com.pilarestilo.discount.application.DiscountRedemptionService;
import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.order.application.commands.CreateOrderCommand;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.systemsettings.domain.model.SystemSettings;
import com.pilarestilo.systemsettings.domain.ports.SystemSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseVariantSnapshotTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock InventoryService inventoryService;
    @Mock DomainEventPublisher eventPublisher;
    @Mock SystemSettingsRepository systemSettingsRepository;
    @Mock DiscountRedemptionService discountRedemptionService;
    @Mock CustomerAddressBookService customerAddressBookService;

    CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateOrderUseCase(
                orderRepository,
                productRepository,
                inventoryService,
                eventPublisher,
                systemSettingsRepository,
                discountRedemptionService,
                customerAddressBookService
        );
    }

    @Test
    void variant_color_and_size_are_captured_in_saved_order_items() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();


        // Settings with bank transfer enabled and default couriers/zones
        SystemSettings settings = SystemSettings.createDefault();
        when(systemSettingsRepository.get()).thenReturn(settings);

        // Product stub
        Product product = productWithPrice(productId, Money.of(java.math.BigDecimal.valueOf(15000)));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // Address stub
        CustomerAddress address = CustomerAddress.reconstruct(
                addressId,
                customerId,
                "Casa",
                "Test User",
                "+56912345678",
                "Av. Ejemplo 100",
                null,
                5,
                501L,
                50101L,
                "Los Andes",
                "Los Andes",
                "Valparaíso",
                null,
                true,
                Instant.now(),
                Instant.now()
        );
        when(customerAddressBookService.resolveOwnedAddress(customerId, addressId)).thenReturn(address);

        // Repository save: capture the Order argument and return it
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Build command with variant info
        CreateOrderCommand command = new CreateOrderCommand(
                customerId,
                List.of(new CreateOrderCommand.OrderItemCommand(productId, 2, "Rojo", "M")),
                PaymentMethod.TRANSFER,
                "LOCAL",
                "starken",
                addressId,
                null,
                null,
                false,
                null
        );

        OrderDto result = useCase.execute(command);

        assertThat(result).isNotNull();

        // Verify the Order passed to save contains variant snapshot
        Order saved = orderCaptor.getValue();
        assertThat(saved.getItems()).hasSize(1);
        OrderItem item = saved.getItems().get(0);
        assertThat(item.getVariantColor()).isEqualTo("Rojo");
        assertThat(item.getVariantSize()).isEqualTo("M");
        assertThat(item.getProductId()).isEqualTo(productId);
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    void null_variant_fields_are_preserved_when_not_provided() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();


        SystemSettings settings = SystemSettings.createDefault();
        when(systemSettingsRepository.get()).thenReturn(settings);

        Product product = productWithPrice(productId, Money.of(java.math.BigDecimal.valueOf(9990)));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        CustomerAddress address = CustomerAddress.reconstruct(
                addressId,
                customerId,
                "Trabajo",
                "Test User",
                "+56912345678",
                "Calle Falsa 123",
                null,
                5,
                501L,
                50101L,
                "Los Andes",
                "Los Andes",
                "Valparaíso",
                null,
                false,
                Instant.now(),
                Instant.now()
        );
        when(customerAddressBookService.resolveOwnedAddress(customerId, addressId)).thenReturn(address);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // OrderItemCommand with null variants (no-variant product)
        CreateOrderCommand command = new CreateOrderCommand(
                customerId,
                List.of(new CreateOrderCommand.OrderItemCommand(productId, 1, null, null)),
                PaymentMethod.TRANSFER,
                "LOCAL",
                "starken",
                addressId,
                null,
                null,
                false,
                null
        );

        OrderDto result = useCase.execute(command);
        assertThat(result).isNotNull();

        Order saved = orderCaptor.getValue();
        OrderItem item = saved.getItems().get(0);
        assertThat(item.getVariantColor()).isNull();
        assertThat(item.getVariantSize()).isNull();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Product productWithPrice(UUID productId, Money price) {
        Product product = Product.create(
                "Test Product",
                "A test product",
                price,
                null,
                com.pilarestilo.product.domain.enums.ProductCondition.USED,
                "TestBrand",
                100
        );
        product.setId(productId);
        return product;
    }
}
