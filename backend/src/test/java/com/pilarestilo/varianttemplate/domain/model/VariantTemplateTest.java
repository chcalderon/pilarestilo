package com.pilarestilo.varianttemplate.domain.model;

import com.pilarestilo.shared.domain.DomainException;
import com.pilarestilo.varianttemplate.domain.valueobjects.VariantFieldConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VariantTemplateTest {

    @Test
    void create_trimsNameAndAssignsId() {
        VariantTemplate t = VariantTemplate.create("  Zapatos  ", VariantFieldConfig.genericFallback());

        assertEquals("Zapatos", t.getName());
        assertNotNull(t.getId());
        assertNotNull(t.getCreatedAt());
        assertEquals(VariantFieldConfig.genericFallback(), t.getConfig());
    }

    @Test
    void create_rejectsBlankName() {
        VariantFieldConfig config = VariantFieldConfig.genericFallback();
        assertThrows(DomainException.class, () -> VariantTemplate.create("  ", config));
    }

    @Test
    void create_rejectsNullConfig() {
        assertThrows(DomainException.class, () -> VariantTemplate.create("Zapatos", null));
    }

    @Test
    void update_replacesNameAndConfig() {
        VariantTemplate t = VariantTemplate.create("Zapatos", VariantFieldConfig.genericFallback());
        var newConfig = new VariantFieldConfig(
                new VariantFieldConfig.FieldConfig("Color", VariantFieldConfig.InputType.FREE_TEXT, List.of(), null, null, false, true),
                new VariantFieldConfig.FieldConfig("Numero", VariantFieldConfig.InputType.RANGE, List.of(), 34, 43, true, true));

        t.update("Zapatos Deportivos", newConfig);

        assertEquals("Zapatos Deportivos", t.getName());
        assertEquals(newConfig, t.getConfig());
    }
}
