package com.pilarestilo.dispatch.application;

import com.pilarestilo.dispatch.application.dto.DispatchDto;
import com.pilarestilo.dispatch.domain.model.Dispatch;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchOrderSummaryServiceTest {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private DispatchOrderSummaryService service;

    private final UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new DispatchOrderSummaryService(orderRepository, productRepository);
    }

    @Test
    @DisplayName("a row carries what somebody packing needs: reference, garment, picture and total")
    void summarisesTheOrder() {
        Order order = paidOrder("Negro", "M");
        givenOrders(order);
        givenProductImage("https://cdn/blazer.jpg");

        DispatchDto row = service.enrich(List.of(dispatchFor(order))).getFirst();

        assertThat(row.orderSummary()).isNotNull();
        assertThat(row.orderSummary().publicReference()).isEqualTo(order.getPublicReference());
        assertThat(row.orderSummary().firstItemName()).isEqualTo("Blazer");
        assertThat(row.orderSummary().firstItemVariant()).isEqualTo("Negro / M");
        assertThat(row.orderSummary().firstItemImageUrl()).isEqualTo("https://cdn/blazer.jpg");
        assertThat(row.orderSummary().itemCount()).isEqualTo(1);
        assertThat(row.orderSummary().totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(175000));
    }

    @Test
    @DisplayName("Base/UNICO placeholders are left out — they hide the rows where size matters")
    void omitsPlaceholderVariants() {
        Order order = paidOrder("Base", "UNICO");
        givenOrders(order);
        givenProductImage(null);

        DispatchDto row = service.enrich(List.of(dispatchFor(order))).getFirst();

        assertThat(row.orderSummary().firstItemVariant()).isNull();
    }

    @Test
    @DisplayName("half a variant is still worth showing")
    void keepsWhicheverHalfIsReal() {
        Order order = paidOrder("Base", "M");
        givenOrders(order);
        givenProductImage(null);

        assertThat(service.enrich(List.of(dispatchFor(order))).getFirst().orderSummary().firstItemVariant())
                .isEqualTo("M");
    }

    @Test
    @DisplayName("an order nobody paid for never reaches the queue somebody works")
    void excludesOrdersThatWereNeverPaidFor() {
        Order unpaid = orderWith(OrderStatus.CREATED, "Base", "UNICO");
        givenOrders(unpaid);
        givenProductImage(null);

        assertThat(service.enrichWorkable(List.of(dispatchFor(unpaid)))).isEmpty();
        // The history is a record, not a work list, so it still shows them.
        assertThat(service.enrich(List.of(dispatchFor(unpaid)))).hasSize(1);
    }

    @Test
    @DisplayName("a dispatch whose order was hard-deleted stays visible, just without a summary")
    void keepsRowsWhoseOrderIsGone() {
        when(orderRepository.findAllByIds(anyCollection())).thenReturn(List.of());

        List<DispatchDto> rows = service.enrich(List.of(dispatchFor(paidOrder("Negro", "M"))));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().orderSummary()).isNull();
    }

    @Test
    @DisplayName("the whole page costs one order query and one product query, not one per row")
    void readsInBulk() {
        Order first = paidOrder("Negro", "M");
        Order second = paidOrder("Crema", "L");
        when(orderRepository.findAllByIds(anyCollection())).thenReturn(List.of(first, second));
        givenProductImage(null);

        service.enrich(List.of(dispatchFor(first), dispatchFor(second)));

        verify(orderRepository, times(1)).findAllByIds(anyCollection());
        verify(productRepository, times(1)).findAllByIds(anyCollection());
    }

    @Test
    @DisplayName("an empty queue asks the database nothing")
    void emptyQueueQueriesNothing() {
        assertThat(service.enrich(List.of())).isEmpty();

        verify(orderRepository, times(0)).findAllByIds(anyCollection());
    }

    private void givenOrders(Order... orders) {
        when(orderRepository.findAllByIds(anyCollection())).thenReturn(List.of(orders));
    }

    private void givenProductImage(String imageUrl) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);
        when(product.getImageUrl()).thenReturn(imageUrl);
        when(productRepository.findAllByIds(anyCollection())).thenReturn(List.of(product));
    }

    private DispatchDto dispatchFor(Order order) {
        return DispatchDto.from(Dispatch.create(order.getId()));
    }

    private Order paidOrder(String colour, String size) {
        return orderWith(OrderStatus.PAID, colour, size);
    }

    private Order orderWith(OrderStatus status, String colour, String size) {
        OrderItem item = new OrderItem(UUID.randomUUID(), productId, "Blazer",
                Money.of(BigDecimal.valueOf(175000)), 1, colour, size);
        Order order = Order.create(
                UUID.randomUUID(),
                List.of(item),
                Money.zero(),
                PaymentMethod.TRANSFER,
                "NACIONAL",
                "chilexpress",
                "Chilexpress",
                "POR_PAGAR",
                UUID.randomUUID(),
                "Santa Angela 92",
                null
        );
        if (status == OrderStatus.PAID) {
            order.markAsPendingPayment();
            order.markAsPaid();
        }
        return order;
    }
}
