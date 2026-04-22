package com.pilarestilo.payment.application.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pilarestilo.payment.application.dto.PaymentDto;
import com.pilarestilo.payment.domain.enums.PaymentStatus;
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
public class PaymentRemoteQueryClient {

    private final boolean enabled;
    private final RestClient restClient;

    public PaymentRemoteQueryClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.payment.remote.enabled:false}") boolean enabled,
            @Value("${app.payment.remote.base-url:http://payment-service:8084}") String baseUrl,
            @Value("${app.payment.remote.service-token:}") String serviceToken
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

    public Optional<PaymentDto> getById(UUID id) {
        try {
            return Optional.ofNullable(
                    restClient.get()
                            .uri("/api/payments/{id}", id)
                            .retrieve()
                            .body(PaymentDto.class)
            );
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new DomainException("Could not fetch payment from payment-service (status " + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            throw new DomainException("Could not fetch payment from payment-service");
        }
    }

    public Optional<PaymentDto> getByOrderId(UUID orderId) {
        try {
            return Optional.ofNullable(
                    restClient.get()
                            .uri("/api/payments/order/{orderId}", orderId)
                            .retrieve()
                            .body(PaymentDto.class)
            );
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new DomainException("Could not fetch payment by order from payment-service (status " + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            throw new DomainException("Could not fetch payment by order from payment-service");
        }
    }

    public Page<PaymentDto> list(PaymentStatus status, Pageable pageable) {
        try {
            PaymentPageResponse response = restClient.get()
                    .uri(uriBuilder -> buildListUri(uriBuilder, status, pageable))
                    .retrieve()
                    .body(PaymentPageResponse.class);
            if (response == null) {
                return Page.empty(pageable);
            }
            List<PaymentDto> content = response.content() == null ? List.of() : response.content();
            return new PageImpl<>(content, pageable, response.totalElements());
        } catch (RestClientResponseException ex) {
            throw new DomainException("Could not list payments from payment-service (status " + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            throw new DomainException("Could not list payments from payment-service");
        }
    }

    private java.net.URI buildListUri(UriBuilder builder, PaymentStatus status, Pageable pageable) {
        builder.path("/api/payments")
                .queryParam("page", pageable.getPageNumber())
                .queryParam("size", pageable.getPageSize());

        if (status != null) {
            builder.queryParam("status", status.name());
        }

        for (var sort : pageable.getSort()) {
            builder.queryParam("sort", sort.getProperty() + "," + sort.getDirection().name().toLowerCase());
        }

        return builder.build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PaymentPageResponse(
            List<PaymentDto> content,
            long totalElements
    ) {
    }
}
