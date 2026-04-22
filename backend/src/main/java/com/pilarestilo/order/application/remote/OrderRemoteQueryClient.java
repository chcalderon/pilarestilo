package com.pilarestilo.order.application.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pilarestilo.order.application.dto.OrderDto;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderRemoteQueryClient {

    private final boolean enabled;
    private final RestClient restClient;

    public OrderRemoteQueryClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.order.remote.enabled:false}") boolean enabled,
            @Value("${app.order.remote.base-url:http://order-service:8083}") String baseUrl,
            @Value("${app.order.remote.service-token:}") String serviceToken
    ) {
        this.enabled = enabled;
        RestClient.Builder builder = restClientBuilder.baseUrl(baseUrl);
        if (serviceToken != null && !serviceToken.isBlank()) {
            builder = builder.defaultHeader("X-Service-Token", serviceToken);
        }
        this.restClient = builder.build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<OrderDto> getById(UUID id) {
        try {
            return Optional.ofNullable(
                    restClient.get()
                            .uri("/api/orders/{id}", id)
                            .retrieve()
                            .body(OrderDto.class)
            );
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new DomainException("Could not fetch order from order-service (status " + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            throw new DomainException("Could not fetch order from order-service");
        }
    }

    public Page<OrderDto> list(UUID customerId, Pageable pageable) {
        try {
            OrderPageResponse response = restClient.get()
                    .uri(uriBuilder -> buildListUri(uriBuilder, customerId, pageable))
                    .retrieve()
                    .body(OrderPageResponse.class);
            if (response == null) {
                return Page.empty(pageable);
            }
            List<OrderDto> content = response.content() == null ? List.of() : response.content();
            return new PageImpl<>(content, pageable, response.totalElements());
        } catch (RestClientResponseException ex) {
            throw new DomainException("Could not list orders from order-service (status " + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            throw new DomainException("Could not list orders from order-service");
        }
    }

    private java.net.URI buildListUri(UriBuilder builder, UUID customerId, Pageable pageable) {
        builder.path("/api/orders")
                .queryParam("page", pageable.getPageNumber())
                .queryParam("size", pageable.getPageSize());

        if (customerId != null) {
            builder.queryParam("customerId", customerId);
        }

        for (var sort : pageable.getSort()) {
            builder.queryParam("sort", sort.getProperty() + "," + sort.getDirection().name().toLowerCase());
        }

        return builder.build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderPageResponse(
            List<OrderDto> content,
            long totalElements
    ) {
    }
}
