package com.pilarestilo.orderservice.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class InventoryCommandClient {

    private final RestClient restClient;

    public InventoryCommandClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.inventory.remote.base-url:http://inventory-service:8082}") String inventoryBaseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(inventoryBaseUrl)
                .build();
    }

    /**
     * @param orderId the order this reservation belongs to, written on the ledger line by
     *                inventory-service. Production reserves here and nowhere else — the monolith
     *                hands the whole creation over — so a reservation sent without it is a stock
     *                movement nobody can trace back to a sale.
     */
    public void reserve(UUID productId, int qty, String variantColor, String variantSize, UUID orderId) {
        try {
            restClient.post()
                    .uri("/api/inventory/commands/reserve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InventoryCommandRequest(productId, qty, variantColor, variantSize,
                            "ORDER", orderId, null))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404) {
                throw new IllegalStateException("Product not found: " + productId);
            }
            throw new IllegalStateException("Inventory reservation rejected (status " + status + ")");
        } catch (Exception ex) {
            throw new IllegalStateException("Could not reserve stock via inventory-service");
        }
    }

    /** Mirrors the request record in inventory-service, cause fields included. */
    private record InventoryCommandRequest(
            UUID productId,
            int qty,
            String variantColor,
            String variantSize,
            String referenceType,
            UUID referenceId,
            UUID recordedBy
    ) {
    }
}

