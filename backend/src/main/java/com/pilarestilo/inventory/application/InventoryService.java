package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.domain.enums.InventoryMovementType;
import com.pilarestilo.inventory.domain.events.StockUpdated;
import com.pilarestilo.inventory.domain.model.InventoryMovement;
import com.pilarestilo.inventory.domain.ports.InventoryMovementRepository;
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
    private final InventoryMovementRepository inventoryMovementRepository;
    private final boolean remoteWriteEnabled;
    private final RestClient remoteInventoryClient;

    public InventoryService(ProductRepository productRepository,
                            DomainEventPublisher eventPublisher,
                            InventoryMovementRepository inventoryMovementRepository,
                            RestClient.Builder restClientBuilder,
                            @Value("${app.inventory.remote.enabled:false}") boolean remoteWriteEnabled,
                            @Value("${app.inventory.remote.base-url:http://inventory-service:8082}") String remoteInventoryBaseUrl) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.remoteWriteEnabled = remoteWriteEnabled;
        this.remoteInventoryClient = restClientBuilder
                .baseUrl(remoteInventoryBaseUrl)
                .build();
    }

    @Transactional
    public void reserve(UUID productId, int qty) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/reserve", productId, qty, null, null, "reserve");
            return;
        }
        reserveLocal(productId, qty, null, null);
    }

    @Transactional
    public void reserve(UUID productId, int qty, String variantColor, String variantSize) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/reserve", productId, qty, variantColor, variantSize, "reserve");
            return;
        }
        reserveLocal(productId, qty, variantColor, variantSize);
    }

    @Transactional
    public void release(UUID productId, int qty) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/release", productId, qty, null, null, "release");
            return;
        }
        releaseLocal(productId, qty, null, null);
    }

    @Transactional
    public void release(UUID productId, int qty, String variantColor, String variantSize) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/release", productId, qty, variantColor, variantSize, "release");
            return;
        }
        releaseLocal(productId, qty, variantColor, variantSize);
    }

    /**
     * Confirms a previously reserved quantity — converts reservation into a real sale.
     * For variant products: atomically decrements both stock_on_hand and stock_reserved.
     * For non-variant products: decrements the legacy aggregate stock field.
     */
    @Transactional
    public void confirm(UUID productId, int qty) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/confirm", productId, qty, null, null, "confirm");
            return;
        }
        confirmLocal(productId, qty, null, null);
    }

    @Transactional
    public void confirm(UUID productId, int qty, String variantColor, String variantSize) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/confirm", productId, qty, variantColor, variantSize, "confirm");
            return;
        }
        confirmLocal(productId, qty, variantColor, variantSize);
    }

    /**
     * POS direct sale: decrements stock without a prior reservation step.
     * For variant products: atomically decrements both stock_on_hand and stock_reserved
     * (stock_reserved is 0 for POS sales so the constraint check uses stock_reserved >= 0).
     * For non-variant products: decrements the legacy aggregate stock field.
     */
    @Transactional
    public void posSale(UUID productId, int qty, String variantColor, String variantSize) {
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.atomicConfirmVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new DomainException("Stock insuficiente para venta POS de variante: " + variantColor + " / " + variantSize);
            }
            recordMovement(productId, variantColor, variantSize, InventoryMovementType.POS_SALE, -qty);
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException("Product not found: " + productId));
        product.decrementStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
        recordMovement(productId, null, null, InventoryMovementType.POS_SALE, -qty);
    }

    private void reserveLocal(UUID productId, int qty, String variantColor, String variantSize) {
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.atomicReserveVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new DomainException("Stock insuficiente para variante: " + variantColor + " / " + variantSize);
            }
            recordMovement(productId, variantColor, variantSize, InventoryMovementType.RESERVE, qty);
            return;
        }
        // Legacy path: aggregate stock for products without variants
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException("Product not found: " + productId));
        product.decrementStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
        recordMovement(productId, null, null, InventoryMovementType.RESERVE, qty);
    }

    private void releaseLocal(UUID productId, int qty, String variantColor, String variantSize) {
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.atomicReleaseVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new DomainException("Variante no encontrada para release: " + variantColor + " / " + variantSize);
            }
            recordMovement(productId, variantColor, variantSize, InventoryMovementType.RELEASE, -qty);
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException("Product not found: " + productId));
        product.releaseStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
        recordMovement(productId, null, null, InventoryMovementType.RELEASE, -qty);
    }

    private void confirmLocal(UUID productId, int qty, String variantColor, String variantSize) {
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.atomicConfirmVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new DomainException("Stock reservado insuficiente para confirmar variante: " + variantColor + " / " + variantSize);
            }
            recordMovement(productId, variantColor, variantSize, InventoryMovementType.CONFIRM, -qty);
            return;
        }
        // Non-variant products: stock was already decremented at reserve time (legacy aggregate model).
        // No second decrement here — confirm is a no-op for the legacy path until products are
        // migrated to the on_hand/reserved model. Guard ensures the product exists before recording.
        productRepository.findById(productId)
                .orElseThrow(() -> new DomainException("Product not found: " + productId));
        recordMovement(productId, null, null, InventoryMovementType.CONFIRM, -qty);
    }

    private void recordMovement(UUID productId, String variantColor, String variantSize,
                                 InventoryMovementType type, int quantity) {
        inventoryMovementRepository.save(InventoryMovement.record(
                productId, variantColor, variantSize, type, quantity,
                null, null, null, null
        ));
    }

    private void invokeRemoteCommand(String path,
                                     UUID productId,
                                     int qty,
                                     String variantColor,
                                     String variantSize,
                                     String operation) {
        try {
            remoteInventoryClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InventoryCommandRequest(productId, qty, variantColor, variantSize))
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
            int qty,
            String variantColor,
            String variantSize
    ) {
    }
}
