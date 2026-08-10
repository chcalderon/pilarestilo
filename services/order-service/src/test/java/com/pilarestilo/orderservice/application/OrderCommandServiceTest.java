package com.pilarestilo.orderservice.application;

import com.pilarestilo.orderservice.persistence.CustomerAddressEntity;
import com.pilarestilo.orderservice.persistence.CustomerAddressRepository;
import com.pilarestilo.orderservice.persistence.OrderEntity;
import com.pilarestilo.orderservice.persistence.OrderRepository;
import com.pilarestilo.orderservice.persistence.PaymentEntity;
import com.pilarestilo.orderservice.persistence.PaymentRepository;
import com.pilarestilo.orderservice.persistence.ProductEntity;
import com.pilarestilo.orderservice.persistence.ProductRepository;
import com.pilarestilo.orderservice.persistence.SystemSettingsEntity;
import com.pilarestilo.orderservice.persistence.SystemSettingsRepository;
import com.pilarestilo.orderservice.web.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SystemSettingsRepository systemSettingsRepository;
    @Mock
    private InventoryCommandClient inventoryCommandClient;
    @Mock
    private CustomerAddressRepository customerAddressRepository;

    @Test
    void create_validates_required_fields() {
        OrderCommandService service = service();
        UUID customerId = UUID.randomUUID();
        UUID shippingAddressId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.create(null));
        assertThrows(IllegalArgumentException.class, () -> service.create(baseRequest(null, "TRANSFER", "LOCAL", "starken", shippingAddressId, List.of(item(UUID.randomUUID(), 1, null, null)))));
        assertThrows(IllegalArgumentException.class, () -> service.create(baseRequest(customerId, "TRANSFER", "LOCAL", "starken", shippingAddressId, List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.create(baseRequest(customerId, " ", "LOCAL", "starken", shippingAddressId, List.of(item(UUID.randomUUID(), 1, null, null)))));
        assertThrows(IllegalArgumentException.class, () -> service.create(baseRequest(customerId, "TRANSFER", " ", "starken", shippingAddressId, List.of(item(UUID.randomUUID(), 1, null, null)))));
        assertThrows(IllegalArgumentException.class, () -> service.create(baseRequest(customerId, "TRANSFER", "LOCAL", " ", shippingAddressId, List.of(item(UUID.randomUUID(), 1, null, null)))));
        assertThrows(IllegalArgumentException.class, () -> service.create(baseRequest(customerId, "TRANSFER", "LOCAL", "starken", null, List.of(item(UUID.randomUUID(), 1, null, null)))));
    }

    @Test
    void create_rejects_missing_or_invalid_shipping_selection() {
        OrderCommandService service = service();
        UUID customerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        CreateOrderRequest request = baseRequest(customerId, "TRANSFER", "LOCAL", "starken", addressId, List.of(item(UUID.randomUUID(), 1, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(request));

        when(customerAddressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address(addressId, customerId)));
        SystemSettingsEntity settings = settings(
                """
                        [{"code":"REGIONAL","active":true}]
                        """,
                """
                        [{"id":"chilexpress","name":"Chilexpress","active":true}]
                        """,
                "POR_PAGAR"
        );
        when(systemSettingsRepository.findById((short) 1)).thenReturn(Optional.of(settings));

        IllegalArgumentException zoneEx = assertThrows(IllegalArgumentException.class, () -> service.create(request));
        assertEquals("Selected shipping zone is not available", zoneEx.getMessage());

        CreateOrderRequest courierRequest = baseRequest(customerId, "TRANSFER", "REGIONAL", "starken", addressId, List.of(item(UUID.randomUUID(), 1, null, null)));
        IllegalArgumentException courierEx = assertThrows(IllegalArgumentException.class, () -> service.create(courierRequest));
        assertEquals("Selected shipping courier is not available", courierEx.getMessage());
    }

    @Test
    void create_rejects_invalid_order_items_and_discounts() {
        OrderCommandService service = service();
        UUID customerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        CustomerAddressEntity address = address(addressId, customerId);
        when(customerAddressRepository.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.of(address));
        when(systemSettingsRepository.findById((short) 1)).thenReturn(Optional.empty());

        IllegalArgumentException variantEx = assertThrows(IllegalArgumentException.class, () ->
                service.create(baseRequest(customerId, "TRANSFER", "LOCAL", "starken", addressId,
                        List.of(item(UUID.randomUUID(), 1, "Negro", null)))));
        assertEquals("variantColor and variantSize must be provided together", variantEx.getMessage());

        IllegalArgumentException qtyEx = assertThrows(IllegalArgumentException.class, () ->
                service.create(baseRequest(customerId, "TRANSFER", "LOCAL", "starken", addressId,
                        List.of(item(UUID.randomUUID(), 0, null, null)))));
        assertEquals("Quantity must be greater than zero", qtyEx.getMessage());

        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () ->
                service.create(baseRequest(customerId, "TRANSFER", "LOCAL", "starken", addressId,
                        List.of(item(productId, 1, null, null)))));

        ProductEntity clp = product(productId, "Vestido", "CLP", "100.00");
        ProductEntity usd = product(UUID.randomUUID(), "Bag", "USD", "50.00");
        when(productRepository.findById(productId)).thenReturn(Optional.of(clp));
        when(productRepository.findById(usd.getId())).thenReturn(Optional.of(usd));
        IllegalArgumentException currencyEx = assertThrows(IllegalArgumentException.class, () ->
                service.create(baseRequest(customerId, "TRANSFER", "LOCAL", "starken", addressId,
                        List.of(item(productId, 1, null, null), item(usd.getId(), 1, null, null)))));
        assertEquals("All order items must share the same currency", currencyEx.getMessage());

        IllegalArgumentException negativeDiscountEx = assertThrows(IllegalArgumentException.class, () ->
                service.create(new CreateOrderRequest(
                        customerId,
                        List.of(item(productId, 1, null, null)),
                        "TRANSFER",
                        "LOCAL",
                        "starken",
                        addressId,
                        null,
                        new BigDecimal("-1"),
                        "CLP",
                        false,
                        null
                )));
        assertEquals("Discount amount cannot be negative", negativeDiscountEx.getMessage());
    }

    @Test
    void create_successfully_persists_order_and_payment_snapshot() {
        OrderCommandService service = service();
        UUID customerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(customerAddressRepository.findByIdAndCustomerId(addressId, customerId))
                .thenReturn(Optional.of(address(addressId, customerId)));
        SystemSettingsEntity settings = settings(
                """
                        [{"code":"LOCAL","active":true}]
                        """,
                """
                        [{"id":"starken","name":"Starken","active":true}]
                        """,
                "por_pagar"
        );
        ReflectionTestUtils.setField(settings, "bankTransferAccountHolder", "Pilar Estilo");
        ReflectionTestUtils.setField(settings, "bankTransferContactEmail", "admin@pilarestilo.com");
        ReflectionTestUtils.setField(settings, "bankTransferAccountNumber", "123456789");
        ReflectionTestUtils.setField(settings, "bankTransferBankName", "Banco de Chile");
        ReflectionTestUtils.setField(settings, "bankTransferAccountType", "Cuenta Corriente");
        when(systemSettingsRepository.findById((short) 1)).thenReturn(Optional.of(settings));

        ProductEntity product = product(productId, "Vestido", "CLP", "100.00");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByOrderId(any(UUID.class))).thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(item(productId, 2, null, null)),
                "TRANSFER",
                "LOCAL",
                "starken",
                addressId,
                "nota",
                new BigDecimal("10.00"),
                "CLP",
                true,
                null
        );

        OrderEntity result = service.create(request);

        assertNotNull(result.getId());
        assertEquals(customerId, result.getCustomerId());
        assertEquals("LOCAL", result.getShippingZoneCode());
        assertEquals("starken", result.getShippingCourierId());
        assertEquals("Starken", result.getShippingCourierName());
        assertEquals("POR_PAGAR", result.getShippingPaymentMode());
        assertEquals(addressId, result.getShippingAddressId());
        assertEquals(new BigDecimal("200.00"), result.getSubtotalAmount());
        assertEquals(new BigDecimal("30.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("170.00"), result.getTotalAmount());
        assertEquals(1, result.getItems().size());
        assertEquals("Vestido", result.getItems().get(0).getProductName());

        ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        PaymentEntity payment = paymentCaptor.getValue();
        assertEquals(result.getId(), payment.getOrderId());
        assertEquals("TRANSFER", payment.getMethod());
        assertEquals("Pilar Estilo", payment.getTransferAccountHolderName());
        assertEquals("admin@pilarestilo.com", payment.getTransferAccountEmail());
    }

    @Test
    void update_status_validates_transitions() {
        OrderCommandService service = service();
        UUID orderId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(null, "PAID"));
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(orderId, "CREATED"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.updateStatus(orderId, "PAID"));

        OrderEntity created = new OrderEntity();
        created.setId(orderId);
        created.setStatus("CREATED");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(created));
        assertThrows(IllegalStateException.class, () -> service.updateStatus(orderId, "SHIPPED"));

        OrderEntity paid = new OrderEntity();
        paid.setId(orderId);
        paid.setStatus("PAID");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(paid));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderEntity updated = service.updateStatus(orderId, "PREPARING_ORDER");
        assertEquals("PREPARING_ORDER", updated.getStatus());
        assertNotNull(updated.getUpdatedAt());
    }

    private OrderCommandService service() {
        return new OrderCommandService(
                orderRepository,
                productRepository,
                paymentRepository,
                systemSettingsRepository,
                inventoryCommandClient,
                customerAddressRepository
        );
    }

    private CreateOrderRequest baseRequest(UUID customerId,
                                           String paymentMethod,
                                           String shippingZoneCode,
                                           String shippingCourierId,
                                           UUID shippingAddressId,
                                           List<CreateOrderRequest.OrderItemRequest> items) {
        return new CreateOrderRequest(
                customerId,
                items,
                paymentMethod,
                shippingZoneCode,
                shippingCourierId,
                shippingAddressId,
                null,
                null,
                null,
                false,
                null
        );
    }

    private CreateOrderRequest.OrderItemRequest item(UUID productId, int quantity, String variantColor, String variantSize) {
        return new CreateOrderRequest.OrderItemRequest(productId, quantity, variantColor, variantSize);
    }

    private CustomerAddressEntity address(UUID addressId, UUID customerId) {
        CustomerAddressEntity address = new CustomerAddressEntity();
        ReflectionTestUtils.setField(address, "id", addressId);
        ReflectionTestUtils.setField(address, "customerId", customerId);
        ReflectionTestUtils.setField(address, "label", "Casa");
        ReflectionTestUtils.setField(address, "recipientName", "Pilar");
        ReflectionTestUtils.setField(address, "phone", "+56912345678");
        ReflectionTestUtils.setField(address, "line1", "Av Apoquindo 123");
        ReflectionTestUtils.setField(address, "line2", "Depto 22");
        ReflectionTestUtils.setField(address, "comuna", "Las Condes");
        ReflectionTestUtils.setField(address, "city", "Santiago");
        ReflectionTestUtils.setField(address, "region", "RM");
        ReflectionTestUtils.setField(address, "reference", "Porteria");
        return address;
    }

    private SystemSettingsEntity settings(String zonesJson, String couriersJson, String paymentMode) {
        SystemSettingsEntity settings = new SystemSettingsEntity();
        ReflectionTestUtils.setField(settings, "id", (short) 1);
        ReflectionTestUtils.setField(settings, "shippingZonesJson", zonesJson);
        ReflectionTestUtils.setField(settings, "shippingCouriersJson", couriersJson);
        ReflectionTestUtils.setField(settings, "shippingPaymentMode", paymentMode);
        return settings;
    }

    private ProductEntity product(UUID id, String name, String currency, String amount) {
        ProductEntity product = new ProductEntity();
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "name", name);
        ReflectionTestUtils.setField(product, "priceAmount", new BigDecimal(amount));
        ReflectionTestUtils.setField(product, "priceCurrency", currency);
        ReflectionTestUtils.setField(product, "stock", 10);
        return product;
    }
}
