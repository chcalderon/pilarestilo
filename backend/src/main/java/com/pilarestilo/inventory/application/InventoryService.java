package com.pilarestilo.inventory.application;

import com.pilarestilo.inventory.domain.enums.InventoryMovementType;
import com.pilarestilo.inventory.domain.events.StockUpdated;
import com.pilarestilo.inventory.domain.model.InventoryMovement;
import com.pilarestilo.inventory.domain.model.StockMovementOrigin;
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

    private static final String PRODUCT_NOT_FOUND_PREFIX = "Product not found: ";

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
    public void reserve(UUID productId, int qty, StockMovementOrigin origin) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/reserve", productId, qty, null, null, origin, "reserve");
            return;
        }
        reserveLocal(productId, qty, null, null, origin);
    }

    @Transactional
    public void reserve(UUID productId, int qty, String variantColor, String variantSize,
                        StockMovementOrigin origin) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/reserve", productId, qty, variantColor, variantSize, origin, "reserve");
            return;
        }
        reserveLocal(productId, qty, variantColor, variantSize, origin);
    }

    @Transactional
    public void release(UUID productId, int qty, StockMovementOrigin origin) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/release", productId, qty, null, null, origin, "release");
            return;
        }
        releaseLocal(productId, qty, null, null, origin);
    }

    @Transactional
    public void release(UUID productId, int qty, String variantColor, String variantSize,
                        StockMovementOrigin origin) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/release", productId, qty, variantColor, variantSize, origin, "release");
            return;
        }
        releaseLocal(productId, qty, variantColor, variantSize, origin);
    }

    /**
     * Confirms a previously reserved quantity — converts reservation into a real sale.
     * For variant products: atomically decrements both stock_on_hand and stock_reserved.
     * For non-variant products: decrements the legacy aggregate stock field.
     */
    @Transactional
    public void confirm(UUID productId, int qty, StockMovementOrigin origin) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/confirm", productId, qty, null, null, origin, "confirm");
            return;
        }
        confirmLocal(productId, qty, null, null, origin);
    }

    @Transactional
    public void confirm(UUID productId, int qty, String variantColor, String variantSize,
                        StockMovementOrigin origin) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/confirm", productId, qty, variantColor, variantSize, origin, "confirm");
            return;
        }
        confirmLocal(productId, qty, variantColor, variantSize, origin);
    }

    /**
     * Puts confirmed units back on the shelf when a paid sale is undone.
     *
     * <p>The inverse of {@link #confirm}, not of {@link #reserve}. By the time a sale is cancelled
     * after payment the reservation is long gone: {@code confirm} took the units out of both
     * {@code stock_on_hand} and {@code stock_reserved}. Releasing here would decrement a
     * reservation that no longer exists and leave the shelf short.
     *
     * <p>Cancelling a paid order used to do nothing at all, so the units were simply lost.
     */
    @Transactional
    public void returnToStock(UUID productId, int qty, String variantColor, String variantSize,
                              StockMovementOrigin origin) {
        if (remoteWriteEnabled) {
            invokeRemoteCommand("/api/inventory/commands/return", productId, qty, variantColor, variantSize, origin, "return");
            return;
        }
        returnToStockLocal(productId, qty, variantColor, variantSize, origin);
    }

    /**
     * POS direct sale: decrements stock without a prior reservation step.
     * For variant products: atomically decrements both stock_on_hand and stock_reserved
     * (stock_reserved is 0 for POS sales so the constraint check uses stock_reserved >= 0).
     * For non-variant products: decrements the legacy aggregate stock field.
     */
    @Transactional
    public void posSale(UUID productId, int qty, String variantColor, String variantSize,
                        StockMovementOrigin origin) {
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.atomicConfirmVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new DomainException("Stock insuficiente para venta POS de variante: " + variantColor + " / " + variantSize);
            }
            recordMovement(productId, variantColor, variantSize, InventoryMovementType.POS_SALE, -qty, origin);
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException(PRODUCT_NOT_FOUND_PREFIX + productId));
        product.decrementStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
        recordMovement(productId, null, null, InventoryMovementType.POS_SALE, -qty, origin);
    }

    private void reserveLocal(UUID productId, int qty, String variantColor, String variantSize,
                        StockMovementOrigin origin) {
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.atomicReserveVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new DomainException("Stock insuficiente para variante: " + variantColor + " / " + variantSize);
            }
            productRepository.syncProductStockFromVariants(productId);
            recordMovement(productId, variantColor, variantSize, InventoryMovementType.RESERVE, qty, origin);
            return;
        }
        // Legacy path: aggregate stock for products without variants
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException(PRODUCT_NOT_FOUND_PREFIX + productId));
        product.decrementStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
        recordMovement(productId, null, null, InventoryMovementType.RESERVE, qty, origin);
    }

    private void releaseLocal(UUID productId, int qty, String variantColor, String variantSize,
                        StockMovementOrigin origin) {
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.atomicReleaseVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new DomainException("Variante no encontrada para release: " + variantColor + " / " + variantSize);
            }
            productRepository.syncProductStockFromVariants(productId);
            recordMovement(productId, variantColor, variantSize, InventoryMovementType.RELEASE, -qty, origin);
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException(PRODUCT_NOT_FOUND_PREFIX + productId));
        product.releaseStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
        recordMovement(productId, null, null, InventoryMovementType.RELEASE, -qty, origin);
    }

    private void confirmLocal(UUID productId, int qty, String variantColor, String variantSize,
                        StockMovementOrigin origin) {
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.atomicConfirmVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new DomainException("Stock reservado insuficiente para confirmar variante: " + variantColor + " / " + variantSize);
            }
            productRepository.syncProductStockFromVariants(productId);
            recordMovement(productId, variantColor, variantSize, InventoryMovementType.CONFIRM, -qty, origin);
            return;
        }
        // Non-variant products: stock was already decremented at reserve time (legacy aggregate model).
        // No second decrement here — confirm is a no-op for the legacy path until products are
        // migrated to the on_hand/reserved model. Guard ensures the product exists before recording.
        productRepository.findById(productId)
                .orElseThrow(() -> new DomainException(PRODUCT_NOT_FOUND_PREFIX + productId));
        recordMovement(productId, null, null, InventoryMovementType.CONFIRM, -qty, origin);
    }

    private void returnToStockLocal(UUID productId, int qty, String variantColor, String variantSize,
                        StockMovementOrigin origin) {
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.atomicReturnVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new DomainException("Variante no encontrada para devolucion: " + variantColor + " / " + variantSize);
            }
            productRepository.syncProductStockFromVariants(productId);
            recordMovement(productId, variantColor, variantSize, InventoryMovementType.RETURN, qty, origin);
            return;
        }
        // Legacy aggregate: reserve was the only decrement and confirm is a no-op there, so putting
        // units back is the same single operation release performs. Reused rather than duplicated —
        // a second method adding to the same field is a second thing to keep correct.
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new DomainException(PRODUCT_NOT_FOUND_PREFIX + productId));
        product.releaseStock(qty);
        productRepository.save(product);
        eventPublisher.publish(new StockUpdated(productId, product.getStock(), Instant.now()));
        recordMovement(productId, null, null, InventoryMovementType.RETURN, qty, origin);
    }

    /** One line of the ledger, and what caused it. */
    private void recordMovement(UUID productId, String variantColor, String variantSize,
                                 InventoryMovementType type, int quantity,
                                 StockMovementOrigin origin) {
        StockMovementOrigin cause = origin == null ? StockMovementOrigin.unknown() : origin;
        inventoryMovementRepository.save(InventoryMovement.create(
                productId, variantColor, variantSize, type, quantity,
                cause.referenceType(), cause.referenceId(), cause.recordedBy(), null
        ));
    }

    private void invokeRemoteCommand(String path,
                                     UUID productId,
                                     int qty,
                                     String variantColor,
                                     String variantSize,
                                     StockMovementOrigin origin,
                                     String operation) {
        StockMovementOrigin cause = origin == null ? StockMovementOrigin.unknown() : origin;
        try {
            remoteInventoryClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InventoryCommandRequest(productId, qty, variantColor, variantSize,
                            cause.referenceType(), cause.referenceId(), cause.recordedBy()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new DomainException(PRODUCT_NOT_FOUND_PREFIX + productId);
            }
            throw new DomainException(
                    "Inventory service rejected " + operation + " for product: " + productId + " (status " + ex.getStatusCode().value() + ")"
            );
        } catch (Exception _) {
            throw new DomainException("Could not " + operation + " stock via inventory-service");
        }
    }

    /**
     * Mirrors {@code InventoryCommandRequest} in inventory-service. The cause travels with the
     * command: when the write is delegated, that service writes the ledger line, and a payload
     * without an origin produces exactly the blank row this change exists to end.
     */
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
