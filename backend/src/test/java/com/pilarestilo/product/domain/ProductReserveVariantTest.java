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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductReserveVariantTest {

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

    @Test
    void reserveVariant_decreasesAvailableAndResyncsTotal() {
        product.reserveVariant(3, "Rojo", "M");

        ProductVariant rojo = product.getVariants().stream()
                .filter(v -> v.getColor().equalsIgnoreCase("Rojo") && v.getSize().equalsIgnoreCase("M"))
                .findFirst()
                .orElseThrow();

        // Fase 4 semantics: on_hand unchanged, reserved increases, available decreases
        assertEquals(10, rojo.getStockOnHand());
        assertEquals(3,  rojo.getStockReserved());
        assertEquals(7,  rojo.available());
        // product.getStock() reflects total available (7 + 5 = 12)
        assertEquals(12, product.getStock());
    }

    @Test
    void reserveVariant_throwsDomainException_whenStockInsufficient() {
        assertThrows(DomainException.class, () ->
                product.reserveVariant(11, "Rojo", "M")
        );
    }

    @Test
    void reserveVariant_throwsDomainException_whenVariantNotFound() {
        assertThrows(DomainException.class, () ->
                product.reserveVariant(1, "Verde", "XL")
        );
    }

    @Test
    void releaseVariant_incrementsAvailableAndResyncsTotal() {
        // Reserve first, then release
        product.reserveVariant(4, "Azul", "L");
        product.releaseVariant(4, "Azul", "L");

        ProductVariant azul = product.getVariants().stream()
                .filter(v -> v.getColor().equalsIgnoreCase("Azul") && v.getSize().equalsIgnoreCase("L"))
                .findFirst()
                .orElseThrow();

        assertEquals(5, azul.getStockOnHand());
        assertEquals(0, azul.getStockReserved());
        assertEquals(5, azul.available());
        assertEquals(15, product.getStock());
    }

    @Test
    void releaseVariant_throwsDomainException_whenVariantNotFound() {
        assertThrows(DomainException.class, () ->
                product.releaseVariant(1, "Verde", "XL")
        );
    }

    @Test
    void releaseVariant_throwsDomainException_whenReservedInsufficient() {
        // No reservation, release must throw
        assertThrows(DomainException.class, () ->
                product.releaseVariant(1, "Rojo", "M")
        );
    }
}
