package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.domain.events.StockUpdated;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.ports.ProductRepository;
import com.pilarestilo.shared.domain.DomainEventPublisher;
import com.pilarestilo.shared.domain.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.UUID;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;
    private final boolean remoteWriteEnabled;
    private final RestClient remoteInventoryClient;

    public InventoryService(ProductRepository productRepository,
                            DomainEventPublisher eventPublisher,
                            RestClient.Builder restClientBuilder,
                            @Value("${app.inventory.remote.enabled:false}") boolean remoteWriteEnabled,
                            @Value("${app.inventory.remote.base-url:http://inventory-service:8082}") String remoteInventoryBaseUrl) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.remoteWriteEnabled = remoteWriteEnabled;
        this.remoteInventoryClient = restClientBuilder
                .baseUrl(remoteInventoryBaseUrl)
                .build();
    }

    @Transactional
    public void reserve(UUID productId, int qty) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/reserve", productId, qty, "reserve");
            return;
        }
        reserveLocal(productId, qty);
    }

    @Transactional
    public void release(UUID productId, int qty) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/release", productId, qty, "release");
            return;
        }
        releaseLocal(productId, qty);
    }

    /**
     * No-op for v1: stock was already reserved (decremented) on order creation.
     * In saga mode this command exists to keep the forward-compatible contract.
     */
    public void confirm(UUID productId, int qty) {
        if (!remoteWriteEnabled) {
            return;
        }
        invokeRemoteCommand("/api/inventory/commands/confirm", productId, qty, "confirm");
    }

    private void reserveLocal(UUID productId, int qty) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException("Product not found: " + productId));
        product.decrementStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
    }

    private void releaseLocal(UUID productId, int qty) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException("Product not found: " + productId));
        product.releaseStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
    }

    private void invokeRemoteCommand(String path, UUID productId, int qty, String operation) {
        try {
            remoteInventoryClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InventoryCommandRequest(productId, qty))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new DomainException("Product not found: " + productId);
            }
            throw new DomainException(
                    "Inventory service rejected " + operation + " for product: " + productId + " (status " + ex.getStatusCode().value() + ")"
            );
        } catch (Exception ex) {
            throw new DomainException("Could not " + operation + " stock via inventory-service");
        }
    }

    private record InventoryCommandRequest(
            UUID productId,
            int qty
    ) {
    }
}
