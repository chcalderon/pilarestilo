package com.pilarestilo.order.application.remote;

import com.pilarestilo.order.application.commands.CreateOrderCommand;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.order.domain.enums.OrderStatus;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
public class OrderRemoteCommandClient {

    private final boolean writeEnabled;
    private final RestClient restClient;

    public OrderRemoteCommandClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.order.remote.write-enabled:false}") boolean writeEnabled,
            @Value("${app.order.remote.base-url:http://order-service:8083}") String baseUrl,
            @Value("${app.order.remote.service-token:}") String serviceToken
    ) {
        this.writeEnabled = writeEnabled;
        RestClient.Builder builder = restClientBuilder.baseUrl(baseUrl);
        if (serviceToken != null && !serviceToken.isBlank()) {
            builder = builder.defaultHeader("X-Service-Token", serviceToken);
        }
        this.restClient = builder.build();
    }

    public boolean isWriteEnabled() {
        return writeEnabled;
    }

    public OrderDto create(CreateOrderCommand command) {
        try {
            return restClient.post()
                    .uri("/api/orders")
                    .body(toCreateRequest(command))
                    .retrieve()
                    .body(OrderDto.class);
        } catch (RestClientResponseException ex) {
            throw new DomainException("Could not create order in order-service (status " + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            throw new DomainException("Could not create order in order-service");
        }
    }

    public OrderDto updateStatus(UUID orderId, OrderStatus targetStatus) {
        try {
            return restClient.patch()
                    .uri("/api/orders/{id}/status", orderId)
                    .body(new UpdateOrderStatusRequest(targetStatus.name()))
                    .retrieve()
                    .body(OrderDto.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new DomainException("Order not found: " + orderId);
            }
            throw new DomainException("Could not update order status in order-service (status " + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            throw new DomainException("Could not update order status in order-service");
        }
    }

    private CreateOrderRequest toCreateRequest(CreateOrderCommand command) {
        List<CreateOrderRequest.OrderItemRequest> items = command.items().stream()
                .map(item -> new CreateOrderRequest.OrderItemRequest(item.productId(), item.quantity()))
                .toList();

        BigDecimal discountAmount = command.discountAmount() == null ? null : command.discountAmount().amount();
        String discountCurrency = command.discountAmount() == null ? null : command.discountAmount().currency();

        return new CreateOrderRequest(
                command.customerId(),
                items,
                command.paymentMethod().name(),
                command.notes(),
                discountAmount,
                discountCurrency,
                command.employeeDiscountEligible()
        );
    }

    private record CreateOrderRequest(
            UUID customerId,
            List<OrderItemRequest> items,
            String paymentMethod,
            String notes,
            BigDecimal discountAmount,
            String discountCurrency,
            boolean employeeDiscountEligible
    ) {
        private record OrderItemRequest(
                UUID productId,
                int quantity
        ) {
        }
    }

    private record UpdateOrderStatusRequest(
            String status
    ) {
    }
}
