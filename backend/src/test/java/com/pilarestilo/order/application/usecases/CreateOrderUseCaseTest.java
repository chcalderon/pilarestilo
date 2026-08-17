package com.pilarestilo.order.application.usecases;

import com.pilarestilo.customeraddress.application.CustomerAddressBookService;
import com.pilarestilo.customeraddress.domain.model.CustomerAddress;
import com.pilarestilo.discount.domain.enums.DiscountType;
import com.pilarestilo.discount.domain.model.Discount;
import com.pilarestilo.discount.application.DiscountRedemptionService;
import com.pilarestilo.inventory.application.InventoryService;
import com.pilarestilo.order.application.commands.CreateOrderCommand;
import com.pilarestilo.order.application.dto.MoneyDto;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.application.remote.OrderRemoteCommandClient;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.events.OrderCreated;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.product.domain.enums.ProductCondition;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock InventoryService inventoryService;
    @Mock DomainEventPublisher eventPublisher;
    @Mock OrderRemoteCommandClient orderRemoteCommandClient;
    @Mock SystemSettingsRepository systemSettingsRepository;
    @Mock DiscountRedemptionService discountRedemptionService;
    @Mock CustomerAddressBookService customerAddressBookService;

    CreateOrderUseCase useCase;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID  = UUID.randomUUID();
    private static final UUID ADDRESS_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new CreateOrderUseCase(
                orderRepository,
                productRepository,
                inventoryService,
                eventPublisher,
                orderRemoteCommandClient,
                systemSettingsRepository,
                discountRedemptionService,
                customerAddressBookService
        );
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    /** Settings with bank-transfer enabled + default couriers / zones. */
    private SystemSettings defaultSettings() {
        return SystemSettings.createDefault();
    }

    private Product productWithPrice(UUID productId, BigDecimal amount) {
        Product product = Product.create(
                "Test Product",
                "A test product",
                Money.of(amount),
                null,
                ProductCondition.USED,
                "TestBrand",
                10
        );
        product.setId(productId);
        return product;
    }

    private CustomerAddress defaultAddress() {
        return CustomerAddress.reconstruct(
                ADDRESS_ID,
                CUSTOMER_ID,
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
    }

    /** Wires up the standard stubs needed by the happy-path flow. */
    private void stubHappyPath(SystemSettings settings) {
        when(orderRemoteCommandClient.isWriteEnabled()).thenReturn(false);
        when(systemSettingsRepository.get()).thenReturn(settings);
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(productWithPrice(PRODUCT_ID, BigDecimal.valueOf(15_000))));
        when(customerAddressBookService.resolveOwnedAddress(CUSTOMER_ID, ADDRESS_ID))
                .thenReturn(defaultAddress());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateOrderCommand basicTransferCommand() {
        return new CreateOrderCommand(
                CUSTOMER_ID,
                List.of(new CreateOrderCommand.OrderItemCommand(PRODUCT_ID, 1, "Rojo", "M")),
                PaymentMethod.TRANSFER,
                "LOCAL",
                "starken",
                ADDRESS_ID,
                null,
                null,
                false,
                null
        );
    }

    // -----------------------------------------------------------------------
    // Test 1: happy path — reserves inventory and saves order
    // -----------------------------------------------------------------------

    @Test
    void createOrder_happyPath_reservesInventoryAndSavesOrder() {
        stubHappyPath(defaultSettings());

        OrderDto result = useCase.execute(basicTransferCommand());

        assertThat(result).isNotNull();

        verify(inventoryService).reserve(PRODUCT_ID, 1, "Rojo", "M");
        verify(orderRepository).save(any(Order.class));
    }

    // -----------------------------------------------------------------------
    // Test 2: discount code — applies discount, saves usage
    // -----------------------------------------------------------------------

    @Test
    void createOrder_withDiscountCode_appliesDiscount() {
        stubHappyPath(defaultSettings());

        Discount disc = Discount.create(
                "SAVE10",
                DiscountType.PERCENTAGE,
                BigDecimal.TEN,
                Money.zero(),
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(30),
                100
        );
        disc.setId(UUID.randomUUID());
        DiscountRedemptionService.DiscountEvaluation evaluation =
                new DiscountRedemptionService.DiscountEvaluation(disc, Money.of(new BigDecimal("1000.00")));
        when(discountRedemptionService.evaluate(eq("save10"), any(Money.class), eq(CUSTOMER_ID)))
                .thenReturn(evaluation);

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID,
                List.of(new CreateOrderCommand.OrderItemCommand(PRODUCT_ID, 1, "Rojo", "M")),
                PaymentMethod.TRANSFER,
                "LOCAL",
                "starken",
                ADDRESS_ID,
                null,
                null,
                false,
                "save10"   // lower-case to verify uppercase normalisation
        );

        OrderDto result = useCase.execute(command);

        assertThat(result).isNotNull();
        // Reserved against the saved order, not consumed outright: the order is CREATED and the
        // customer has not paid. Settling happens when it reaches PAID.
        verify(discountRedemptionService).reserve(eq(evaluation), eq(CUSTOMER_ID), any(UUID.class));
    }

    @Test
    void createOrder_rejectsCodeThatDoesNotBelongToTheCustomer() {
        // No stubHappyPath: this fails at discount evaluation, before the collaborators it stubs
        // are reached, and strict stubbing rightly objects to the unused ones.
        when(orderRemoteCommandClient.isWriteEnabled()).thenReturn(false);
        when(systemSettingsRepository.get()).thenReturn(defaultSettings());
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(productWithPrice(PRODUCT_ID, BigDecimal.valueOf(15_000))));
        when(customerAddressBookService.resolveOwnedAddress(CUSTOMER_ID, ADDRESS_ID))
                .thenReturn(defaultAddress());

        // The guard used to live only in ValidateDiscountForUserUseCase, so posting straight to
        // POST /api/orders redeemed a code assigned to somebody else.
        when(discountRedemptionService.evaluate(eq("AJENO"), any(Money.class), eq(CUSTOMER_ID)))
                .thenThrow(new DomainException("Este código no está disponible para tu cuenta"));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID,
                List.of(new CreateOrderCommand.OrderItemCommand(PRODUCT_ID, 1, "Rojo", "M")),
                PaymentMethod.TRANSFER,
                "LOCAL",
                "starken",
                ADDRESS_ID,
                null,
                null,
                false,
                "AJENO"
        );

        assertThrows(DomainException.class, () -> useCase.execute(command));

        // Evaluated before inventory is touched, so a rejected code leaves no stock reserved.
        verifyNoInteractions(inventoryService);
        verify(orderRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Test 3: employee discount — order discount > 0
    // -----------------------------------------------------------------------

    @Test
    void createOrder_withEmployeeDiscount_appliesEmployeeDiscount() {
        // Wire stubs manually so there is no duplicate save stub
        when(orderRemoteCommandClient.isWriteEnabled()).thenReturn(false);
        when(systemSettingsRepository.get()).thenReturn(defaultSettings());
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(productWithPrice(PRODUCT_ID, BigDecimal.valueOf(15_000))));
        when(customerAddressBookService.resolveOwnedAddress(CUSTOMER_ID, ADDRESS_ID))
                .thenReturn(defaultAddress());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        when(orderRepository.save(orderCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID,
                List.of(new CreateOrderCommand.OrderItemCommand(PRODUCT_ID, 1, "Rojo", "M")),
                PaymentMethod.TRANSFER,
                "LOCAL",
                "starken",
                ADDRESS_ID,
                null,
                null,
                true,   // employeeDiscountEligible
                null
        );

        useCase.execute(command);

        Order saved = orderCaptor.getValue();
        // Employee discount = 10% of 15 000 = 1 500
        assertThat(saved.getDiscountAmount().amount())
                .isGreaterThan(BigDecimal.ZERO);
    }

    // -----------------------------------------------------------------------
    // Test 4: publishes OrderCreated event exactly once
    // -----------------------------------------------------------------------

    @Test
    void createOrder_publishesOrderCreatedEvent() {
        stubHappyPath(defaultSettings());

        useCase.execute(basicTransferCommand());

        verify(eventPublisher, times(1)).publish(any(OrderCreated.class));
    }

    // -----------------------------------------------------------------------
    // Test 5: payment method disabled → DomainException
    // -----------------------------------------------------------------------

    @Test
    void createOrder_throwsDomainException_whenPaymentMethodDisabled() {
        // Spy on a real SystemSettings so we can override isPaymentMethodBankTransferEnabled
        SystemSettings settings = spy(SystemSettings.createDefault());
        doReturn(false).when(settings).isPaymentMethodBankTransferEnabled();

        // validatePaymentMethodEnabled is the FIRST call — throws before isWriteEnabled()
        when(systemSettingsRepository.get()).thenReturn(settings);

        assertThatThrownBy(() -> useCase.execute(basicTransferCommand()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("TRANSFER");
    }

    // -----------------------------------------------------------------------
    // Test 6: product not found → DomainException
    // -----------------------------------------------------------------------

    @Test
    void createOrder_throwsDomainException_whenProductNotFound() {
        when(orderRemoteCommandClient.isWriteEnabled()).thenReturn(false);
        when(systemSettingsRepository.get()).thenReturn(defaultSettings());
        when(customerAddressBookService.resolveOwnedAddress(CUSTOMER_ID, ADDRESS_ID))
                .thenReturn(defaultAddress());
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(basicTransferCommand()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Product not found");
    }

    // -----------------------------------------------------------------------
    // Test 7: inactive shipping zone → DomainException
    // -----------------------------------------------------------------------

    @Test
    void createOrder_throwsDomainException_whenShippingZoneNotActive() {
        // resolveShippingSelection is called BEFORE isWriteEnabled() — no need to stub the remote client
        when(systemSettingsRepository.get()).thenReturn(defaultSettings());

        CreateOrderCommand command = new CreateOrderCommand(
                CUSTOMER_ID,
                List.of(new CreateOrderCommand.OrderItemCommand(PRODUCT_ID, 1, "Rojo", "M")),
                PaymentMethod.TRANSFER,
                "INVALID_ZONE",   // not in the active zones JSON
                "starken",
                ADDRESS_ID,
                null,
                null,
                false,
                null
        );

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("shipping zone");
    }

    // ---------------------------------------------------------------------------------------
    // Delegated writes. order-service owns no redemption ledger, so the monolith keeps the whole
    // discount decision and sends only the resulting amount. These cases pin the ordering, which
    // is inverted relative to the local path and is the reason the mode works at all.
    // ---------------------------------------------------------------------------------------

    private OrderDto remoteOrderDto(UUID orderId) {
        MoneyDto money = new MoneyDto(BigDecimal.valueOf(15_000), "CLP");
        return new OrderDto(orderId, "PE-0123456789", CUSTOMER_ID, List.of(),
                money, new MoneyDto(BigDecimal.ZERO, "CLP"), money,
                new MoneyDto(BigDecimal.valueOf(12_605), "CLP"),
                new MoneyDto(BigDecimal.valueOf(2_395), "CLP"),
                new BigDecimal("19.00"),
                PaymentMethod.TRANSFER, "LOCAL", "starken", "Starken", "POR_PAGAR",
                ADDRESS_ID, "Casa", null, SalesChannel.ECOMMERCE,
                OrderStatus.CREATED, Instant.now(), Instant.now());
    }

    private DiscountRedemptionService.DiscountEvaluation evaluationWorth(BigDecimal amount) {
        Discount disc = Discount.create("SAVE10", DiscountType.PERCENTAGE, BigDecimal.TEN,
                Money.zero(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), 100);
        disc.setId(UUID.randomUUID());
        return new DiscountRedemptionService.DiscountEvaluation(disc, Money.of(amount));
    }

    /* resolveShippingSelection runs before the remote branch, so the address is always needed. */
    private void stubRemoteWrite() {
        when(orderRemoteCommandClient.isWriteEnabled()).thenReturn(true);
        when(systemSettingsRepository.get()).thenReturn(defaultSettings());
        when(customerAddressBookService.resolveOwnedAddress(CUSTOMER_ID, ADDRESS_ID))
                .thenReturn(defaultAddress());
    }

    private void stubRemoteWriteWithProduct() {
        stubRemoteWrite();
        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(productWithPrice(PRODUCT_ID, BigDecimal.valueOf(15_000))));
    }

    private CreateOrderCommand commandWithCode(String code) {
        return new CreateOrderCommand(
                CUSTOMER_ID,
                List.of(new CreateOrderCommand.OrderItemCommand(PRODUCT_ID, 1, "Rojo", "M")),
                PaymentMethod.TRANSFER,
                "LOCAL",
                "starken",
                ADDRESS_ID,
                null,
                null,
                false,
                code
        );
    }

    @Test
    void remoteWrite_reservesTheSlotBeforeCallingOrderService() {
        UUID orderId = UUID.randomUUID();
        UUID redemptionId = UUID.randomUUID();
        stubRemoteWriteWithProduct();
        var evaluation = evaluationWorth(new BigDecimal("1500.00"));
        when(discountRedemptionService.evaluate(eq("SAVE10"), any(Money.class), eq(CUSTOMER_ID)))
                .thenReturn(evaluation);
        when(discountRedemptionService.reserveWithoutOrder(evaluation, CUSTOMER_ID)).thenReturn(redemptionId);
        when(orderRemoteCommandClient.create(any(CreateOrderCommand.class), any())).thenReturn(remoteOrderDto(orderId));

        useCase.execute(commandWithCode("SAVE10"));

        // Claim, then create, then bind. Any other order leaves either an unbindable reservation
        // or an order in another service that would have to be cancelled.
        InOrder inOrder = inOrder(discountRedemptionService, orderRemoteCommandClient);
        inOrder.verify(discountRedemptionService).reserveWithoutOrder(evaluation, CUSTOMER_ID);
        inOrder.verify(orderRemoteCommandClient).create(any(CreateOrderCommand.class), any());
        inOrder.verify(discountRedemptionService).attachOrder(redemptionId, orderId);
    }

    @Test
    void remoteWrite_sendsTheComputedAmountAndNeverTheCode() {
        stubRemoteWriteWithProduct();
        when(discountRedemptionService.evaluate(eq("SAVE10"), any(Money.class), eq(CUSTOMER_ID)))
                .thenReturn(evaluationWorth(new BigDecimal("1500.00")));
        when(discountRedemptionService.reserveWithoutOrder(any(), any())).thenReturn(UUID.randomUUID());
        when(orderRemoteCommandClient.create(any(CreateOrderCommand.class), any()))
                .thenReturn(remoteOrderDto(UUID.randomUUID()));

        useCase.execute(commandWithCode("SAVE10"));

        ArgumentCaptor<CreateOrderCommand> sent = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderRemoteCommandClient).create(sent.capture(), any());
        assertThat(sent.getValue().discountAmount().amount()).isEqualByComparingTo("1500.00");
    }

    /** A code the customer may not use must fail before order-service is ever contacted. */
    @Test
    void remoteWrite_rejectsAnInvalidCodeWithoutCreatingAnything() {
        stubRemoteWriteWithProduct();
        when(discountRedemptionService.evaluate(eq("SAVE10"), any(Money.class), eq(CUSTOMER_ID)))
                .thenThrow(new DomainException("Codigo ya utilizado"));

        assertThrows(DomainException.class, () -> useCase.execute(commandWithCode("SAVE10")));

        verify(orderRemoteCommandClient, never()).create(any(), any());
        verify(discountRedemptionService, never()).reserveWithoutOrder(any(), any());
    }

    /** Losing the capacity race must also happen before anything is created remotely. */
    @Test
    void remoteWrite_doesNotCallOrderServiceWhenTheSlotIsGone() {
        stubRemoteWriteWithProduct();
        var evaluation = evaluationWorth(new BigDecimal("1500.00"));
        when(discountRedemptionService.evaluate(eq("SAVE10"), any(Money.class), eq(CUSTOMER_ID)))
                .thenReturn(evaluation);
        when(discountRedemptionService.reserveWithoutOrder(evaluation, CUSTOMER_ID))
                .thenThrow(new DomainException("Discount usage limit reached"));

        assertThrows(DomainException.class, () -> useCase.execute(commandWithCode("SAVE10")));

        verify(orderRemoteCommandClient, never()).create(any(), any());
    }

    @Test
    void remoteWrite_withoutACodeTouchesTheLedgerNotAtAll() {
        stubRemoteWrite();
        when(orderRemoteCommandClient.create(any(CreateOrderCommand.class), any()))
                .thenReturn(remoteOrderDto(UUID.randomUUID()));

        useCase.execute(basicTransferCommand());

        verify(discountRedemptionService, never()).evaluate(any(), any(), any());
        verify(discountRedemptionService, never()).reserveWithoutOrder(any(), any());
        verify(discountRedemptionService, never()).attachOrder(any(), any());
    }

    // ---------------------------------------------------------------------------------------
    // Discount provenance. orders.discount_id / discount_code exist so that hard-deleting a code
    // cannot erase which orders used it — DeleteDiscountUseCase deletes for real and the ledger's
    // FK is ON DELETE SET NULL. Both write paths must fill them or the columns are decoration.
    // ---------------------------------------------------------------------------------------

    @Test
    void localWrite_storesWhichCodeProducedTheDiscount() {
        stubHappyPath(defaultSettings());
        var evaluation = evaluationWorth(new BigDecimal("1500.00"));
        when(discountRedemptionService.evaluate(eq("SAVE10"), any(Money.class), eq(CUSTOMER_ID)))
                .thenReturn(evaluation);

        useCase.execute(commandWithCode("SAVE10"));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getDiscountId()).isEqualTo(evaluation.discountId());
        assertThat(saved.getValue().getDiscountCode()).isEqualTo("SAVE10");
    }

    @Test
    void localWrite_leavesProvenanceEmptyWithoutACode() {
        stubHappyPath(defaultSettings());

        useCase.execute(basicTransferCommand());

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getDiscountId()).isNull();
        assertThat(saved.getValue().getDiscountCode()).isNull();
    }

    /** order-service cannot look the code up, so the monolith has to send it along. */
    @Test
    void remoteWrite_forwardsTheProvenanceToOrderService() {
        stubRemoteWriteWithProduct();
        var evaluation = evaluationWorth(new BigDecimal("1500.00"));
        when(discountRedemptionService.evaluate(eq("SAVE10"), any(Money.class), eq(CUSTOMER_ID)))
                .thenReturn(evaluation);
        when(discountRedemptionService.reserveWithoutOrder(any(), any())).thenReturn(UUID.randomUUID());
        when(orderRemoteCommandClient.create(any(CreateOrderCommand.class), any()))
                .thenReturn(remoteOrderDto(UUID.randomUUID()));

        useCase.execute(commandWithCode("SAVE10"));

        ArgumentCaptor<CreateOrderCommand> sent = ArgumentCaptor.forClass(CreateOrderCommand.class);
        verify(orderRemoteCommandClient).create(sent.capture(), any());
        assertThat(sent.getValue().resolvedDiscountId()).isEqualTo(evaluation.discountId());
        assertThat(sent.getValue().discountCode()).isEqualTo("SAVE10");
    }
}
