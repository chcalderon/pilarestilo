package com.pilarestilo.order.domain;

import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderExternalSaleTest {

    private OrderItem line(String name, long price, int qty) {
        return new OrderItem(UUID.randomUUID(), UUID.randomUUID(), name,
                Money.of(BigDecimal.valueOf(price)), qty, null, null);
    }

    @Test
    void createExternalSale_shipping_snapshots_the_buyer_and_address_and_leaves_customer_null() {
        Order order = Order.createExternalSale(
                "Javiera Rojas", "+56 9 1111 2222",
                List.of(line("Vestido", 19990, 2)),
                PaymentMethod.TRANSFER, DeliveryMethod.SHIPPING,
                "Av. Siempre Viva 742, Providencia, RM",
                "por IG", SalesChannel.INSTAGRAM, new BigDecimal("19.00"), "idem-1");

        assertThat(order.getCustomerId()).isNull();
        assertThat(order.getBuyerName()).isEqualTo("Javiera Rojas");
        assertThat(order.getBuyerContact()).isEqualTo("+56 9 1111 2222");
        assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.SHIPPING);
        assertThat(order.getShippingAddressReference()).isEqualTo("Av. Siempre Viva 742, Providencia, RM");
        assertThat(order.getSalesChannel()).isEqualTo(SalesChannel.INSTAGRAM);
        assertThat(order.getExternalIdempotencyKey()).isEqualTo("idem-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount().amount()).isEqualByComparingTo("39980");
    }

    @Test
    void createExternalSale_pickup_has_no_address() {
        Order order = Order.createExternalSale(
                "Ana", "@ana", List.of(line("Aros", 8000, 1)),
                PaymentMethod.OTHER, DeliveryMethod.PICKUP,
                null, null, SalesChannel.WHATSAPP, new BigDecimal("19.00"), "idem-2");

        assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(order.getShippingAddressReference()).isNull();
    }

    @Test
    void createExternalSale_rejects_blank_buyer_name() {
        assertThatThrownBy(() -> Order.createExternalSale(
                "  ", "@x", List.of(line("Aros", 8000, 1)),
                PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                SalesChannel.WHATSAPP, new BigDecimal("19.00"), "k"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void createExternalSale_rejects_shipping_without_an_address() {
        assertThatThrownBy(() -> Order.createExternalSale(
                "Ana", "@ana", List.of(line("Aros", 8000, 1)),
                PaymentMethod.OTHER, DeliveryMethod.SHIPPING, "   ", null,
                SalesChannel.WHATSAPP, new BigDecimal("19.00"), "k"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void createExternalSale_rejects_empty_items() {
        assertThatThrownBy(() -> Order.createExternalSale(
                "Ana", "@ana", List.of(),
                PaymentMethod.OTHER, DeliveryMethod.PICKUP, null, null,
                SalesChannel.WHATSAPP, new BigDecimal("19.00"), "k"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void regular_create_still_works_and_defaults_delivery_to_shipping() {
        Order order = Order.create(
                UUID.randomUUID(), List.of(line("Vestido", 19990, 1)), Money.zero(),
                PaymentMethod.TRANSFER, "RM", "starken", "Starken", "PREPAID",
                UUID.randomUUID(), "Depto 1", "web", SalesChannel.ECOMMERCE,
                new BigDecimal("19.00"), DeliveryMethod.SHIPPING);
        assertThat(order.getDeliveryMethod()).isEqualTo(DeliveryMethod.SHIPPING);
    }
}
