package com.pilarestilo.order.infrastructure.persistence.repositories;

import com.pilarestilo.order.domain.enums.DeliveryMethod;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.order.domain.enums.PaymentMethod;
import com.pilarestilo.order.domain.enums.SalesChannel;
import com.pilarestilo.order.domain.model.Order;
import com.pilarestilo.order.domain.model.OrderItem;
import com.pilarestilo.order.domain.ports.OrderRepository;
import com.pilarestilo.shared.application.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class OrderRepositoryExternalSaleIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("pilarestilo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    OrderRepository orderRepository;

    private Order externalSale(String idempotencyKey, DeliveryMethod delivery, String address) {
        Order order = Order.createExternalSale(
                "Javiera", "+56911112222",
                List.of(new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Vestido",
                        Money.of(new BigDecimal("19990")), 1, "Rojo", "M")),
                PaymentMethod.TRANSFER, delivery, address, "por IG",
                SalesChannel.INSTAGRAM, new BigDecimal("19.00"), idempotencyKey);
        order.markAsPendingPayment();
        order.markAsPaid();
        return order;
    }

    @Test
    void round_trips_an_external_sale_with_no_customer() {
        Order saved = orderRepository.save(externalSale("idem-" + UUID.randomUUID(), DeliveryMethod.PICKUP, null));
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getCustomerId()).isNull();
        assertThat(reloaded.getBuyerName()).isEqualTo("Javiera");
        assertThat(reloaded.getBuyerContact()).isEqualTo("+56911112222");
        assertThat(reloaded.getDeliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(reloaded.getShippingAddressReference()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void finds_by_external_idempotency_key() {
        String key = "idem-" + UUID.randomUUID();
        orderRepository.save(externalSale(key, DeliveryMethod.SHIPPING, "Av. Siempre Viva 742"));

        assertThat(orderRepository.findByExternalIdempotencyKey(key)).isPresent();
        assertThat(orderRepository.findByExternalIdempotencyKey("nope-" + UUID.randomUUID())).isEmpty();
    }
}
