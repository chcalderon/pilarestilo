package com.pilarestilo.product.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductSizeRulesTest {

    @Test
    void trimsAndCollapsesInternalWhitespace() {
        assertEquals("L XL", callNormalize("  L    XL  "));
    }

    @Test
    void rejectsBlank() {
        assertThrows(DomainException.class, () -> callNormalize("   "));
    }

    @Test
    void rejectsNull() {
        assertThrows(DomainException.class, () -> callNormalize(null));
    }

    @Test
    void acceptsASingleLetter_noLongerRejectedAsTooShort() {
        assertEquals("X", callNormalize("X"));
    }

    @Test
    void acceptsADoubleHyphen_noLongerAnApparelFormatError() {
        assertEquals("L--XL", callNormalize("L--XL"));
    }

    @Test
    void rejectsOverMaxLength() {
        String tooLong = "A".repeat(41);
        assertThrows(DomainException.class, () -> callNormalize(tooLong));
    }

    @Test
    void acceptsExactlyMaxLength() {
        String exact = "A".repeat(40);
        assertEquals(exact, callNormalize(exact));
    }

    // ProductSizeRules is package-private; call it through the one public entry
    // point that already exists in this package, ProductVariant, to avoid
    // widening its visibility just for tests.
    private static String callNormalize(String raw) {
        return new ProductVariant("Color", raw, 0).getSize();
    }
}
