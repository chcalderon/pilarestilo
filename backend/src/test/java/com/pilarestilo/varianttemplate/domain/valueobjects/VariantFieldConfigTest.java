package com.pilarestilo.varianttemplate.domain.valueobjects;

import com.pilarestilo.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VariantFieldConfigTest {

    @Test
    void freeTextField_needsNoOptionsOrRange() {
        var field = new VariantFieldConfig.FieldConfig(
                "Color", VariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true);

        assertEquals("Color", field.label());
        assertEquals(VariantFieldConfig.InputType.FREE_TEXT, field.inputType());
    }

    @Test
    void optionsField_requiresAtLeastOneOption() {
        assertThrows(DomainException.class, () -> new VariantFieldConfig.FieldConfig(
                "Talla", VariantFieldConfig.InputType.OPTIONS, List.of(), null, null, true, true));
    }

    @Test
    void rangeField_requiresMinLessThanMax() {
        assertThrows(DomainException.class, () -> new VariantFieldConfig.FieldConfig(
                "Numero", VariantFieldConfig.InputType.RANGE, List.of(), 43, 34, true, true));
    }

    @Test
    void rangeField_requiresBothMinAndMax() {
        assertThrows(DomainException.class, () -> new VariantFieldConfig.FieldConfig(
                "Numero", VariantFieldConfig.InputType.RANGE, List.of(), 34, null, true, true));
    }

    @Test
    void blankLabel_isRejected() {
        assertThrows(DomainException.class, () -> new VariantFieldConfig.FieldConfig(
                "  ", VariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true));
    }

    @Test
    void genericFallback_isBothFieldsFreeTextMultipleAndCustom() {
        VariantFieldConfig fallback = VariantFieldConfig.genericFallback();

        assertEquals("Variante", fallback.primary().label());
        assertEquals("Detalle", fallback.secondary().label());
        assertEquals(VariantFieldConfig.InputType.FREE_TEXT, fallback.primary().inputType());
        assertTrue(fallback.primary().allowMultiple());
        assertTrue(fallback.secondary().allowCustom());
    }
}
