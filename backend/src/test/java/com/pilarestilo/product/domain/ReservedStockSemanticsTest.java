package com.pilarestilo.product.domain;

import com.pilarestilo.product.domain.enums.ProductCondition;
import com.pilarestilo.product.domain.model.Product;
import com.pilarestilo.product.domain.model.ProductVariant;
import com.pilarestilo.shared.application.Money;
import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservedStockSemanticsTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.create(
                "Test Product",
                "desc",
                new Money(BigDecimal.valueOf(100), "CLP"),
                null,
                ProductCondition.NEW,
                "BrandX",
                0
        );
        product.setVariants(List.of(
                new ProductVariant("Rojo", "M", 10),
                new ProductVariant("Azul", "L", 5)
        ));
    }

    // ── reserve semantics ──────────────────────────────────────────────────────

    @Test
    void afterReserve_availableDecreases_onHandUnchanged_reservedIncreases() {
        product.reserveVariant(3, "Rojo", "M");

        ProductVariant rojo = findVariant("Rojo", "M");

        assertEquals(10, rojo.getStockOnHand(),  "on_hand must not change on reserve");
        assertEquals(3,  rojo.getStockReserved(), "reserved must increase by qty");
        assertEquals(7,  rojo.available(),         "available = on_hand - reserved");
        // getStock() is an alias for getStockOnHand()
        assertEquals(10, rojo.getStock(), "getStock() alias must return on_hand");
    }

    @Test
    void afterReserve_productStockReflectsAvailable() {
        product.reserveVariant(3, "Rojo", "M");
        // Rojo/M: 10-3=7 available; Azul/L: 5 available → total 12
        assertEquals(12, product.getStock(), "product.getStock() = sum of available across variants");
    }

    @Test
    void reserveVariant_throwsDomainException_whenAvailableInsufficient() {
        // Reserve 8 first, leaving 2 available
        product.reserveVariant(8, "Rojo", "M");
        // Now trying to reserve 3 more (only 2 available) must throw
        assertThrows(DomainException.class, () ->
                product.reserveVariant(3, "Rojo", "M")
        );
    }

    @Test
    void reserveVariant_throwsDomainException_whenVariantNotFound() {
        assertThrows(DomainException.class, () ->
                product.reserveVariant(1, "Verde", "XL")
        );
    }

    // ── release semantics ─────────────────────────────────────────────────────

    @Test
    void afterRelease_reservedDecreases_onHandUnchanged() {
        product.reserveVariant(4, "Azul", "L");
        product.releaseVariant(2, "Azul", "L");

        ProductVariant azul = findVariant("Azul", "L");

        assertEquals(5, azul.getStockOnHand(),  "on_hand unchanged after release");
        assertEquals(2, azul.getStockReserved(), "reserved decreases on release");
        assertEquals(3, azul.available());
    }

    @Test
    void releaseVariant_throwsDomainException_whenReservedInsufficient() {
        // No reservation exists; attempting release must throw
        assertThrows(DomainException.class, () ->
                product.releaseVariant(1, "Rojo", "M")
        );
    }

    // ── confirm semantics ─────────────────────────────────────────────────────

    @Test
    void afterConfirm_onHandDecreases_reservedReturnsToZero() {
        product.reserveVariant(3, "Rojo", "M");

        product.confirmVariant(3, "Rojo", "M");

        ProductVariant rojo = findVariant("Rojo", "M");

        assertEquals(7, rojo.getStockOnHand(),  "on_hand decreases by confirmed qty");
        assertEquals(0, rojo.getStockReserved(), "reserved returns to 0 after confirm");
        assertEquals(7, rojo.available());
    }

    @Test
    void afterConfirm_productStockUpdated() {
        product.reserveVariant(3, "Rojo", "M");
        product.confirmVariant(3, "Rojo", "M");
        // Rojo/M: 7 available; Azul/L: 5 available → 12
        assertEquals(12, product.getStock());
    }

    @Test
    void confirmVariant_throwsDomainException_whenReservedInsufficient() {
        // No reservation exists
        assertThrows(DomainException.class, () ->
                product.confirmVariant(1, "Rojo", "M")
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ProductVariant findVariant(String color, String size) {
        return product.getVariants().stream()
                .filter(v -> v.getColor().equalsIgnoreCase(color) && v.getSize().equalsIgnoreCase(size))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Variant not found: " + color + "/" + size));
    }
}
