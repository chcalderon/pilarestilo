package com.pilarestilo.order.application.remote;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    /**
     * @param taxRate the shop's configured VAT rate. Only the rate crosses the wire: order-service
     *                computes the total itself, so sending a net and a tax derived from a total the
     *                monolith merely predicted would put three numbers on the row that need not add
     *                up. The rate is the input both sides agree on; the arithmetic is pinned on each
     *                side by the same test vectors.
     */
    public OrderDto create(CreateOrderCommand command, BigDecimal taxRate) {
        try {
            return restClient.post()
                    .uri("/api/orders")
                    .body(toCreateRequest(command, taxRate))
                    .retrieve()
                    .body(OrderDto.class);
        } catch (RestClientResponseException ex) {
            String detail = extractProblemDetail(ex.getResponseBodyAsString());
            if (detail != null && !detail.isBlank()) {
                throw new DomainException(detail);
            }
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
            String detail = extractProblemDetail(ex.getResponseBodyAsString());
            if (detail != null && !detail.isBlank()) {
                throw new DomainException(detail);
            }
            throw new DomainException("Could not update order status in order-service (status " + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            throw new DomainException("Could not update order status in order-service");
        }
    }

    private String extractProblemDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            for (String field : new String[]{"detail", "message", "error"}) {
                JsonNode node = root.get(field);
                if (node != null && !node.isNull() && !node.asString().isBlank()) {
                    return node.asString();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private CreateOrderRequest toCreateRequest(CreateOrderCommand command, BigDecimal taxRate) {
        List<CreateOrderRequest.OrderItemRequest> items = command.items().stream()
                .map(item -> new CreateOrderRequest.OrderItemRequest(
                        item.productId(),
                        item.quantity(),
                        item.variantColor(),
                        item.variantSize()
                ))
                .toList();

        BigDecimal discountAmount = command.discountAmount() == null ? null : command.discountAmount().amount();
        String discountCurrency = command.discountAmount() == null ? null : command.discountAmount().currency();

        return new CreateOrderRequest(
                command.customerId(),
                items,
                command.paymentMethod().name(),
                command.shippingZoneCode(),
                command.shippingCourierId(),
                command.shippingAddressId(),
                command.notes(),
                discountAmount,
                discountCurrency,
                command.employeeDiscountEligible(),
                command.resolvedDiscountId(),
                command.discountCode(),
                taxRate
        );
    }

    private record CreateOrderRequest(
            UUID customerId,
            List<OrderItemRequest> items,
            String paymentMethod,
            String shippingZoneCode,
            String shippingCourierId,
            UUID shippingAddressId,
            String notes,
            BigDecimal discountAmount,
            String discountCurrency,
            boolean employeeDiscountEligible,
            /* Provenance only. order-service stores these; the ledger stays in the monolith. */
            UUID discountId,
            String discountCode,
            /* order-service derives net and tax from this and the total it computes. */
            BigDecimal taxRate
    ) {
        private record OrderItemRequest(
                UUID productId,
                int quantity,
                String variantColor,
                String variantSize
        ) {
        }
    }

    private record UpdateOrderStatusRequest(
            String status
    ) {
    }
}
