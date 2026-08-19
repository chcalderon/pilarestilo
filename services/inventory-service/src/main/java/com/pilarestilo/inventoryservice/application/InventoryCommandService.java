package com.pilarestilo.inventoryservice.application;

import com.pilarestilo.inventoryservice.persistence.InventoryMovementEntity;
import com.pilarestilo.inventoryservice.persistence.InventoryMovementRepository;
import com.pilarestilo.inventoryservice.persistence.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class InventoryCommandService {

    /*
     * The monolith maps this column with an enum, so a value it does not know would break its
     * reads. POS sales are the monolith's channel, so only these three appear here.
     */
    private static final String RESERVE = "RESERVE";
    private static final String RELEASE = "RELEASE";
    private static final String CONFIRM = "CONFIRM";
    private static final String RETURN = "RETURN";

    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    public InventoryCommandService(ProductRepository productRepository,
                                   InventoryMovementRepository movementRepository) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
    }

    /**
     * Writes one line of the stock ledger, with the monolith's sign convention: positive when units
     * are put aside, negative when they leave the shelf or are handed back. The sign is direction
     * per line, not a running total — see InventoryMovementEntity.record.
     *
     * <p>Recorded after the update succeeded, never before, so the ledger describes what happened
     * rather than what was attempted. It runs inside the same transaction as the movement it
     * describes, so neither can exist without the other.
     */
    private void record(String type, UUID productId, String variantColor, String variantSize,
                        int signedQty, StockOrigin origin) {
        StockOrigin cause = origin == null ? StockOrigin.none() : origin;
        movementRepository.save(InventoryMovementEntity.record(
                productId, variantColor, variantSize, type, signedQty,
                cause.referenceType(), cause.referenceId(), cause.recordedBy(), null,
                Instant.now()));
    }

    /**
     * What caused a movement, as it arrives from the monolith. Deliberately a plain carrier rather
     * than a copy of the monolith's factories: this service does not decide causes, it records the
     * one it was given.
     */
    public record StockOrigin(String referenceType, UUID referenceId, UUID recordedBy) {
        public static StockOrigin none() {
            return new StockOrigin(null, null, null);
        }
    }

    @Transactional
    public void reserve(UUID productId, int qty, StockOrigin origin) {
        reserve(productId, qty, null, null, origin);
    }

    @Transactional
    public void reserve(UUID productId, int qty, String variantColor, String variantSize,
                       StockOrigin origin) {
        validate(productId, qty);
        validateVariantSelector(variantColor, variantSize);
        ensureExists(productId);
        boolean variantSelectionProvided = variantColor != null && variantSize != null;
        boolean productHasVariants = productRepository.hasVariants(productId);

        if (!variantSelectionProvided && productHasVariants) {
            throw new IllegalArgumentException("Variant selection (color + size) is required for this product");
        }

        if (variantSelectionProvided) {
            /*
             * The variant row is the only gate. reserveVariantStock already refuses unless
             * stock_on_hand - stock_reserved >= qty for this exact colour and size; a second
             * check against a per-size total could only ever be more restrictive, and when that
             * total drifted it refused sales the variants allowed. product_size_stocks is now
             * derived on read instead of maintained here.
             */
            int variantUpdated = productRepository.reserveVariantStock(productId, variantColor, variantSize, qty);
            if (variantUpdated == 0) {
                throw new IllegalStateException("Insufficient stock for variant: " + productId + " / " + variantColor + " / " + variantSize);
            }
            productRepository.syncProductStockFromVariants(productId, Instant.now());
            record(RESERVE, productId, variantColor, variantSize, qty, origin);
            return;
        }

        int updated = productRepository.reserveStock(productId, qty, Instant.now());
        if (updated == 0) {
            throw new IllegalStateException("Insufficient stock for product: " + productId);
        }
        record(RESERVE, productId, null, null, qty, origin);
    }

    @Transactional
    public void release(UUID productId, int qty, StockOrigin origin) {
        release(productId, qty, null, null, origin);
    }

    @Transactional
    public void release(UUID productId, int qty, String variantColor, String variantSize,
                       StockOrigin origin) {
        validate(productId, qty);
        validateVariantSelector(variantColor, variantSize);
        ensureExists(productId);
        if (variantColor != null && variantSize != null) {
            productRepository.releaseVariantStock(productId, variantColor, variantSize, qty);
            productRepository.syncProductStockFromVariants(productId, Instant.now());
            record(RELEASE, productId, variantColor, variantSize, -qty, origin);
            return;
        }

        productRepository.releaseStock(productId, qty, Instant.now());
        record(RELEASE, productId, null, null, -qty, origin);
    }

    @Transactional
    public void confirm(UUID productId, int qty, StockOrigin origin) {
        confirm(productId, qty, null, null, origin);
    }

    /**
     * Turns the order's reservation into a sale.
     *
     * <p>This was a no-op on the grounds that "stock was already reserved during order creation",
     * which is not what reserving does: it moves units into stock_reserved and leaves
     * stock_on_hand untouched. So with APP_INVENTORY_REMOTE_ENABLED on — what production runs — a
     * paid order never took its units off the shelf. Every completed sale left a reservation that
     * was never released and never deducted, and the shop would eventually refuse orders for goods
     * it still had. The monolith's confirmLocal has always done this correctly; only this copy
     * did not.
     */
    @Transactional
    public void confirm(UUID productId, int qty, String variantColor, String variantSize,
                       StockOrigin origin) {
        validate(productId, qty);
        validateVariantSelector(variantColor, variantSize);
        ensureExists(productId);
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.confirmVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new IllegalStateException(
                        "No reserved stock to confirm for variant " + variantColor + " / " + variantSize
                                + " of product " + productId);
            }
            productRepository.syncProductStockFromVariants(productId, Instant.now());
            record(CONFIRM, productId, variantColor, variantSize, -qty, origin);
            return;
        }
        // Non-variant products follow the legacy aggregate model, where reserving already
        // decremented the total. A second decrement here would double-count the sale. The ledger
        // still records it: the sale happened, and a gap in the trail would read as a lost movement.
        record(CONFIRM, productId, null, null, -qty, origin);
    }

    /**
     * Puts confirmed units back on the shelf when a paid sale is undone.
     *
     * <p>Not a release. By the time a sale is cancelled after payment the reservation is gone —
     * {@code confirm} took the units out of both columns — so returning them touches only
     * stock_on_hand. Calling release here would decrement a reservation that no longer exists and
     * leave the shelf permanently short.
     *
     * <p>Twin of {@code InventoryService.returnToStock} in the monolith. Both write this table and
     * share no compiler: a change to one needs the same change here, in the same commit.
     */
    @Transactional
    public void returnToStock(UUID productId, int qty, String variantColor, String variantSize,
                       StockOrigin origin) {
        validate(productId, qty);
        validateVariantSelector(variantColor, variantSize);
        ensureExists(productId);
        if (variantColor != null && variantSize != null) {
            int updated = productRepository.returnVariantStock(productId, variantColor, variantSize, qty);
            if (updated == 0) {
                throw new NoSuchElementException(
                        "No variant " + variantColor + " / " + variantSize + " on product " + productId);
            }
            productRepository.syncProductStockFromVariants(productId, Instant.now());
            record(RETURN, productId, variantColor, variantSize, qty, origin);
            return;
        }
        // Legacy aggregate: reserving was the only decrement, so this restores the same field —
        // which is exactly what releaseStock does. The movement type is what tells them apart.
        productRepository.releaseStock(productId, qty, Instant.now());
        record(RETURN, productId, null, null, qty, origin);
    }

    private void validate(UUID productId, int qty) {
        if (productId == null) {
            throw new IllegalArgumentException("Product id is required");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
    }

    private void ensureExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new NoSuchElementException("Product not found: " + productId);
        }
    }

    private void validateVariantSelector(String variantColor, String variantSize) {
        if ((variantColor == null) != (variantSize == null)) {
            throw new IllegalArgumentException("variantColor and variantSize must be provided together");
        }
        if (variantColor != null && variantColor.trim().isEmpty()) {
            throw new IllegalArgumentException("variantColor cannot be blank");
        }
        if (variantSize != null && variantSize.trim().isEmpty()) {
            throw new IllegalArgumentException("variantSize cannot be blank");
        }
    }
}
